package com.njydsz.pmis.common.redis.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 声明式分布式锁注解
 *
 * <p>标注在方法上，自动获取/释放分布式锁。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @DistributedLock(key = "order:#{#orderId}", type = LockType.REENTRANT, waitTime = 5, leaseTime = -1)
 * public OrderResult processOrder(String orderId) {
 *     // 业务逻辑
 * }
 * }</pre>
 *
 * <p>SpEL 表达式支持：#{#param} 引用方法参数，#{T(com.njydsz.pmis.common.util.TraceIdUtil).getTraceId()} 引用静态方法。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 锁键（支持 SpEL 表达式）
     *
     * @return 锁键
     */
    String key();

    /**
     * 锁类型
     *
     * @return 锁类型
     */
    LockType type() default LockType.REENTRANT;

    /**
     * 等待时间（秒），默认 10
     *
     * @return 等待时间
     */
    long waitTime() default 10;

    /**
     * 持有时间（秒），-1 表示 WatchDog 自动续期
     *
     * @return 持有时间
     */
    long leaseTime() default -1;

    /**
     * 获取锁失败时的消息（默认抛出 IllegalStateException）
     *
     * @return 失败消息
     */
    String failMessage() default "操作过于频繁，请稍后重试";

    /**
     * 锁类型枚举
     */
    enum LockType {
        /** 可重入锁 */
        REENTRANT,
        /** 公平锁 */
        FAIR,
        /** 读锁 */
        READ,
        /** 写锁 */
        WRITE
    }
}
