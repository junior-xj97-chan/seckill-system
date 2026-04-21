package com.seckill.service;

import java.util.List;
import java.util.Map;

/**
 * 压测服务接口
 */
public interface StressTestService {

    /**
     * 批量注册用户
     */
    int batchRegister(int count);

    /**
     * 批量登录
     */
    List<Map<String, Object>> batchLogin(int count);

    /**
     * 重置 Redis 库存
     */
    void resetStock(Long goodsId, int stock);

    /**
     * 查询订单数
     */
    Long getOrderCount(Long goodsId);

    /**
     * 查询 Redis 剩余库存
     */
    String getRedisStock(Long goodsId);

    /**
     * 清理压测数据
     */
    void cleanup();
}
