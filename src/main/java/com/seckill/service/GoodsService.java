package com.seckill.service;

import com.seckill.entity.SeckillGoods;

import java.util.List;

/**
 * 商品服务接口
 */
public interface GoodsService {

    /**
     * 获取所有秒杀商品列表
     */
    List<SeckillGoods> listGoods();

    /**
     * 获取商品详情
     */
    SeckillGoods getDetail(Long goodsId);

    /**
     * 将商品库存加载到 Redis
     */
    void loadStockToRedis(Long goodsId);

    /**
     * 将所有秒杀商品库存加载到 Redis
     */
    void loadAllStockToRedis();
}
