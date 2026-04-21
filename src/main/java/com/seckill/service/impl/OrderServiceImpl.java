package com.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.seckill.common.BusinessException;
import com.seckill.entity.SeckillOrder;
import com.seckill.enums.OrderStatus;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final SeckillOrderMapper orderMapper;

    @Value("${seckill.order-timeout:15}")
    private int orderTimeoutMinutes;

    @Override
    @Transactional
    public void pay(String orderNo) {
        SeckillOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != OrderStatus.UNPAID.getCode()) {
            throw new BusinessException("订单状态异常，无法支付");
        }

        order.setStatus(OrderStatus.PAID.getCode());
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单支付成功: orderNo={}", orderNo);
    }

    @Override
    @Transactional
    public void cancel(String orderNo) {
        SeckillOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != OrderStatus.UNPAID.getCode()) {
            throw new BusinessException("只能取消待支付的订单");
        }

        order.setStatus(OrderStatus.CANCELLED.getCode());
        orderMapper.updateById(order);

        // 恢复库存
        orderMapper.recoverStock(order.getGoodsId());
        log.info("订单取消，库存已恢复: orderNo={}, goodsId={}", orderNo, order.getGoodsId());
    }

    @Override
    public List<SeckillOrder> listOrders(Long userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getUserId, userId)
                        .orderByDesc(SeckillOrder::getCreateTime)
        );
    }

    @Override
    public SeckillOrder getByOrderNo(String orderNo) {
        return orderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getOrderNo, orderNo)
        );
    }

    @Override
    @Transactional
    public void closeTimeoutOrders() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(orderTimeoutMinutes);

        // 查询超时未支付的订单
        List<SeckillOrder> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getStatus, OrderStatus.UNPAID.getCode())
                        .lt(SeckillOrder::getCreateTime, timeoutThreshold)
        );

        for (SeckillOrder order : timeoutOrders) {
            try {
                order.setStatus(OrderStatus.TIMEOUT.getCode());
                orderMapper.updateById(order);

                // 恢复库存
                orderMapper.recoverStock(order.getGoodsId());
                log.info("超时订单关闭，库存已恢复: orderNo={}, goodsId={}", order.getOrderNo(), order.getGoodsId());
            } catch (Exception e) {
                log.error("关闭超时订单失败: orderNo={}", order.getOrderNo(), e);
            }
        }

        if (!timeoutOrders.isEmpty()) {
            log.info("本次关闭超时订单数量: {}", timeoutOrders.size());
        }
    }
}
