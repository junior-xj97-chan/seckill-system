package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.service.SeckillService;
import com.seckill.util.RateLimitAnnotation;
import com.seckill.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀控制器
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 获取秒杀地址
     */
    @GetMapping("/path/{goodsId}")
    public Result<String> getPath(@PathVariable Long goodsId) {
        Long userId = UserContext.getUserId();
        return Result.success(seckillService.getSeckillPath(userId, goodsId));
    }

    /**
     * 执行秒杀（限流：每秒最多 100 次请求/IP）
     */
    @PostMapping("/execute/{goodsId}")
    @RateLimitAnnotation(key = "seckill", time = 1, count = 100)
    public Result<String> doSeckill(
            @PathVariable Long goodsId,
            @RequestParam String path) {
        Long userId = UserContext.getUserId();
        String result = seckillService.doSeckill(userId, goodsId, path);
        return Result.success(result);
    }

    /**
     * 查询秒杀结果（轮询）
     */
    @GetMapping("/result/{goodsId}")
    public Result<String> getResult(@PathVariable Long goodsId) {
        Long userId = UserContext.getUserId();
        return Result.success(seckillService.getSeckillResult(userId, goodsId));
    }
}
