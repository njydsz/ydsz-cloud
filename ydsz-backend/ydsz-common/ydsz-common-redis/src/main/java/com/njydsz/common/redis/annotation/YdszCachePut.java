package com.njydsz.common.redis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 缓存更新注解
 *
 * <p>标注在方法上，方法执行成功后将返回值自动写入 Redis 缓存。
 * 与 {@link YdszCacheable} 不同，{@code @YdszCachePut} <b>不查询缓存</b>，
 * 始终执行方法体，适用于更新操作后刷新缓存。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @YdszCachePut(key = "'user:' + #user.id", ttl = 600)
 * public User updateUser(User user) {
 *     return userMapper.update(user);
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface YdszCachePut {

    /**
     * 缓存 Key（支持 SpEL 表达式）
     *
     * @return SpEL Key 表达式
     */
    String key();

    /**
     * 缓存过期时间
     *
     * @return 过期时间数值
     */
    long ttl() default 300;

    /**
     * 时间单位
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
