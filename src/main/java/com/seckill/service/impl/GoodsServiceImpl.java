package com.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.BusinessException;
import com.seckill.entity.SeckillGoods;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoodsServiceImpl implements GoodsService {

    private final SeckillGoodsMapper goodsMapper;
    private final StringRedisTemplate redisTemplate;

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";

    @Override
    public List<SeckillGoods> listGoods() {
        return goodsMapper.selectList(new LambdaQueryWrapper<SeckillGoods>()
                .ge(SeckillGoods::getSeckillStock, 0)
                .orderByAsc(SeckillGoods::getStartDate));
    }

    @Override
    public SeckillGoods getDetail(Long goodsId) {
        SeckillGoods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            throw new BusinessException("商品不存在");
        }
        return goods;
    }

    @Override
    public void loadStockToRedis(Long goodsId) {
        SeckillGoods goods = goodsMapper.selectById(goodsId);
        if (goods != null) {
            String key = SECKILL_STOCK_KEY + goodsId;
            redisTemplate.opsForValue().set(key, String.valueOf(goods.getSeckillStock()));
        }
    }

    @Override
    public void loadAllStockToRedis() {
        List<SeckillGoods> goodsList = goodsMapper.selectList(null);
        for (SeckillGoods goods : goodsList) {
            String key = SECKILL_STOCK_KEY + goods.getId();
            redisTemplate.opsForValue().set(key, String.valueOf(goods.getSeckillStock()));
        }
    }
}
