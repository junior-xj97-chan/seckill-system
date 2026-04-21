package com.seckill.service;

/**
 * 秒杀核心服务接口
 */
public interface SeckillService {

    /**
     * 获取秒杀地址（MD5 加密，防止用户提前知道秒杀 URL）
     */
    String getSeckillPath(Long userId, Long goodsId);

    /**
     * 执行秒杀
     * @return 排队中提示或错误信息
     */
    String doSeckill(Long userId, Long goodsId, String path);

    /**
     * 获取秒杀结果（轮询用）
     */
    String getSeckillResult(Long userId, Long goodsId);
}
