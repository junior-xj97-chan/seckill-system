package com.seckill.mq;

import com.seckill.service.GoodsService;
import com.seckill.service.OrderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务
 * - 项目启动时预热 Redis 库存
 * - 每分钟关闭超时未支付订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleTask {

    private final GoodsService goodsService;
    private final OrderService orderService;

    /**
     * 项目启动时，将所有秒杀商品库存加载到 Redis
     */
    @PostConstruct
    public void init() {
        log.info("=== 开始预热 Redis 库存 ===");
        try {
            goodsService.loadAllStockToRedis();
            log.info("=== Redis 库存预热完成 ===");
        } catch (Exception e) {
            log.error("Redis 库存预热失败", e);
        }
    }

    /**
     * 每分钟检查并关闭超时未支付订单
     */
    @Scheduled(fixedRate = 60000)
    public void closeTimeoutOrders() {
        log.info("=== 开始执行超时订单关闭任务 ===");
        try {
            orderService.closeTimeoutOrders();
        } catch (Exception e) {
            log.error("关闭超时订单任务执行失败", e);
        }
    }
}
