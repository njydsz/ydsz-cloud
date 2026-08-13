package com.njydsz.common.redis.annotation;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.service.RedisCacheGuard;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * {@link YdszCacheable} 注解的 AOP 切面实现
 *
 * <p>提供三重缓存防护：
 * <ul>
 *   <li>缓存穿透防护（空值缓存）—— {@code preventPenetration}</li>
 *   <li>缓存击穿防护（分布式互斥锁 + WatchDog 续期）—— {@code preventStampede}</li>
 *   <li>缓存雪崩防护（随机TTL偏移）—— 默认启用</li>
 * </ul>
 *
 * <p><b>逻辑统一说明：</b>
 * <p>本切面负责 SpEL 表达式解析和 AOP 织入，核心缓存防护逻辑
 * （分布式锁、WatchDog 续期、单flight 等待、空值缓存）统一委托给
 * {@link RedisCacheGuard}，避免与 {@code YdszCacheableAspect} 各自实现
 * 一套锁机制导致的逻辑分歧和维护成本。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Aspect
public class YdszCacheableAspect {

    /** 缓存 TTL 随机抖动范围（比例） */
    private static final double TTL_JITTER_RANGE = 0.1;

    /** SpEL 表达式解析器（线程安全，复用） */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private final RedisCacheGuard redisCacheGuard;
    private final RedisStringOps redisStringOps;

    /**
     * 构造切面实例
     *
     * <p>注入 {@link RedisCacheGuard} 统一处理缓存防护逻辑，
     * 通过 SpEL 解析后的缓存键调用防护方法，消除重复的锁代码。
     *
     * @param redisStringOps  Redis String 操作（用于 YdszCacheEvict/YdszCachePut）
     * @param redisTemplate   Redis 模板（用于构建 RedisCacheGuard）
     * @param redisProperties Redis 配置（用于获取 CacheGuard 参数）
     */
    public YdszCacheableAspect(RedisStringOps redisStringOps,
                                RedisTemplate<String, Object> redisTemplate,
                                RedisProperties redisProperties) {
        this.redisStringOps = redisStringOps;
        // 构建 RedisCacheGuard 实例，复用可配置参数（breakdownLockLeaseSeconds 等）
        int nullValueTtl = redisProperties != null ? redisProperties.getNullValueTtlSeconds() : 1800;
        this.redisCacheGuard = new RedisCacheGuard(redisStringOps, redisTemplate,
                nullValueTtl,
                redisProperties != null ? redisProperties.getCacheGuard() : new RedisProperties.CacheGuard());
    }

    /**
     * 环绕通知：拦截 {@link YdszCacheable} 注解方法
     *
     * <p>执行流程：
     * <ol>
     *   <li>解析 SpEL Key 后根据 preventStampede 选择防护策略</li>
     *   <li>开启防击穿：委托 {@link RedisCacheGuard#antiBreakdown} 统一处理</li>
     *   <li>未开启防击穿：直接加载并使用防穿透空值缓存回填</li>
     * </ol>
     *
     * @param joinPoint 切点
     * @return 方法返回值（可能为 null）
     * @throws Throwable 原方法或缓存操作抛出的异常
     */
    @Around("@annotation(com.njydsz.common.redis.annotation.YdszCacheable)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        YdszCacheable annotation = method.getAnnotation(YdszCacheable.class);

        String cacheKey = resolveKey(annotation.key(), signature, joinPoint.getArgs());
        long ttlWithJitter = applyRandomJitter(annotation.ttl(), annotation.timeUnit());

        // 委托 RedisCacheGuard 统一处理带防击穿的缓存读取
        if (annotation.preventStampede()) {
            return redisCacheGuard.antiBreakdown(cacheKey, ttlWithJitter,
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable e) {
                            if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                            }
                            throw new RuntimeException("缓存加载执行失败", e);
                        }
                    },
                    Object.class);
        }

        // 无防击穿：使用 antiPenetration 的空值缓存防穿透
        return redisCacheGuard.antiPenetration(cacheKey,
                () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException("缓存加载执行失败", e);
                    }
                },
                Object.class,
                (int) annotation.nullValueTtl());
    }

    /**
     * 解析 SpEL 缓存键表达式
     *
     * <p>使用 {@link SimpleEvaluationContext} 替代 {@code StandardEvaluationContext}，
     * 禁止访问 Bean 引用、类型引用和方法引用，防止 SpEL 注入攻击。
     * 仅支持读取变量和属性访问，满足缓存键解析需求。
     *
     * @param keyExpression SpEL 键表达式
     * @param signature     方法签名（用于获取参数名）
     * @param args          方法实参
     * @return 解析后的缓存键
     */
    private String resolveKey(String keyExpression, MethodSignature signature, Object[] args) {
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        String[] paramNames = signature.getParameterNames();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return PARSER.parseExpression(keyExpression).getValue(context, String.class);
    }

    private long applyRandomJitter(long ttl, TimeUnit timeUnit) {
        long ttlSeconds = timeUnit.toSeconds(ttl);
        double jitter = 1.0 + ThreadLocalRandom.current().nextDouble(-TTL_JITTER_RANGE, TTL_JITTER_RANGE);
        return Math.max(1, (long) (ttlSeconds * jitter));
    }

    /**
     * 环绕通知：拦截 {@link YdszCacheEvict} 注解方法
     *
     * <p>方法执行成功后删除对应的 Redis 缓存。
     *
     * @param joinPoint 切点
     * @return 方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(com.njydsz.common.redis.annotation.YdszCacheEvict)")
    public Object aroundEvict(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        YdszCacheEvict annotation = method.getAnnotation(YdszCacheEvict.class);

        Object result = joinPoint.proceed();

        String cacheKey = resolveKey(annotation.key(), signature, joinPoint.getArgs());
        redisStringOps.del(cacheKey);
        log.debug("【YdszCacheEvict】缓存淘汰 | key={}", cacheKey);

        return result;
    }

    /**
     * 环绕通知：拦截 {@link YdszCachePut} 注解方法
     *
     * <p>方法执行成功后将返回值写入 Redis 缓存。与 {@link #around} 不同，
     * 此方法不查询缓存，始终执行方法体。
     *
     * @param joinPoint 切点
     * @return 方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(com.njydsz.common.redis.annotation.YdszCachePut)")
    public Object aroundPut(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        YdszCachePut annotation = method.getAnnotation(YdszCachePut.class);

        Object result = joinPoint.proceed();

        if (result != null) {
            String cacheKey = resolveKey(annotation.key(), signature, joinPoint.getArgs());
            Duration ttlDuration = Duration.of(annotation.ttl(), annotation.timeUnit().toChronoUnit());
            redisStringOps.set(cacheKey, result, ttlDuration);
            log.debug("【YdszCachePut】缓存更新 | key={} | ttl={}s", cacheKey, annotation.ttl());
        }

        return result;
    }
}
