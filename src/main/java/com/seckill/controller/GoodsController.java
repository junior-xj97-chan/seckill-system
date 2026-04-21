package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.SeckillGoods;
import com.seckill.service.GoodsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品控制器
 */
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    @GetMapping("/list")
    public Result<List<SeckillGoods>> list() {
        return Result.success(goodsService.listGoods());
    }

    @GetMapping("/detail/{goodsId}")
    public Result<SeckillGoods> detail(@PathVariable Long goodsId) {
        return Result.success(goodsService.getDetail(goodsId));
    }
}
