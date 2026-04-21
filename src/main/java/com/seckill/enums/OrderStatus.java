package com.seckill.enums;

import lombok.Getter;

/**
 * 订单状态枚举
 */
@Getter
public enum OrderStatus {

    UNPAID(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消"),
    TIMEOUT(3, "超时关闭");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
