package com.njydsz.pmis.common.aspect;

import com.njydsz.pmis.common.annotation.DistributedLock;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 分布式锁切面（P2-3：分布式锁统一封装）
 *
 * <p>对标注 {@link DistributedLock} 的方法自动加锁/解锁。
 * 使用 Redisson RLock，支持 waitTime + leaseTime 配置。
 * 获取锁失败时抛出 {@link BizException}。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = resolveKey(distributedLock.key(), joinPoint);
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(),
                    distributedLock.timeUnit());
            if (!acquired) {
                log.warn("[DistributedLock] 获取锁失败: key={}, waitTime={}s", lockKey, distributedLock.waitTime());
                throw new BizException(BizErrorCode.RESOURCE_LOCKED, "操作过于频繁，请稍后重试");
            }
            log.debug("[DistributedLock] 获取锁成功: key={}", lockKey);
            return joinPoint.proceed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(BizErrorCode.RESOURCE_LOCKED, "操作被中断，请稍后重试");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[DistributedLock] 释放锁: key={}", lockKey);
            }
        }
    }

    /**
     * 解析 SpEL 表达式，获取锁的 key
     */
    private String resolveKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String[] paramNames = PARAMETER_NAME_DISCOVERER.getParameterNames(method);
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return SPEL_PARSER.parseExpression(keyExpression).getValue(context, String.class);
    }
}