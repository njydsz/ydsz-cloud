package com.njydsz.common.cache.spring;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CacheProtectionGuard;

/**
 * {@link YdszCacheable} 注解的 AOP 切面实现。
 *
 * <p>在 Spring Cache 之上提供 YDSZ 差异化的三防能力（穿透/击穿/雪崩）和租户自动隔离：
 *
 * <ul>
 *   <li><b>防穿透（nullTtl）</b>：方法返回 null 时注册短 TTL 空值占位符
 *   <li><b>防击穿（sync）</b>：同一 key 的并发请求使用 {@link Semaphore} 互斥，仅一个执行方法体
 *   <li><b>防雪崩（nullTtl 随机抖动）</b>：空值占位 TTL 引入随机抖动，避免同时过期
 *   <li><b>租户隔离（tenantKey）</b>：自动从 {@link
 *       com.njydsz.common.cache.support.CacheKeyBuilder} 获取租户 ID 并追加到缓存 key
 * </ul>
 *
 * <p>前置条件：Spring 容器中必须存在 {@link YdszCacheManager} 实例（由业务模块注入或通过 {@link
 * YdszCacheableAutoConfiguration} 配置）。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see YdszCacheable
 * @see YdszCacheableAutoConfiguration
 */
@Aspect
@Component
public class YdszCacheableAspect {

  private static final Logger LOG = LoggerFactory.getLogger(YdszCacheableAspect.class);

  /** SpEL 表达式解析器 */
  private final ExpressionParser expressionParser = new SpelExpressionParser();

  /** 方法参数名发现器 */
  private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

  /** per-key 防击穿信号量（自动驱逐：WeakHashMap 语义由 TTL key 管理，此处使用定容 Map） */
  private final Map<String, Semaphore> syncLocks = new ConcurrentHashMap<>(64);

  /** Cache Manager（通过setter注入，条件依赖 YdszCacheManager 类型 Bean 存在） */
  @Autowired(required = false)
  private YdszCacheManager cacheManager;

  /**
   * 环绕通知 — 拦截 {@link YdszCacheable} 注解方法调用的切面入口。
   *
   * @param joinPoint 切点
   * @return 方法返回值或缓存值
   * @throws Throwable 方法执行异常
   */
  @Around("@annotation(com.njydsz.common.cache.spring.YdszCacheable)")
  public Object aroundCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
    if (cacheManager == null) {
      LOG.debug("YdszCacheManager 未配置，跳过缓存切面，直接执行方法");
      return joinPoint.proceed();
    }

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    YdszCacheable annotation = method.getAnnotation(YdszCacheable.class);

    if (annotation == null) {
      return joinPoint.proceed();
    }

    String cacheName = annotation.value();
    if (!StringUtils.hasText(cacheName)) {
      return joinPoint.proceed();
    }

    // 评估前置条件 condition（SpEL）
    if (StringUtils.hasText(annotation.condition())) {
      if (!evaluateCondition(annotation.condition(), joinPoint, method)) {
        return joinPoint.proceed();
      }
    }

    // 构造缓存 key
    String cacheKey = resolveKey(annotation.key(), annotation.tenantKey(), joinPoint, method);

    // 防击穿：sync=true 时使用 per-key Semaphore 互斥
    Semaphore syncSemaphore = null;
    boolean isSyncOwner = false;
    if (annotation.sync()) {
      syncSemaphore = syncLocks.computeIfAbsent(cacheKey, k -> new Semaphore(1));
      if (!syncSemaphore.tryAcquire()) {
        // 未取得锁：等待锁释放后从缓存读取
        try {
          syncSemaphore.acquire();
          syncSemaphore.release();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return joinPoint.proceed();
        }
        // 锁释放说明首个线程已完成加载，从缓存读取
        Object cached = lookupCacheValue(cacheManager, cacheName, cacheKey);
        if (cached != null || isNullPlaceholderActive(cacheManager, cacheName, cacheKey)) {
          return cached;
        }
        // 缓存仍空（可能已被淘汰），重新竞争执行权
        syncSemaphore = syncLocks.computeIfAbsent(cacheKey, k -> new Semaphore(1));
      }
      isSyncOwner = true;
    }

