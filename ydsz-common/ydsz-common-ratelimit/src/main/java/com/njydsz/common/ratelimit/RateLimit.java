package com.njydsz.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解。
 *
 * <p>标记在 Controller 方法上，限制指定时间窗口内的访问次数。
 *
 * <p>支持多维度限流：
 * <ul>
 *   <li><b>接口维度：</b>限制单个接口的总 QPS（默认）</li>
 *   <li><b>IP 维度：</b>限制单个 IP 的访问频率（{@code byClientIp = true}）</li>
 *   <li><b>用户维度：</b>限制单个用户的访问频率（{@code byUser = true}）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 限制接口每秒最多 10 次调用
 * @GetMapping("/api/data")
 * @RateLimit(limit = 10, window = 1, timeUnit = TimeUnit.SECONDS)
 * public BaseResponse<List<DataVO>> getData() { ... }
 *
 * // 限制单个 IP 每分钟最多 60 次
 * @PostMapping("/api/orders")
 * @RateLimit(limit = 60, window = 1, timeUnit = TimeUnit.MINUTES, byClientIp = true)
 * public BaseResponse<OrderVO> createOrder() { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RateLimit {

    /**
     * 时间窗口内允许的最大请求数。
     *
     * @return 限流阈值
     */
    int limit() default 100;

    /**
     * 时间窗口长度。
     *
     * @return 窗口长度值
     */
    long window() default 1;

    /**
     * 时间窗口单位。
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 是否按客户端 IP 维度限流。
     *
     * <p>开启后，限流键会包含客户端 IP，实现单 IP 限流。
     *
     * @return true=按 IP 限流
     */
    boolean byClientIp() default false;

    /**
     * 限流键 SpEL 表达式（自定义维度）。
     *
     * <p>当 byClientIp=false 且需要自定义限流维度时使用。
     *
     * @return SpEL 表达式
     */
    String key() default "";

    /**
     * 限流被拒绝时的提示消息。
     *
     * @return 提示消息
     */
    String message() default "请求过于频繁，请稍后再试";
}
