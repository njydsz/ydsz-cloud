package com.njydsz.pmis.common.lock.aspect;

import com.njydsz.pmis.common.lock.annotation.RemiLock;
import com.njydsz.pmis.common.lock.annotation.LockType;
import com.njydsz.pmis.common.lock.core.DistributedLock;
import com.njydsz.pmis.common.lock.exception.DistributedLockException;
import com.njydsz.pmis.common.lock.impl.FallbackDistributedLock;
import com.njydsz.pmis.common.lock.metrics.LockMetrics;
import com.njydsz.pmis.common.lock.strategy.LockStrategy;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 AOP 切面
 *
 * 拦截带有 @RemiLock 注解的方法，在方法执行前后进行加锁和解锁操作。
 * 支持 SpEL 表达式解析锁的键。
 *
 * 执行流程：
 * 1. 解析方法上的 @RemiLock 注解
 * 2. 使用 SpEL 解析锁的键
 * 3. 根据锁类型获取对应的锁实例
 * 4. 尝试获取锁，失败则按配置重试
 * 5. 执行目标方法
 * 6. 方法执行完成后释放锁
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class DistributedLockAspect {

    /**
     * SpEL 表达式解析器
     */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 参数名发现器
     */
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * SpEL 表达式缓存（避免重复解析相同表达式）
     */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

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
    public DistributedLockAspect(LockStrategy lockStrategy, boolean fallbackEnabled) {
        this.lockStrategy = lockStrategy;
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * 构造器注入（降级策略由配置决定，此构造器硬编码为不启用降级，
     * 推荐使用双参数构造器并传入 lockProperties.isFallbackEnabled()）
     *
     * @param lockStrategy 锁策略工厂
     */
    public DistributedLockAspect(LockStrategy lockStrategy) {
        this(lockStrategy, false);
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
     * @param lockAnn   分布式锁注解
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(lockAnn)")
    public Object around(ProceedingJoinPoint joinPoint, RemiLock lockAnn) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String lockKey = resolveLockKey(lockAnn.key(), method, joinPoint.getArgs());
        LockType lockType = lockAnn.lockType();
        long waitTime = lockAnn.waitTime();
        long leaseTime = lockAnn.leaseTime();
        TimeUnit timeUnit = lockAnn.timeUnit();
        int retryCount = lockAnn.retryCount();
        long retryInterval = lockAnn.retryInterval();

        DistributedLock lock = lockStrategy.getLock(lockType);

        // 如果启用降级策略，包装为 FallbackDistributedLock
        if (fallbackEnabled && !(lock instanceof FallbackDistributedLock)) {
            lock = new FallbackDistributedLock(lock, true);
        }

        long acquireStartTime = System.currentTimeMillis();
        String lockValue = acquireLockWithRetry(lock, lockKey, waitTime, leaseTime, timeUnit, retryCount, retryInterval);
        long waitTimeMillis = System.currentTimeMillis() - acquireStartTime;

        if (lockValue == null) {
            if (lockMetrics != null) {
                lockMetrics.recordAcquireFail(lockType.name().toLowerCase());
            }
            if (lockAnn.throwException()) {
                throw new DistributedLockException(lockAnn.message());
            } else {
                log.warn("【分布式锁】获取锁失败，跳过方法执行 | lockKey={}", lockKey);
                return null;
            }
        }

        if (lockMetrics != null) {
            lockMetrics.recordAcquireSuccess(waitTimeMillis, lockType.name().toLowerCase());
        }

        log.debug("【分布式锁】获取锁成功 | lockKey={} | lockType={}", lockKey, lockType);

        long holdStartTime = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long holdTimeMillis = System.currentTimeMillis() - holdStartTime;
            boolean released = lock.unlock(lockKey, lockValue);
            if (released) {
                if (lockMetrics != null) {
                    lockMetrics.recordRelease(holdTimeMillis, lockType.name().toLowerCase());
                }
                log.debug("【分布式锁】释放锁成功 | lockKey={}", lockKey);
            } else {
                log.error("【分布式锁】释放锁失败 | lockKey={}", lockKey);
            }
        }
    }

    /**
     * 带重试机制的锁获取
     *
     * 采用指数退避策略：每次重试间隔 = retryInterval * 2^attempt
     *
     * @param lock          锁实例
     * @param lockKey       锁键
     * @param waitTime      等待时间
     * @param leaseTime     租期
     * @param timeUnit      时间单位
     * @param retryCount    最大重试次数
     * @param retryInterval 重试间隔（毫秒）
     * @return 锁值，获取失败返回 null
     */
    private String acquireLockWithRetry(
            DistributedLock lock, String lockKey, long waitTime,
            long leaseTime, TimeUnit timeUnit, int retryCount, long retryInterval) {

        int maxRetries = Math.min(retryCount, 5);
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
                    throw new DistributedLockException("【分布式锁】获取锁时被中断 | lockKey=" + lockKey, e);
                }
            }

            if (lockValue != null) {
                return lockValue;
            }

            attempt++;
            if (attempt <= maxRetries) {
                long backoffDelay = retryInterval * (1L << (attempt - 1));
                log.debug("【分布式锁】获取锁失败，第 {} 次重试，等待 {} ms | lockKey={}", attempt, backoffDelay, lockKey);
                try {
                    TimeUnit.MILLISECONDS.sleep(backoffDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DistributedLockException("【分布式锁】重试等待时被中断 | lockKey=" + lockKey, e);
                }
            }
        }

        return null;
    }

    /**
     * 解析锁的键
     * <p>支持 SpEL 表达式，如 "order:#{#orderId}"
     * <p>优化：缓存解析后的 Expression 对象，避免重复解析
     *
     * @param keyExpression 锁键表达式
     * @param method        目标方法
     * @param args          方法参数
     * @return 解析后的锁键
     */
    private String resolveLockKey(String keyExpression, Method method, Object[] args) {
        if (!keyExpression.contains("#{")) {
            return keyExpression;
        }

        String spelExpression = keyExpression.replaceAll("#\\{(.+?)}", "$1");
        
        // 从缓存获取或解析表达式
        Expression expression = expressionCache.computeIfAbsent(spelExpression, 
            expr -> expressionParser.parseExpression(expr));
        
        // 构建上下文并执行
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return expression.getValue(context, String.class);
    }
}
