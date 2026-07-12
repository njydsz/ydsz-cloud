package com.njydsz.pmis.common.redis.lock;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁 AOP 切面
 *
 * <p>拦截标注了 {@link DistributedLock} 的方法，自动获取/释放分布式锁。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Aspect
@Component
public class DistributedLockAspect {

    private final DistributedLockSupport lockSupport;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Autowired
    public DistributedLockAspect(DistributedLockSupport lockSupport) {
        this.lockSupport = lockSupport;
    }

    /**
     * 拦截分布式锁注解
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        // 解析 SpEL 锁键
        String lockKey = resolveKey(distributedLock.key(), joinPoint);

        // 根据锁类型执行
        return switch (distributedLock.type()) {
            case REENTRANT -> lockSupport.executeWithReentrantLock(lockKey,
                    distributedLock.waitTime(), distributedLock.leaseTime(),
                    () -> proceed(joinPoint));
            case FAIR -> lockSupport.executeWithFairLock(lockKey,
                    distributedLock.waitTime(), distributedLock.leaseTime(),
                    () -> proceed(joinPoint));
            case READ -> lockSupport.executeWithReadLock(lockKey,
                    distributedLock.waitTime(), distributedLock.leaseTime(),
                    () -> proceed(joinPoint));
            case WRITE -> lockSupport.executeWithWriteLock(lockKey,
                    distributedLock.waitTime(), distributedLock.leaseTime(),
                    () -> proceed(joinPoint));
        };
    }

    /**
     * 解析 SpEL 锁键
     */
    private String resolveKey(String keyExpr, ProceedingJoinPoint joinPoint) {
        if (!keyExpr.contains("#")) {
            // 非 SpEL 表达式，直接返回
            return keyExpr;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, args, paramNameDiscoverer);
        String value = parser.parseExpression(keyExpr).getValue(context, String.class);
        return value != null ? value : keyExpr;
    }

    /**
     * 执行目标方法
     */
    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }
}
