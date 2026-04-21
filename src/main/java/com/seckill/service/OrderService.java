package com.seckill.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seckill.entity.SeckillOrder;

import java.util.List;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 模拟支付
     */
    void pay(String orderNo);

    /**
     * 取消订单
     */
    void cancel(String orderNo);

    /**
     * 查询用户订单列表
     */
    List<SeckillOrder> listOrders(Long userId);

    /**
     * 根据订单号查询
     */
    SeckillOrder getByOrderNo(String orderNo);

    /**
     * 关闭超时未支付订单（定时任务调用）
     */
    void closeTimeoutOrders();
}
