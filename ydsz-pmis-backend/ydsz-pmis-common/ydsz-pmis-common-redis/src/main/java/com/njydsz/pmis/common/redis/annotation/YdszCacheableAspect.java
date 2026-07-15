package com.njydsz.pmis.common.redis.annotation;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

import com.njydsz.pmis.common.redis.service.RedisService;

/**
 * {@link YdszCacheable} 注解的 AOP 切面实现
 *
 * <p>提供三重缓存防护：
 * <ul>
 *   <li>缓存穿透防护（空值缓存）—— {@code preventPenetration}</li>
 *   <li>缓存击穿防护（分布式互斥锁）—— {@code preventStampede}</li>
 *   <li>缓存雪崩防护（随机TTL偏移）—— 默认启用</li>
 * </ul>
 *
 * <p><b>防击穿流程：</b>
 * <ol>
 *   <li>缓存未命中时，使用 Redis SETNX 获取互斥锁</li>
 *   <li>获取锁成功的线程执行数据加载并回填缓存</li>
 *   <li>获取锁失败的线程自旋等待（带超时），直到缓存被填充</li>
 *   <li>超时后降级执行数据加载（不缓存结果）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 3.0.0
 */
@Aspect
public class YdszCacheableAspect {

    private static final Logger log = LoggerFactory.getLogger(YdszCacheableAspect.class);

    /**
     * 空值标记的序列化字符串，标识缓存中存放的是空值占位
     */
    private static final String NULL_VALUE_MARKER = "__NULL__";

    /**
     * 防击穿互斥锁 Key 前缀
     */
    private static final String LOCK_KEY_PREFIX = "lock:stampede:";

    /**
     * 防击穿互斥锁默认过期时间（秒）
     * <p>超过该时间未释放则自动过期，避免死锁
     */
    private static final long LOCK_EXPIRE_SECONDS = 30;

    /**
     * SpEL 表达式解析器（线程安全，复用）
     */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private final RedisService redisService;

    /**
     * 构造切面实例
     *
     * @param redisService Redis 服务（用于读写缓存、释放锁等）
     */
    public YdszCacheableAspect(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * 环绕通知：拦截 {@link YdszCacheable} 注解方法
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>解析 SpEL Key 后查询缓存</li>
     *   <li>命中直接返回</li>
     *   <li>未命中则根据 preventStampede 决定是否走防击穿分支</li>
     *   <li>回源后将结果（含空值）回填缓存</li>
     * </ol>
     *
     * @param joinPoint 切点
     * @return 方法返回值（可能为 null）
     * @throws Throwable 原方法或缓存操作抛出的异常
     */
    @Around("@annotation(com.njydsz.pmis.common.redis.annotation.YdszCacheable)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        YdszCacheable annotation = method.getAnnotation(YdszCacheable.class);

        String cacheKey = resolveKey(annotation.key(), signature, joinPoint.getArgs());

        long ttl = applyRandomJitter(annotation.ttl(), annotation.timeUnit());

        // 1. 尝试从缓存获取
        Object cachedValue = redisService.get(cacheKey);
        if (cachedValue != null) {
            if (NULL_VALUE_MARKER.equals(cachedValue)) {
                log.debug("【YdszCacheable】缓存命中空值标记 key={}", cacheKey);
                return null;
            }
            log.debug("【YdszCacheable】缓存命中 key={}", cacheKey);
            return cachedValue;
        }

        // 2. 缓存未命中 —— 根据是否开启防击穿走不同分支
        if (annotation.preventStampede()) {
            return handleWithStampedePrevention(joinPoint, annotation, cacheKey, ttl);
        }

        // 3. 无防击穿：直接加载数据并回填缓存
        return loadAndCache(joinPoint, annotation, cacheKey, ttl);
    }

    /**
     * 防击穿逻辑：使用 Redis SETNX 互斥锁保护数据加载
     */
    private Object handleWithStampedePrevention(ProceedingJoinPoint joinPoint,
                                                 YdszCacheable annotation,
                                                 String cacheKey,
                                                 long ttl) throws Throwable {
        String lockKey = LOCK_KEY_PREFIX + cacheKey;
        String lockValue = UUID.randomUUID().toString();

        // 尝试获取互斥锁
        boolean locked = redisService.setIfAbsent(lockKey, lockValue, LOCK_EXPIRE_SECONDS);

        if (locked) {
            // 获取锁成功：执行数据加载并回填缓存
            try {
                // 双重检查：获取锁后再次检查缓存（可能已被其他线程填充）
                Object cachedValue = redisService.get(cacheKey);
                if (cachedValue != null) {
                    if (NULL_VALUE_MARKER.equals(cachedValue)) {
                        log.debug("【YdszCacheable】防击穿双重检查命中空值标记 key={}", cacheKey);
                        return null;
                    }
                    log.debug("【YdszCacheable】防击穿双重检查命中 key={}", cacheKey);
                    return cachedValue;
                }

                return loadAndCache(joinPoint, annotation, cacheKey, ttl);
            } finally {
                // 安全释放锁（Lua 脚本校验锁持有者，防止误删）
                releaseLock(lockKey, lockValue);
            }
        }

        // 获取锁失败：自旋等待缓存被填充
        return spinWaitForCache(joinPoint, annotation, cacheKey, ttl);
    }

    /**
     * 自旋等待缓存被填充（带超时）
     *
     * <p>使用指数退避策略，初始间隔 50ms，最大等待时间由 {@code lockWaitTimeout} 决定。
     * 超时后降级执行数据加载（不缓存结果）。
     */
    private Object spinWaitForCache(ProceedingJoinPoint joinPoint,
                                     YdszCacheable annotation,
                                     String cacheKey,
                                     long ttl) throws Throwable {
        long waitNanos = TimeUnit.MILLISECONDS.toNanos(50);
        long maxWaitNanos = TimeUnit.SECONDS.toNanos(annotation.lockWaitTimeout());
        long totalWaitNanos = 0;

        while (totalWaitNanos < maxWaitNanos) {
            LockSupport.parkNanos(waitNanos);
            totalWaitNanos += waitNanos;

            Object cachedValue = redisService.get(cacheKey);
            if (cachedValue != null) {
                if (NULL_VALUE_MARKER.equals(cachedValue)) {
                    log.debug("【YdszCacheable】防击穿等待命中空值标记 key={}", cacheKey);
                    return null;
                }
                log.debug("【YdszCacheable】防击穿等待命中 key={} waitMs={}", cacheKey,
                        TimeUnit.NANOSECONDS.toMillis(totalWaitNanos));
                return cachedValue;
            }

            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                break;
            }

            // 指数退避，但不超过剩余等待时间
            waitNanos = Math.min(waitNanos * 2, maxWaitNanos - totalWaitNanos);
            if (waitNanos <= 0) {
                break;
            }
        }

        // 超时降级：直接执行数据加载，不缓存结果
        log.warn("【YdszCacheable】防击穿等待超时，降级执行 key={} timeout={}s",
                cacheKey, annotation.lockWaitTimeout());
        return joinPoint.proceed();
    }

