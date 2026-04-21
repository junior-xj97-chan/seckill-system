package com.seckill.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀消息 — 发送到 RabbitMQ
 */
@Data
public class SeckillMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long goodsId;
    private Long timestamp;
}
