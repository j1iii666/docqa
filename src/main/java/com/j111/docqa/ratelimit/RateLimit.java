package com.j111.docqa.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流（令牌桶）。
 * <p>
 * 用法：{@code @RateLimit(capacity = 10, perSeconds = 60)} 表示"每个用户每分钟最多 10 次"。
 * 默认按 userId 限；登录接口还没登录用户，用 {@code key = KeyType.IP} 按 IP 限。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 桶容量 = 允许的突发次数 */
    int capacity() default 10;

    /** 窗口长度（秒），配合 capacity 决定补充速率：capacity / perSeconds 个每秒 */
    int perSeconds() default 60;

    /** 限流维度 */
    KeyType key() default KeyType.USER;

    enum KeyType {
        USER,
        IP
    }
}
