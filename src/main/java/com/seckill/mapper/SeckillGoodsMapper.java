package com.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.seckill.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {

    /**
     * 直接设置库存为指定值（压测重置用）
     */
    @Update("UPDATE seckill_goods SET seckill_stock = #{stock} WHERE id = #{goodsId}")
    int updateStockTo(@Param("goodsId") Long goodsId, @Param("stock") int stock);
}
