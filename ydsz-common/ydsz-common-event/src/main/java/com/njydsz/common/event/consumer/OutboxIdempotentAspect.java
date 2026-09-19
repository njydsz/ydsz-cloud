package com.njydsz.common.event.consumer;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.njydsz.common.event.model.OutboxMessage;

/**
 * Outbox 幂等消费 AOP 切面（F-3）
 *
 * <p>拦截带有 {@link OutboxIdempotentConsumer} 注解的方法，通过 Redis / DB / JVM 内存实现消费去重：
 *
 * <ul>
 *   <li>解析注解中的 SpEL 表达式获取幂等键（如 {@code #message.eventId}）
 *   <li>尝试 SETNX 幂等键（TTL = expireSeconds）
 *   -   成功 → 执行方法体
 *   -   失败 → 跳过方法体（重复消费），记录 DEBUG 日志
 * </ul>
 *
 * <p><b>条件装配：</b>需要 spring-boot-starter-aop 在 classpath 并由业务模块配置切面 Bean。 *
 *
 * <p><b>编码规范遵循：</b>YDIZ-WARN-001（不使用 @SuppressWarnings，所有告警通过空安全检查和 fallback 解决）
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see OutboxIdempotentConsumer
 */
@Aspect
public class OutboxIdempotentAspect {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(OutboxIdempotentAspect.class);

  /** SpEL 表达式解析器 */
  private final ExpressionParser parser = new SpelExpressionParser();

  /** 参数名发现器 */
  private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

  /** Redis SETNX 客户端提供者（可选，优先使用 Redis） */
  private final ObjectProvider<Object> redisProvider;

  /** JVM 本地去重缓存（Redis 不可用时的降级方案） */
  private final ConcurrentHashMap<String, Long> localCache = new ConcurrentHashMap<>(256);

  /** 是否启用日志 */
  private final boolean isRedisAvailable;

  /**
   * 构造幂等消费切面
   *
   * @param redisProvider Redis StringRedisTemplate 提供者（可选）
   */
  public OutboxIdempotentAspect(ObjectProvider<Object> redisProvider) {
    this.redisProvider = redisProvider;
    this.isRedisAvailable = redisProvider.getIfAvailable() != null;
    if (!isRedisAvailable) {
      LOG.info("Redis not available for OutboxIdempotentAspect. Using JVM local cache fallback.");
    }
  }

  /**
   * 环绕通知：拦截带有 @OutboxIdempotentConsumer 注解的方法
   *
   * @param joinPoint 连接点
   * @return 方法返回值（或 null 跳过方法执行）
   * @throws Throwable 方法执行异常
   */
  @Around("@annotation(com.njydsz.common.event.consumer.OutboxIdempotentConsumer)")
  public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    OutboxIdempotentConsumer annotation = method.getAnnotation(OutboxIdempotentConsumer.class);

    if (annotation == null) {
      return joinPoint.proceed();
    }

