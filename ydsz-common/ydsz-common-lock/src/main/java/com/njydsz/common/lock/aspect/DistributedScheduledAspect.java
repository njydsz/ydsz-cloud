package com.njydsz.common.lock.aspect;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.lock.annotation.LockType;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.common.lock.strategy.LockStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * 分布式定时任务 AOP 切面
 *
 * <p>拦截标注了 {@link DistributedScheduled} 的方法，在方法执行前尝试获取分布式锁，
 * 获取成功则执行任务，获取失败（其他节点正在执行）则跳过本次执行。
 *
 * <p><b>SpEL 支持：</b>{@link DistributedScheduled#lockKey()} 支持 SpEL 表达式
 * （{@code #{...}} 包裹），可引用方法参数动态生成锁 key。
 *
 * <p><b>降级策略：</b>当 {@code LockStrategy} Bean 不存在时（构造器传入 null），
 * 直接执行任务不做加锁，保证单节点/测试环境功能可用。
 *
 * <p>与 {@link YdszDistributedLockAspect} 的区别：
 * <ul>
 *   <li>本切面针对 {@code @Scheduled} 定时任务，获取锁失败时<b>跳过</b>执行（不抛异常）</li>
 *   <li>{@link YdszDistributedLockAspect} 针对业务方法，获取锁失败时<b>抛异常</b>或重试</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Aspect
public class DistributedScheduledAspect {

    /** 锁 key 前缀 */
    private static final String LOCK_PREFIX = "ydsz:schedule:";

    /** SpEL 表达式解析器 */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /** 参数名发现器（用于 SpEL 上下文绑定） */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /** SpEL 表达式缓存，避免每次反射解析 */
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

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
            log.info("[ydsz-lock] [scheduled] LockStrategy 不可用，定时任务将以单节点模式运行（不加锁）");
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

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String lockKey = resolveLockKey(annotation.lockKey(), method, joinPoint.getArgs());
        String fullLockKey = LOCK_PREFIX + lockKey;
        long leaseTime = annotation.leaseTime();
        TimeUnit timeUnit = annotation.timeUnit();

        String lockValue = distributedLocker.tryLock(fullLockKey, leaseTime, timeUnit);
        if (lockValue == null) {
            log.debug("[ydsz-lock] [scheduled] 未获取锁，跳过本次执行: key={}", fullLockKey);
            return null;
        }

        try {
            return joinPoint.proceed();
        } finally {
            try {
                distributedLocker.unlock(fullLockKey, lockValue);
            } catch (Exception e) {
                log.debug("[ydsz-lock] [scheduled] 解锁异常（可能已超时自动释放）: key={} err={}",
                        fullLockKey, e.getMessage());
            }
        }
    }

    /**
     * 解析锁 key，支持 SpEL 表达式
     *
     * <p>如果 lockKey 包含 {@code #{...}} 占位符，则解析为 SpEL 表达式；
     * 否则直接返回原字符串。
     *
     * @param lockKey 注解上的 key 表达式
     * @param method  目标方法
     * @param args    方法参数
     * @return 解析后的锁 key
     */
    private String resolveLockKey(String lockKey, Method method, Object[] args) {
        if (lockKey == null || !lockKey.contains("#{")) {
            return lockKey;
        }
        try {
            String spelExpression = lockKey.replaceAll("#\\{(.+?)}", "$1");
            Expression expression = expressionCache.computeIfAbsent(spelExpression,
                    expr -> expressionParser.parseExpression(expr));
            SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
            if (parameterNames != null) {
                for (int i = 0; i < parameterNames.length; i++) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }
            String evaluated = expression.getValue(context, String.class);
            return evaluated != null ? evaluated : lockKey;
        } catch (Exception e) {
            log.warn("[ydsz-lock] [scheduled] SpEL 解析失败，使用原始 key expr={} cause={}",
                    lockKey, e.getMessage());
            return lockKey;
        }
    }
}
