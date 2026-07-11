package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等（防重提交）注解
 *
 * <p>基于 Redis SETNX 实现，在指定时间窗口内同一 key 仅允许一次成功执行。
 * 适用于创建订单、支付等关键写操作。
 *
 * <p>用法：
 * <pre>
 *   {@code @Idempotent(key = "order:create:", ttlSeconds = 5)}
 *   {@code @Idempotent(key = "user:register:", keyFromArg = "#dto.username", ttlSeconds = 60)}
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等 key 前缀（必填）
     */
    String key();

    /**
     * 从参数中提取 key 的 SpEL 表达式，如 {@code #dto.username}
     */
    String keyFromArg() default "";

    /**
     * 是否附加 userId 维度（默认 true）
     */
    boolean useUser() default true;

    /**
     * 防重时间窗口（秒），默认 5 秒
     */
    int ttlSeconds() default 5;

    /**
     * 重复提交时提示信息
     */
    String message() default "请勿重复提交";
}
