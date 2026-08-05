package com.remisoft.common.redis.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * remi 分布式缓存注解
 *
 * <p>增强 Spring {@link org.springframework.cache.annotation.Cacheable}，提供：
 * <ul>
 *   <li>自定义过期时间（TTL）</li>
 *   <li>空值缓存防穿透（默认缓存 60 秒）</li>
 *   <li>分布式互斥锁防击穿</li>
 *   <li>随机过期防雪崩</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * &#64;RemiCacheable(key = "'user:' + #userId", ttl = 300, timeUnit = TimeUnit.SECONDS)
 * public User getUserById(Long userId) {
 *     return userMapper.selectById(userId);
 * }
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RemiCacheable {

    /**
     * SpEL 缓存键表达式
     * <p>使用 Spring EL 语法，支持引用方法参数，例如 {@code "'user:' + #userId"}
     *
     * @return SpEL 表达式字符串
     */
    String key();

    /**
     * 缓存过期时间
     *
     * @return 过期时间数值，默认 600 秒
     */
    long ttl() default 600;

    /**
     * 缓存过期时间单位
     *
     * @return 时间单位，默认 {@link TimeUnit#SECONDS}
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 空值缓存 TTL（数据库返回 null 时占位的过期时间）
     *
     * @return 空值过期时间数值，默认 60
     */
    long nullValueTtl() default 60;

    /**
     * 是否启用空值缓存防穿透
     *
     * @return true-启用（默认），false-禁用
     */
    boolean preventPenetration() default true;

    /**
     * 是否启用分布式互斥锁防击穿
     * <p>开启后，缓存未命中时仅一个线程回源，其余线程自旋等待。
     *
     * @return true-启用，false-禁用（默认）
     */
    boolean preventStampede() default false;

    /**
     * 防击穿模式下，等待缓存填充的最大等待时间
     * <p>仅在 {@link #preventStampede()} 为 true 时生效。
     *
     * @return 最大等待秒数，默认 3 秒
     */
    long lockWaitTimeout() default 3;
}