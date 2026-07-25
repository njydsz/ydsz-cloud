package com.njydsz.common.safe.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（兼容旧 com.njydsz.common.annotation.RateLimit）。
 *
 * <p>标注在 Controller 方法上，基于 Redis 滑动窗口实现方法级限流。
 * 超出 QPS 阈值时返回 429 Too Many Requests。</p>
 *
 * <p><b>支持 SPEL 表达式：</b>key 属性支持 SPEL 表达式，可引用方法参数，
 * 实现按用户 ID、按 IP 等维度的精细限流。</p>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 固定 key 限流
 * @RateLimit(key = "login", qps = 5, windowSeconds = 60)
 * @PostMapping("/login")
 * public Result login(@RequestBody LoginDTO dto) { ... }
 *
 * // SPEL 表达式按用户 ID 限流
 * @RateLimit(key = "#userId", qps = 3)
 * @GetMapping("/users/{userId}/orders")
 * public Result orders(@PathVariable Long userId) { ... }
 *
 * // 组合 key
 * @RateLimit(key = "#dto.type + ':' + #dto.userId", qps = 10)
 * @PostMapping("/export")
 * public Result export(@RequestBody ExportDTO dto) { ... }
 * }</pre>
 *
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流维度，决定限流 key 的组合方式。
     */
    enum Dimension {
        /** IP 维度：按客户端 IP 限流 */
        IP,
        /** 用户维度：按用户 ID 限流 */
        USER,
        /** 全局维度：所有请求共享一个限流桶 */
        GLOBAL
    }

    /**
     * 限流键，用于区分不同接口的限流桶。
     *
     * <p>支持 SPEL 表达式，可引用方法参数（如 {@code #userId}）、
     * 请求对象属性（如 {@code #dto.type}）、调用目标类名（{@code #targetType}）、
     * 方法名（{@code #methodName}）等上下文变量。
     * 非 SPEL 表达式时作为固定字符串使用。</p>
     *
     * @return 限流键
     */
    String key();

    /**
     * 限流维度，决定限流 key 的组合方式。
     *
     * <p>当维度为 {@link Dimension#IP} 时，最终 key = ipKey:clientIp:方法key；
     * 当维度为 {@link Dimension#USER} 时，最终 key = userKey:userId:方法key；
     * 当维度为 {@link Dimension#GLOBAL} 时，最终 key = globalKey:方法key。
     * 默认为 {@link Dimension#IP}。</p>
     *
     * @return 限流维度
     */
    Dimension dimension() default Dimension.IP;

    /**
     * 每秒允许的请求数（QPS）。
     *
     * @return QPS 阈值
     */
    double qps() default 10;

    /**
     * 滑动窗口大小（秒）。
     *
     * @return 窗口秒数
     */
    int windowSeconds() default 60;

    /**
     * 突发容量（允许瞬时超过 QPS 的最大请求数）。
     *
     * @return 突发容量
     */
    int burstCapacity() default 200;

    /**
     * 限流触发时的提示信息。
     *
     * @return 提示信息
     */
    String message() default "操作过于频繁，请稍后重试";
}
