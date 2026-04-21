package com.seckill.util;

import java.lang.annotation.*;

/**
 * 接口限流注解 — 基于 Redis + Lua 脚本
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimitAnnotation {

    /**
     * 限流 Key 前缀
     */
    String key() default "";

    /**
     * 时间窗口（秒）
     */
    int time() default 1;

    /**
     * 时间窗口内最大请求数
     */
    int count() default 100;
}