    // 尝试从缓存读取
    Object cached = lookupCacheValue(cacheManager, cacheName, cacheKey);
    if (cached != null) {
      releaseSync(isSyncOwner, syncSemaphore);
      return cached;
    }

    // 空值占位符命中（防穿透）：直接返回 null
    if (annotation.nullTtl() > 0
        && cacheManager.getCache(cacheName) != null
        && isNullPlaceholderActive(cacheManager, cacheName, cacheKey)) {
      releaseSync(isSyncOwner, syncSemaphore);
      return null;
    }

    // 执行方法体
    Object result;
    try {
      result = joinPoint.proceed();
    } catch (Throwable t) {
      releaseSync(isSyncOwner, syncSemaphore);
      throw t;
    }

    // 评估后置条件 unless（SpEL）
    if (StringUtils.hasText(annotation.unless())) {
      if (evaluateCondition(annotation.unless(), joinPoint, method, result)) {
        releaseSync(isSyncOwner, syncSemaphore);
        return result;
      }
    }

    // 写入缓存
    if (result == null && annotation.nullTtl() > 0) {
      // 空值占位：注册短 TTL 空值占位符防穿透（带随机抖动防雪崩）
      registerNullPlaceholder(cacheManager, cacheName, cacheKey, annotation.nullTtl());
    } else if (result != null) {
      // 正常值写入缓存
      putCacheValue(cacheManager, cacheName, cacheKey, result);
    }

