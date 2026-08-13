package com.njydsz.common.redis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Redis 操作类型声明注解
 *
 * <p>用于显式声明 Redis 操作的读写属性，供异常处理拦截器统一决策：
 * <ul>
 *   <li>READ 操作：触发自动重试</li>
 *   <li>WRITE 操作：不重试（默认），需显式开启 retryOnWrite 才重试</li>
 *   <li>UNKNOWN 操作：由拦截器通过方法名推断（fallback）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @RedisOperation(type = OperationType.READ)
 * public User getUser(String key) { ... }
 *
 * @RedisOperation(type = OperationType.WRITE, retryOnWrite = true)
 * public boolean updateUser(String key, User user) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RedisOperation {

    /**
     * 操作类型（默认 UNKNOWN，由拦截器推断）
     *
     * @return 操作类型
     */
    OperationType type() default OperationType.UNKNOWN;

    /**
     * 写操作重试开关（仅对 WRITE 操作生效）
     * <p>true = 写操作也参与重试（需确保操作幂等）
     *
     * @return 是否对写操作重试
     */
    boolean retryOnWrite() default false;

    /**
     * 操作类型枚举
     */
    enum OperationType {
        /** 读操作（可重试） */
        READ,
        /** 写操作（默认不重试） */
        WRITE,
        /** 未知类型（由拦截器通过方法名推断） */
        UNKNOWN
    }
}
