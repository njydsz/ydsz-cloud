package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 *
 * <p>基于 Redis 滑动窗口，由 RateLimiterAspect 拦截。
 *
 * <p>用法：
 * <pre>
 *   {@code @RateLimit(qps = 5, key = "login:")} // 每秒 5 次
 *   {@code @RateLimit(qps = 10, key = "user:create:")} // 每秒 10 次
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 每秒允许的请求数 */
    int qps() default 10;

    /** 限流维度 key 前缀 */
    String key() default "";

    /** 限流时间窗口（秒），默认 1 秒 */
    int windowSeconds() default 1;

    /** 提示信息 */
    String message() default "请求频率超限，请稍后再试";
}