    // 获取幂等键
    String idempotencyKey = resolveIdempotencyKey(joinPoint, annotation.idempotencyKey());
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      // 无法解析幂等键 → 直接执行方法
      LOG.debug("Cannot resolve idempotency key for {}, proceed directly", method.getName());
      return joinPoint.proceed();
    }

    String cacheKey = "ydsz:outbox:idempotent:" + idempotencyKey;

    // 尝试获取幂等锁
    boolean acquired = tryAcquire(cacheKey, annotation.expireSeconds());

    if (!acquired) {
      // 重复消费，跳过方法体
      LOG.debug("Duplicate message detected, skip method execution: key={}, method={}", cacheKey,
          method.getName());
      return null;
    }

    try {
      return joinPoint.proceed();
    } catch (Throwable e) {
      if (annotation.removeOnFailure()) {
        // 消费失败，移除幂等标记（允许重试）
        release(cacheKey);
      }
      throw e;
    }
  }

  /**
   * 解析 SpEL 表达式获取幂等键
   *
   * @param joinPoint 连接点
   * @param spelExpression SpEL 表达式
   * @return 幂等键（无法解析时返回 null）
   */
  private String resolveIdempotencyKey(ProceedingJoinPoint joinPoint, String spelExpression) {
    try {
      Object[] args = joinPoint.getArgs();
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      String[] parameterNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());

      StandardEvaluationContext context = new StandardEvaluationContext();
      // 设置 #message 变量（第一个参数）
      if (args.length > 0 && args[0] instanceof OutboxMessage) {
        context.setVariable("message", args[0]);
      }
      // 设置所有命名参数
      if (parameterNames != null) {
        for (int i = 0; i < parameterNames.length && i < args.length; i++) {
          context.setVariable(parameterNames[i], args[i]);
        }
      }

      Expression expression = parser.parseExpression(spelExpression);
      Object value = expression.getValue(context);
      return value != null ? value.toString() : null;
    } catch (Exception e) {
      LOG.debug("Failed to resolve SpEL '{}': {}", spelExpression, e.getMessage());
      return null;
    }
  }

  /**
   * 尝试获取幂等锁
   *
   * @param key 缓存键
   * @param expireSeconds 过期秒数
   * @return true 表示获取成功（首次消费）
   */
  private boolean tryAcquire(String key, int expireSeconds) {
    if (isRedisAvailable) {
      return tryAcquireWithRedis(key, expireSeconds);
    }
    return tryAcquireLocal(key, expireSeconds);
  }

  /**
   * 通过 Redisson / Redis 尝试 SETNX
   *
   * @param key 缓存键
   * @param expireSeconds 过期秒数
   * @return true 表示 SETNX 成功
   */
  private boolean tryAcquireWithRedis(String key, int expireSeconds) {
    try {
      Object redisTemplate = redisProvider.getIfAvailable();
      if (redisTemplate == null) {
        return tryAcquireLocal(key, expireSeconds);
      }

      // 通过反射调用 StringRedisTemplate.opsForValue().setIfAbsent()
      Object ops = redisTemplate.getClass().getMethod("opsForValue").invoke(redisTemplate);
      if (ops == null) {
        return tryAcquireLocal(key, expireSeconds);
      }
      Object result = ops.getClass().getMethod("setIfAbsent", Object.class, Object.class,
          long.class, TimeUnit.class).invoke(ops, key, "1", (long) expireSeconds, TimeUnit.SECONDS);

      return Boolean.TRUE.equals(result);
    } catch (RuntimeException e) {
      LOG.debug("Redis SETNX failed, fallback to local cache: {}", e.getMessage());
      return tryAcquireLocal(key, expireSeconds);
    } catch (Exception e) {
      LOG.debug("Redis operation exception, fallback to local cache: {}", e.getMessage());
      return tryAcquireLocal(key, expireSeconds);
    }
  }

  /**
   * 通过 JVM 本地 ConcurrentHashMap 实现去重（单实例有效）
   *
   * @param key 缓存键
   * @param expireSeconds 过期秒数
   * @return true 表示获取成功
   */
  private boolean tryAcquireLocal(String key, int expireSeconds) {
    long now = System.currentTimeMillis();
    long expireAt = now + (expireSeconds * 1000L);
    Long previous = localCache.putIfAbsent(key, expireAt);
    if (previous == null) {
      return true;
    }
    // 检查是否已过期
    if (previous < now) {
      // 已过期，替换
      localCache.put(key, expireAt);
      return true;
    }
    return false;
  }

  /**
   * 释放幂等锁
   *
   * @param key 缓存键
   */
  private void release(String key) {
    if (isRedisAvailable) {
      try {
        Object redisTemplate = redisProvider.getIfAvailable();
        if (redisTemplate != null) {
          redisTemplate.getClass().getMethod("delete", Object.class).invoke(redisTemplate, key);
        }
      } catch (Throwable e) {
        LOG.debug("Redis delete failed: {}", e.getMessage());
        localCache.remove(key);
      }
    }
    localCache.remove(key);
  }
}
