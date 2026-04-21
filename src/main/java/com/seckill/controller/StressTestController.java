package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.service.StressTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 压测专用控制器
 * ⚠️ 仅用于本地开发/测试，生产环境必须删除或禁用
 */
@RestController
@RequestMapping("/api/stress")
@RequiredArgsConstructor
public class StressTestController {

    private final StressTestService stressTestService;

    /**
     * 批量注册用户
     * @param count 注册数量（最大 5000）
     */
    @PostMapping("/register/{count}")
    public Result<Integer> batchRegister(@PathVariable int count) {
        if (count > 5000) {
            count = 5000;
        }
        int actual = stressTestService.batchRegister(count);
        return Result.success("批量注册完成", actual);
    }

    /**
     * 批量登录，返回所有用户的 token 列表
     * @param count 登录数量（必须已注册）
     */
    @PostMapping("/login/{count}")
    public Result<List<Map<String, Object>>> batchLogin(@PathVariable int count) {
        List<Map<String, Object>> tokens = stressTestService.batchLogin(count);
        return Result.success("批量登录完成", tokens);
    }

    /**
     * 重置指定商品的 Redis 库存
     * @param goodsId 商品ID
     * @param stock 重置库存数量
     */
    @PostMapping("/reset-stock/{goodsId}/{stock}")
    public Result<Void> resetStock(@PathVariable Long goodsId, @PathVariable int stock) {
        stressTestService.resetStock(goodsId, stock);
        return Result.success("库存重置完成", null);
    }

    /**
     * 查询某商品的实际订单数
     */
    @GetMapping("/order-count/{goodsId}")
    public Result<Long> getOrderCount(@PathVariable Long goodsId) {
        return Result.success(stressTestService.getOrderCount(goodsId));
    }

    /**
     * 查询 Redis 中某商品的剩余库存
     */
    @GetMapping("/redis-stock/{goodsId}")
    public Result<String> getRedisStock(@PathVariable Long goodsId) {
        return Result.success(stressTestService.getRedisStock(goodsId));
    }

    /**
     * 清理压测数据（删除压测用户和订单）
     */
    @DeleteMapping("/cleanup")
    public Result<Void> cleanup() {
        stressTestService.cleanup();
        return Result.success("压测数据已清理", null);
    }
}
