package com.njydsz.pmis.common.safe.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解（兼容旧 com.njydsz.pmis.common.annotation.RateLimit）。
 *
 * <p>标注在 Controller 方法上，基于 Redis/Resilience4j 实现滑动窗口限流。
 * 超出 QPS 阈值时返回 429 Too Many Requests。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RateLimit(key = "login", qps = 5, windowSeconds = 60, message = "操作过于频繁")
 * @PostMapping("/login")
 * public Result login(@RequestBody LoginDTO dto) { ... }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流键，用于区分不同接口的限流桶。
     *
     * @return 限流键
     */
    String key();

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
     * 限流触发时的提示信息。
     *
     * @return 提示信息
     */
    String message() default "操作过于频繁，请稍后重试";
}
