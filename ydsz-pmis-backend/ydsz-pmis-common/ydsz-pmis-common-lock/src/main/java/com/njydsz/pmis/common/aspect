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

    /** Redisson 客户端，用于获取分布式锁 */
    private final RedissonClient redissonClient;

    /** Spring 参数名发现器，用于解析方法参数名以支持 SpEL 表达式 */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new DefaultParameterNameDiscoverer();

    /** SpEL 表达式解析器，用于解析锁 key 中的动态表达式 */
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /**
     * 环绕增强：解析锁 key → 尝试获取锁 → 执行目标方法 → 释放锁。
     *
     * <p>获取锁失败时抛出 {@link BizException}（RESOURCE_LOCKED）。
     * 业务异常时自动释放锁，避免锁泄漏；{@code InterruptedException} 时恢复中断标志。</p>
     *
     * @param joinPoint       连接点
     * @param distributedLock 分布式锁注解
     * @return 目标方法返回值
     * @throws Throwable    目标方法抛出的异常
     * @throws BizException 获取锁失败或被中断时抛出
     */
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
     * 解析 SpEL 表达式，获取锁的 key。
     *
     * <p>将方法参数名与参数值绑定到 SpEL 上下文，支持如
     * {@code 'order:pay:' + #orderId} 的动态 key 表达式。</p>
     *
     * @param keyExpression SpEL 表达式
     * @param joinPoint     连接点（用于提取方法签名与参数）
     * @return 解析后的锁 key 字符串
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