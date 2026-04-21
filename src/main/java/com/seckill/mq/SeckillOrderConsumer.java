package com.seckill.mq;

import com.rabbitmq.client.Channel;
import com.seckill.common.BusinessException;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.enums.OrderStatus;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单 MQ 消费者
 *
 * 接收秒杀消息 → 数据库减库存 → 创建订单
 * 手动 ACK 机制保证消息不丢失
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final SeckillOrderMapper orderMapper;
    private final SeckillGoodsMapper goodsMapper;
    private final OrderService orderService;

    @RabbitListener(queues = "seckill.order.queue")
    public void onMessage(SeckillMessage message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到秒杀消息: userId={}, goodsId={}", message.getUserId(), message.getGoodsId());

        try {
            // 1. 数据库层面减库存（乐观锁防超卖兜底）
            int rows = orderMapper.reduceStock(message.getGoodsId());
            if (rows == 0) {
                log.warn("数据库减库存失败，库存不足: goodsId={}", message.getGoodsId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 2. 创建订单
            SeckillGoods goods = goodsMapper.selectById(message.getGoodsId());
            SeckillOrder order = new SeckillOrder();
            order.setUserId(message.getUserId());
            order.setGoodsId(message.getGoodsId());
            order.setOrderNo(generateOrderNo());
            order.setSeckillPrice(goods.getSeckillPrice());
            order.setStatus(OrderStatus.UNPAID.getCode());

            try {
                orderMapper.insert(order);
            } catch (Exception e) {
                // 重复下单（并发场景），回滚库存
                log.warn("订单创建失败（可能重复），回滚库存: userId={}, goodsId={}",
                        message.getUserId(), message.getGoodsId());
                orderMapper.recoverStock(message.getGoodsId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 3. 确认消息
            channel.basicAck(deliveryTag, false);
            log.info("秒杀订单创建成功: userId={}, goodsId={}, orderNo={}",
                    message.getUserId(), message.getGoodsId(), order.getOrderNo());

        } catch (Exception e) {
            log.error("处理秒杀消息异常", e);
            try {
                // 消费失败，重新入队
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ex) {
                log.error("消息重入队失败", ex);
            }
        }
    }

    private String generateOrderNo() {
        // 时间戳 + 随机数生成订单号
        return System.currentTimeMillis() + "" + (int)(Math.random() * 9000 + 1000);
    }
}
