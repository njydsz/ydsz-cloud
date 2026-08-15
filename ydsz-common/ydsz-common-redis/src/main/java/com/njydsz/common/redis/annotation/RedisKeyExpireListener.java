package com.njydsz.common.redis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis Key 过期事件监听注解
 *
 * <p>标注在 Spring Bean 的方法上，当匹配的 Redis Key 过期时自动回调。
 * 底层基于 Redis Keyspace Notifications（{@code __keyevent@*__:expired}），
 * 需要 Redis 服务端开启 notify-keyspace-events 配置（建议 {@code Ex}）。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RedisKeyExpireListener(keyPattern = "order:lock:*")
 * public void onOrderLockExpired(String expiredKey) {
 *     log.info("订单锁已过期：{}", expiredKey);
 * }
 *
 * // 支持 SpEL 表达式引用配置
 * @RedisKeyExpireListener(keyPattern = "${redis.expire.listen.pattern}")
 * public void onCustomKeyExpired(String expiredKey) {
 *     // 处理过期事件
 * }
 * }</pre>
 *
 * <p><b>注意：</b>
 * <ul>
 *   <li>方法签名必须为 {@code void method(String expiredKey)} 或
 *       {@code void method(RedisKeyExpirationEvent event)}</li>
 *   <li>Redis 服务端需配置 {@code notify-keyspace-events Ex}</li>
 *   <li>过期事件不保证实时性，Redis 采用惰性删除 + 定期删除策略</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisKeyExpireListener {

    /**
     * 监听的 Key 模式（Ant 风格通配符）
     *
     * <p>支持以下模式：
     * <ul>
     *   <li>{@code user:*} - 匹配所有以 "user:" 开头的 key</li>
     *   <li>{@code order:*:lock} - 匹配中间任意段的 key</li>
     *   <li>{@code *} - 匹配所有 key（慎用，性能开销大）</li>
     * </ul>
     *
     * @return key 匹配模式
     */
    String keyPattern() default "*";

    /**
     * 监听的数据库索引（0-15）
     *
     * <p>默认 -1 表示监听所有数据库。
     *
     * @return 数据库索引，-1 表示全部
     */
    int dbIndex() default -1;

    /**
     * 是否启用 SpEL 表达式解析
     *
     * <p>开启后 {@link #keyPattern()} 支持 {@code ${...}} 占位符引用配置项。
     *
     * @return true 表示启用 SpEL 解析
     */
    boolean spelEnabled() default false;
}
