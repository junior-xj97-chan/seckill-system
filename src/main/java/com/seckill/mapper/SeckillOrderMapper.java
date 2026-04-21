package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    /**
     * 数据库层面减库存（乐观锁，防止超卖）
     */
    @Update("UPDATE seckill_goods SET seckill_stock = seckill_stock - 1, stock_count = stock_count - 1 " +
            "WHERE id = #{goodsId} AND seckill_stock > 0")
    int reduceStock(@Param("goodsId") Long goodsId);

    /**
     * 恢复库存（订单取消/超时时）
     */
    @Update("UPDATE seckill_goods SET seckill_stock = seckill_stock + 1, stock_count = stock_count + 1 " +
            "WHERE id = #{goodsId}")
    int recoverStock(@Param("goodsId") Long goodsId);
}
