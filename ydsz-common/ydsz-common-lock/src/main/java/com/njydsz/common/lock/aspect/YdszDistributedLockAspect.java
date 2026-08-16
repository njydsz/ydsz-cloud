package com.njydsz.common.lock.aspect;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.exception.DistributedLockException;
import com.njydsz.common.lock.impl.FallbackDistributedLock;
import com.njydsz.common.lock.metrics.LockMetrics;
import com.njydsz.common.lock.strategy.LockStrategy;
import com.njydsz.common.lock.util.LockExpressionUtils;
import com.njydsz.common.lock.util.LockKeyValidator;


/**
 * 分布式锁 AOP 切面
 *
 * 拦截带有 @YdszDistributedLock 注解的方法，在方法执行前后进行加锁和解锁操作。
 * 支持 SpEL 表达式解析锁的键（委托 {@link LockExpressionUtils}）。
 *
 * 执行流程：
 * 1. 解析方法上的 @YdszDistributedLock 注解
 * 2. 使用 SpEL 解析锁的键
 * 3. 根据锁类型获取对应的锁实例
 * 4. 尝试获取锁，失败则按配置重试
 * 5. 执行目标方法
 * 6. 方法执行完成后释放锁
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class YdszDistributedLockAspect {

    /**
     * 降级锁实例缓存（按锁类型缓存，避免每次拦截创建新实例）
     * <p>FallbackDistributedLock 内部维护降级状态，需复用同一实例以保证状态连续性
     */
    private final Map<LockType, FallbackDistributedLock> fallbackLockCache = new ConcurrentHashMap<>();

    /**
     * 锁策略工厂
     */
    private final LockStrategy lockStrategy;

    /**
     * 是否启用锁降级策略
     */
    private final boolean fallbackEnabled;

    /**
     * 锁指标收集器（可选）
     */
    private LockMetrics lockMetrics;

    /**
     * 构造器注入
     *
     * @param lockStrategy    锁策略工厂
     * @param fallbackEnabled 是否启用锁降级策略
     */
    public YdszDistributedLockAspect(LockStrategy lockStrategy, boolean fallbackEnabled) {
        this.lockStrategy = lockStrategy;
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * 设置锁指标收集器
     *
     * @param lockMetrics 锁指标收集器
     */
    public void setLockMetrics(LockMetrics lockMetrics) {
        this.lockMetrics = lockMetrics;
    }

    /**
     * 环绕通知：处理分布式锁逻辑
     *
     * @param joinPoint 切入点
     * @param lockAnn 分布式锁注解
     * @return 方法执行结果
     */
    @Around("@annotation(lockAnn)")
    public Object around(ProceedingJoinPoint joinPoint, YdszDistributedLock lockAnn) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String lockKey = resolveLockKey(lockAnn.key(), method, joinPoint.getArgs());
        LockKeyValidator.validate(lockKey);
        LockType lockType = lockAnn.lockType();
        long waitTime = lockAnn.waitTime();
        long leaseTime = lockAnn.leaseTime();
        TimeUnit timeUnit = lockAnn.timeUnit();
        int retryCount = lockAnn.retryCount();
        long retryInterval = lockAnn.retryInterval();

        DistributedLocker lock = lockStrategy.getLock(lockType);

        // 如果启用降级策略，包装为 FallbackDistributedLock（缓存实例，避免频繁创建）
        if (fallbackEnabled && !(lock instanceof FallbackDistributedLock)) {
            lock = fallbackLockCache.computeIfAbsent(lockType,
                    lt -> new FallbackDistributedLock(lockStrategy.getLock(lt), true));
        }

        long acquireStartTime = System.currentTimeMillis();
        String lockValue = acquireLockWithRetry(lock, lockKey, lockAnn);
        long waitTimeMillis = System.currentTimeMillis() - acquireStartTime;

        if (lockValue == null) {
            if (lockMetrics != null) {
                lockMetrics.recordAcquireFail(lockType.name().toLowerCase());
            }
            if (lockAnn.throwException()) {
                throw new DistributedLockException(lockAnn.message());
            }
            log.warn("[ydsz-lock]获取锁失败，跳过方法执行 | lockKey={} | traceId={}", lockKey, resolveTraceId());
            return null;
        }

        if (lockMetrics != null) {
            lockMetrics.recordAcquireSuccess(waitTimeMillis, lockType.name().toLowerCase());
        }

        log.debug("[ydsz-lock]获取锁成功 | lockKey={} | lockType={}", lockKey, lockType);

        long holdStartTime = System.currentTimeMillis();
        try {
            // 如果禁用看门狗续期，获取锁后立即停止续期任务
            if (!lockAnn.autoRenew()) {
                lockStrategy.stopWatchDog(lockKey);
            }
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new DistributedLockException("业务方法执行异常 | lockKey=" + lockKey, t);
        } finally {
            long holdTimeMillis = System.currentTimeMillis() - holdStartTime;
            boolean released = lock.unlock(lockKey, lockValue);
            if (released) {
                if (lockMetrics != null) {
                    lockMetrics.recordRelease(holdTimeMillis, lockType.name().toLowerCase());
                }
                log.debug("[ydsz-lock]释放锁成功 | lockKey={} | traceId={}", lockKey, resolveTraceId());
            } else {
                log.error("[ydsz-lock]释放锁失败 | lockKey={} | traceId={}", lockKey, resolveTraceId());
            }
        }
    }

    /**
     * 解析当前链路 traceId：优先 {@link RequestContext}，回退 MDC（兼容 SkyWalking "tid"）。
     *
     * @return 当前 traceId；均不存在时返回 null
     */
    private String resolveTraceId() {
        String traceId = RequestContext.getTraceId();
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return MDC.get("tid");
    }

    /**
     * 带重试机制的锁获取
     *
     * <p>采用指数退避策略：每次重试间隔 = retryInterval * 2^attempt，
     * 重试次数上限 {@value #MAX_RETRY_CAP} 次。
     *
     * @param lock    锁实例
     * @param lockKey 锁键
     * @param lockAnn 分布式锁注解（等待时间/租约/重试参数均取自注解）
     * @return 锁值，获取失败返回 null
     */
    private String acquireLockWithRetry(DistributedLocker lock, String lockKey, YdszDistributedLock lockAnn) {
        long waitTime = lockAnn.waitTime();
        long leaseTime = lockAnn.leaseTime();
        TimeUnit timeUnit = lockAnn.timeUnit();
        int retryCount = lockAnn.retryCount();
        long retryInterval = lockAnn.retryInterval();

        int maxRetries = Math.min(retryCount, MAX_RETRY_CAP);
        int attempt = 0;

        while (attempt <= maxRetries) {
            String lockValue;
            if (waitTime == 0) {
                lockValue = lock.tryLock(lockKey, leaseTime, timeUnit);
            } else {
                try {
                    lockValue = lock.tryLock(lockKey, waitTime, leaseTime, timeUnit);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DistributedLockException("[ydsz-lock]获取锁时被中断 | lockKey=" + lockKey, e);
                }
            }

            if (lockValue != null) {
                return lockValue;
            }

            attempt++;
            if (attempt <= maxRetries) {
                long backoffDelay = retryInterval * (1L << (attempt - 1));
                log.debug("[ydsz-lock]获取锁失败，第 {} 次重试，等待 {} ms | lockKey={}", attempt, backoffDelay, lockKey);
                try {
                    TimeUnit.MILLISECONDS.sleep(backoffDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DistributedLockException("[ydsz-lock]重试等待时被中断 | lockKey=" + lockKey, e);
                }
            }
        }

        return null;
    }

    /**
     * 最大重试次数上限（防止异常配置导致无界重试）
     */
    private static final int MAX_RETRY_CAP = 5;

    /**
     * 解析锁的键
     * <p>支持两种 SpEL 写法（委托 {@link LockExpressionUtils}）：
     * <ul>
     *   <li>模板模式：{@code "order:#{#orderId}"}</li>
     *   <li>整串 SpEL 模式：{@code "'order:' + #orderId"}</li>
     * </ul>
     *
     * @param keyExpression 锁键表达式
     * @param method        目标方法
     * @param args          方法参数
     * @return 解析后的锁键
     */
    private String resolveLockKey(String keyExpression, Method method, Object[] args) {
        return LockExpressionUtils.resolve(keyExpression, method, args);
    }
}