    /**
     * 执行数据加载并回填缓存
     */
    private Object loadAndCache(ProceedingJoinPoint joinPoint,
                                 YdszCacheable annotation,
                                 String cacheKey,
                                 long ttl) throws Throwable {
        Object result = joinPoint.proceed();

        if (result == null && annotation.preventPenetration()) {
            Duration nullTtl = Duration.of(annotation.nullValueTtl(), annotation.timeUnit().toChronoUnit());
            redisService.set(cacheKey, NULL_VALUE_MARKER, nullTtl);
            log.debug("【YdszCacheable】缓存空值防穿透 key={} ttl={}s", cacheKey, annotation.nullValueTtl());
            return null;
        }

        if (result != null) {
            Duration ttlDuration = Duration.of(ttl, annotation.timeUnit().toChronoUnit());
            redisService.set(cacheKey, result, ttlDuration);
            log.debug("【YdszCacheable】缓存写入成功 key={} ttl={}s", cacheKey, ttl);
        }

        return result;
    }

    /**
     * 使用 Lua 脚本安全释放分布式锁（校验锁持有者，防止误删其他线程的锁）
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            redisService.executeScript(UNLOCK_LUA, Collections.singletonList(lockKey), Long.class, lockValue);
        } catch (Exception e) {
            log.error("【YdszCacheable】释放防击穿锁失败 | lockKey={} | error={}", lockKey, e.getMessage());
        }
    }

    /**
     * 解析 SpEL 缓存键表达式
     *
     * <p>使用 {@link SimpleEvaluationContext} 替代 {@code StandardEvaluationContext}，
     * 禁止访问 Bean 引用、类型引用和方法引用，防止 SpEL 注入攻击。
     * 仅支持读取变量和属性访问，满足缓存键解析需求。
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
        double jitter = 1.0 + ThreadLocalRandom.current().nextDouble(-0.1, 0.1);
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
    @Around("@annotation(com.njydsz.pmis.common.redis.annotation.YdszCacheEvict)")
    public Object aroundEvict(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        YdszCacheEvict annotation = method.getAnnotation(YdszCacheEvict.class);

        Object result = joinPoint.proceed();

        String cacheKey = resolveKey(annotation.key(), signature, joinPoint.getArgs());
        redisService.delete(cacheKey);
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
    @Around("@annotation(com.njydsz.pmis.common.redis.annotation.YdszCachePut)")
    public Object aroundPut(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        YdszCachePut annotation = method.getAnnotation(YdszCachePut.class);

        Object result = joinPoint.proceed();

        if (result != null) {
            String cacheKey = resolveKey(annotation.key(), signature, joinPoint.getArgs());
            Duration ttlDuration = Duration.of(annotation.ttl(), annotation.timeUnit().toChronoUnit());
            redisService.set(cacheKey, result, ttlDuration);
            log.debug("【YdszCachePut】缓存更新 | key={} | ttl={}s", cacheKey, annotation.ttl());
        }

        return result;
    }
}