    releaseSync(isSyncOwner, syncSemaphore);
    return result;
  }

  // ============================== SpEL 表达式求值 ==============================

  /**
   * 评估 SpEL 条件表达式（前置条件，无返回值）。
   *
   * @param expressionString SpEL 表达式
   * @param joinPoint 切点
   * @param method 方法
   * @return true 表示条件满足
   */
  private boolean evaluateCondition(
      String expressionString, ProceedingJoinPoint joinPoint, Method method) {
    return evaluateCondition(expressionString, joinPoint, method, null);
  }

  /**
   * 评估 SpEL 条件表达式（后置条件，含返回值）。
   *
   * @param expressionString SpEL 表达式
   * @param joinPoint 切点
   * @param method 方法
   * @param result 方法返回值（前置条件时为 null）
   * @return true 表示条件满足
   */
  private boolean evaluateCondition(
      String expressionString, ProceedingJoinPoint joinPoint, Method method, Object result) {
    try {
      Expression expression = expressionParser.parseExpression(expressionString);
      MethodBasedEvaluationContext context =
          new MethodBasedEvaluationContext(
              joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
      // 提供 #result 上下文变量（后置条件时可引用方法返回值）
      if (result != null) {
        context.setVariable("result", result);
      }
      Object value = expression.getValue(context);
      return Boolean.TRUE.equals(value);
    } catch (Exception e) {
      LOG.warn("SpEL 条件求值失败, expression={}, error={}", expressionString, e.getMessage());
      return true; // 求值失败时默认放行（不跳过缓存）
    }
  }

  // ============================== 缓存 key 解析 ==============================

  /**
   * 解析 SpEL key 表达式为最终缓存 key。
   *
   * @param keyExpression SpEL key 表达式
   * @param tenantKey 是否追加租户上下文
   * @param joinPoint 切点
   * @param method 方法
   * @return 最终缓存 key（已含 SpEL 求值结果与租户前缀）
   */
  private String resolveKey(
      String keyExpression, boolean tenantKey, ProceedingJoinPoint joinPoint, Method method) {
    String resolved;
    if (StringUtils.hasText(keyExpression)) {
      try {
        Expression expression = expressionParser.parseExpression(keyExpression);
        MethodBasedEvaluationContext context =
            new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
        Object value = expression.getValue(context);
        resolved = value != null ? value.toString() : "null";
      } catch (Exception e) {
        LOG.warn("SpEL key 求值失败, expression={}, 使用默认 key, error={}", keyExpression, e.getMessage());
        resolved = defaultKey(joinPoint);
      }
    } else {
      resolved = defaultKey(joinPoint);
    }

    if (tenantKey) {
      // 租户感知：追加当前租户 ID
      String tenant = com.njydsz.common.cache.support.CacheKeyBuilder.currentTenantId();
      resolved = tenant + ":" + resolved;
    }

    return resolved;
  }

  /**
   * 生成默认缓存 key（方法全名 + 参数拼接）。
   *
   * @param joinPoint 切点
   * @return 默认缓存 key
   */
  private String defaultKey(ProceedingJoinPoint joinPoint) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    StringBuilder sb = new StringBuilder(signature.getMethod().getName()).append(":");
    Object[] args = joinPoint.getArgs();
    if (args != null && args.length > 0) {
      for (int i = 0; i < args.length; i++) {
        if (i > 0) {
          sb.append("_");
        }
        sb.append(args[i]);
      }
    }
    return sb.toString();
  }

  // ============================== 缓存访问辅助方法 ==============================

  /**
   * 从缓存管理器查找值。
   *
   * @param manager 缓存管理器
   * @param cacheName 缓存名称
   * @param key 缓存 key
   * @return 缓存值（不存在返回 null）
   */
  private Object lookupCacheValue(CacheManager manager, String cacheName, String key) {
    try {
      return SpringCacheHelper.lookupCacheValue(manager, cacheName, key);
    } catch (Exception e) {
      LOG.debug("缓存读取({}, {}) 失败: {}", cacheName, key, e.getMessage());
      return null;
    }
  }

  /**
   * 写入缓存。
   *
   * @param manager 缓存管理器
   * @param cacheName 缓存名称
   * @param key 缓存 key
   * @param value 缓存值
   */
  private void putCacheValue(CacheManager manager, String cacheName, String key, Object value) {
    try {
      SpringCacheHelper.putCacheValue(manager, cacheName, key, value);
    } catch (Exception e) {
      LOG.warn("缓存写入({}, {}) 失败: {}", cacheName, key, e.getMessage());
    }
  }

  /**
   * 注册空值占位符（防缓存穿透）。
   *
   * @param manager 缓存管理器
   * @param cacheName 缓存名称
   * @param key 缓存 key
   * @param nullTtlSeconds 空值占位 TTL（秒）
   */
  private void registerNullPlaceholder(
      CacheManager manager, String cacheName, String key, long nullTtlSeconds) {
    try {
      Object nativeCache = SpringCacheHelper.getNativeCache(manager, cacheName);
      if (!(nativeCache instanceof Cache<?, ?> ydszCache)) {
        return;
      }
      CacheProtectionGuard.registerNullPlaceholder(
          (Cache<Object, Object>) ydszCache, key, nullTtlSeconds * 1000, nullTtlSeconds * 1000);
      // 同时在 Spring 缓存中写入 null（确保 Spring 路径也命中）
      SpringCacheHelper.putCacheValue(manager, cacheName, key, null);
    } catch (Exception e) {
      LOG.warn("空值占位符注册失败, cache={}, key={}, error={}", cacheName, key, e.getMessage());
    }
  }

  /**
   * 检查空值占位符是否仍然有效（用于防穿透命中判断）。
   *
   * @param manager 缓存管理器
   * @param cacheName 缓存名称
   * @param key 缓存 key
   * @return true 表示空值占位处于活动期
   */
  private boolean isNullPlaceholderActive(CacheManager manager, String cacheName, String key) {
    try {
      Object nativeCache = SpringCacheHelper.getNativeCache(manager, cacheName);
      if (!(nativeCache instanceof Cache<?, ?> ydszCache)) {
        return false;
      }
      return CacheProtectionGuard.isNullPlaceholderActive(
          (Cache<Object, Object>) ydszCache, key);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 释放防击穿同步锁。
   *
   * @param isSyncOwner 是否持有锁
   * @param semaphore 信号量
   */
  private void releaseSync(boolean isSyncOwner, Semaphore semaphore) {
    if (isSyncOwner && semaphore != null) {
      semaphore.release();
    }
  }
}
