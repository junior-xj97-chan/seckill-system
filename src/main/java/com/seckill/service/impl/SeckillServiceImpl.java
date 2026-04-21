package com.seckill.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.MD5;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.BusinessException;
import com.seckill.config.RabbitMQConfig;
import com.seckill.dto.SeckillMessage;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.service.SeckillService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀核心服务实现
 *
 * 核心流程：
 * 1. 验证秒杀地址（MD5 防刷）
 * 2. 检查是否重复秒杀（Redis SETNX 去重）
 * 3. Redis Lua 脚本原子预扣库存（防超卖）
 * 4. 发送 MQ 消息异步下单（削峰）
 * 5. 数据库层面乐观锁减库存（兜底防超卖）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillGoodsMapper goodsMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    // 秒杀地址加密盐值
    private static final String SECKILL_SALT = "seckill_salt_2026";

    // Redis Key 前缀
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_PATH_KEY = "seckill:path:";
    private static final String SECKILL_USER_KEY = "seckill:user:";
    private static final String SECKILL_RESULT_KEY = "seckill:result:";

    // Lua 脚本：原子性预扣库存
    private DefaultRedisScript<Long> DEDUCT_STOCK_SCRIPT;

    @PostConstruct
    public void init() {
        DEDUCT_STOCK_SCRIPT = new DefaultRedisScript<>();
        DEDUCT_STOCK_SCRIPT.setResultType(Long.class);
        DEDUCT_STOCK_SCRIPT.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/deduct_stock.lua"))
        );
    }

    @Override
    public String getSeckillPath(Long userId, Long goodsId) {
        // 检查商品是否存在
        SeckillGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw new BusinessException("商品不存在");
        }

        // 检查秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(goods.getStartDate())) {
            throw new BusinessException("秒杀尚未开始");
        }
        if (now.isAfter(goods.getEndDate())) {
            throw new BusinessException("秒杀已结束");
        }

        // 生成 MD5 秒杀地址
        String path = MD5.create().digestHex(userId + goodsId + SECKILL_SALT);
        String key = SECKILL_PATH_KEY + userId + ":" + goodsId;
        redisTemplate.opsForValue().set(key, path, 5, TimeUnit.MINUTES);
        return path;
    }

    @Override
    public String doSeckill(Long userId, Long goodsId, String path) {
        // 1. 验证秒杀地址
        String pathKey = SECKILL_PATH_KEY + userId + ":" + goodsId;
        String cachedPath = redisTemplate.opsForValue().get(pathKey);
        if (cachedPath == null || !cachedPath.equals(path)) {
            throw new BusinessException("秒杀地址无效，请重新获取");
        }

        // 验证后删除地址，防止重复使用
        redisTemplate.delete(pathKey);

        // 2. Redis SETNX 去重 — 同一用户同一商品只能秒杀一次
        String userKey = SECKILL_USER_KEY + userId + ":" + goodsId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(userKey, "1", 1, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            throw new BusinessException("您已参与过该商品的秒杀，请勿重复操作");
        }

        // 3. Redis Lua 脚本原子预扣库存
        String stockKey = SECKILL_STOCK_KEY + goodsId;
        Long result = redisTemplate.execute(
                DEDUCT_STOCK_SCRIPT,
                Collections.singletonList(stockKey)
        );

        if (result == null || result == 0) {
            // 库存不足，释放用户标记
            redisTemplate.delete(userKey);
            throw new BusinessException("商品已售罄");
        }

        // 4. 发送 MQ 消息，异步下单
        SeckillMessage message = new SeckillMessage();
        message.setUserId(userId);
        message.setGoodsId(goodsId);
        message.setTimestamp(System.currentTimeMillis());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.SECKILL_EXCHANGE,
                    RabbitMQConfig.SECKILL_ROUTING_KEY,
                    message
            );
            log.info("秒杀消息已发送: userId={}, goodsId={}", userId, goodsId);
        } catch (Exception e) {
            log.error("发送MQ消息失败，回滚库存", e);
            // 发送失败，回滚 Redis 库存
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.delete(userKey);
            throw new BusinessException("系统繁忙，请稍后重试");
        }

        // 5. 返回排队提示
        return "排队中，请稍后查询秒杀结果";
    }

    @Override
    public String getSeckillResult(Long userId, Long goodsId) {
        // 先查 Redis 结果缓存
        String resultKey = SECKILL_RESULT_KEY + userId + ":" + goodsId;
        String cached = redisTemplate.opsForValue().get(resultKey);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        SeckillOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getUserId, userId)
                        .eq(SeckillOrder::getGoodsId, goodsId)
        );

        if (order != null) {
            String result;
            if (order.getStatus() == 0) {
                result = "SUCCESS:" + order.getOrderNo();
            } else if (order.getStatus() == 3) {
                result = "FAILED:订单超时关闭";
            } else {
                result = "SUCCESS:" + order.getOrderNo();
            }
            // 缓存结果
            redisTemplate.opsForValue().set(resultKey, result, 10, TimeUnit.MINUTES);
            return result;
        }

        return "排队中";
    }
}
