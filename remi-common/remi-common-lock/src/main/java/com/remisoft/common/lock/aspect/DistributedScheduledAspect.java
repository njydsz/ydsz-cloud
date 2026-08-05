package com.remisoft.common.lock.aspect;

import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import com.remisoft.common.lock.annotation.DistributedScheduled;
import com.remisoft.common.lock.annotation.LockType;
import com.remisoft.common.lock.core.DistributedLocker;
import com.remisoft.common.lock.strategy.LockStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式定时任务 AOP 切面
 *
 * <p>拦截标注了 {@link DistributedScheduled} 的方法，在方法执行前尝试获取分布式锁，
 * 获取成功则执行任务，获取失败（其他节点正在执行）则跳过本次执行。
 *
 * <p><b>降级策略：</b>当 {@code LockStrategy} Bean 不存在时（构造器传入 null），
 * 直接执行任务不做加锁，保证单节点/测试环境功能可用。
 *
 * <p>与 {@link RemiDistributedLockAspect} 的区别：
 * <ul>
 *   <li>本切面针对 {@code @Scheduled} 定时任务，获取锁失败时<b>跳过</b>执行（不抛异常）</li>
 *   <li>{@link RemiDistributedLockAspect} 针对业务方法，获取锁失败时<b>抛异常</b>或重试</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class DistributedScheduledAspect {

    /** 锁 key 前缀 */
    private static final String LOCK_PREFIX = "remi:schedule:";

    /** 分布式锁提供者（null 时降级为不加锁） */
    private final DistributedLocker distributedLocker;

    /**
     * 构造器
     *
     * @param lockStrategy 锁策略（可选，null 时降级）
     */
    public DistributedScheduledAspect(LockStrategy lockStrategy) {
        if (lockStrategy == null) {
            this.distributedLocker = null;
            log.info("[DistributedScheduled] LockStrategy 不可用，定时任务将以单节点模式运行（不加锁）");
        } else {
            this.distributedLocker = lockStrategy.getLock(LockType.REENTRANT);
        }
    }

    /**
     * 环绕通知：拦截 @DistributedScheduled 注解的方法
     *
     * <p>获取不到锁时直接返回 null，不抛异常，不执行目标方法。
     *
     * @param joinPoint AOP 连接点
     * @param annotation 注解（由参数绑定自动注入）
     * @return 目标方法返回值；未获取锁时返回 null
     * @throws Throwable 目标方法抛出的异常
     */
    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedScheduled annotation) throws Throwable {
        if (distributedLocker == null) {
            return joinPoint.proceed();
        }

        String lockKey = LOCK_PREFIX + annotation.lockKey();
        long leaseTime = annotation.leaseTime();
        TimeUnit timeUnit = annotation.timeUnit();

        String lockValue = distributedLocker.tryLock(lockKey, leaseTime, timeUnit);
        if (lockValue == null) {
            log.debug("[DistributedScheduled] 未获取锁，跳过本次执行: key={}", lockKey);
            return null;
        }

        try {
            return joinPoint.proceed();
        } finally {
            try {
                distributedLocker.unlock(lockKey, lockValue);
            } catch (Exception e) {
                log.debug("[DistributedScheduled] 解锁异常（可能已超时自动释放）: key={} err={}",
                        lockKey, e.getMessage());
            }
        }
    }
}
