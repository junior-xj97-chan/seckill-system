package com.seckill.controller;

import com.seckill.common.Result;
import com.seckill.entity.SeckillOrder;
import com.seckill.service.OrderService;
import com.seckill.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单控制器
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 支付订单
     */
    @PostMapping("/pay/{orderNo}")
    public Result<Void> pay(@PathVariable String orderNo) {
        orderService.pay(orderNo);
        return Result.success("支付成功", null);
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderService.cancel(orderNo);
        return Result.success("订单已取消", null);
    }

    /**
     * 查询我的订单列表
     */
    @GetMapping("/list")
    public Result<List<SeckillOrder>> list() {
        Long userId = UserContext.getUserId();
        return Result.success(orderService.listOrders(userId));
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    public Result<SeckillOrder> detail(@PathVariable String orderNo) {
        return Result.success(orderService.getByOrderNo(orderNo));
    }
}
