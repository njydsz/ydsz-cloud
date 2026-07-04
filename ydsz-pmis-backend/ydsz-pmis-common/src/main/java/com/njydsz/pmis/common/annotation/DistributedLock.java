package com.njydsz.pmis.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解（P2-3：分布式锁统一封装）
 *
 * <p>基于 Redisson RLock 实现，支持 SpEL 表达式指定锁 key。
 * 失败时抛出 {@link com.njydsz.pmis.common.exception.BizException}。
 *
 * <p>使用示例：
 * <pre>
 * &#64;DistributedLock(key = "'contract:update:' + #contractId", waitTime = 3, leaseTime = 10)
 * public void updateContract(Long contractId, ContractUpdateDTO dto) { ... }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的 key，支持 SpEL 表达式
     * <p>示例：{@code "'order:pay:' + #orderId"}
     */
    String key();

    /**
     * 等待获取锁的最大时间，默认 3 秒
     */
    long waitTime() default 3;

    /**
     * 锁的自动释放时间，默认 10 秒
     */
    long leaseTime() default 10;

    /**
     * 时间单位，默认秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}