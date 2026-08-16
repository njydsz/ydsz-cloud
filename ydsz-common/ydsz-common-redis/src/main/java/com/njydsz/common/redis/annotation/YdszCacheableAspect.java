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
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * {@link YdszCacheable} 注解的 AOP 切面实现
 *
 * <p>提供缓存防护能力：
 * <ul>
 *   <li>缓存穿透防护（空值缓存）—— {@code preventPenetration}</li>
 *   <li>缓存雪崩防护（随机TTL偏移）—— 默认启用</li>
 * </ul>
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

    private final RedisStringOps redisStringOps;

    /**
     * 构造切面实例
     *
     * @param redisStringOps  Redis String 操作（用于缓存读写、空值缓存等）
     * @param redisTemplate   Redis 模板（预留，供未来扩展）
     * @param redisProperties Redis 配置（预留，供未来扩展）
     */
    public YdszCacheableAspect(RedisStringOps redisStringOps,
                                org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate,
                                com.njydsz.common.redis.config.RedisProperties redisProperties) {
        this.redisStringOps = redisStringOps;
    }

    /**
     * 环绕通知：拦截 {@link YdszCacheable} 注解方法
     *
     * <p>执行流程：
     * <ol>
     *   <li>解析 SpEL Key 后检查缓存命中</li>
     *   <li>缓存未命中：执行方法体，结果写入缓存（含 TTL 抖动防雪崩）</li>
     *   <li>空值缓存：方法返回 null 时写入空值标记，防止缓存穿透</li>
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

        // 查询缓存
        Object cached = redisStringOps.get(cacheKey);
        if (cached != null) {
            // 命中空值缓存标记，返回 null
            if ("NULL".equals(cached)) {
                return null;
            }
            return cached;
        }

        // 缓存未命中：执行方法体
        Object result = joinPoint.proceed();

        if (result != null) {
            // 写入缓存（带 TTL 抖动防雪崩）
            redisStringOps.set(cacheKey, result, Duration.ofSeconds(ttlWithJitter));
        } else if (annotation.preventPenetration()) {
            // 空值缓存防穿透
            redisStringOps.set(cacheKey, "NULL", Duration.ofSeconds(annotation.nullValueTtl()));
        }

        return result;
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
