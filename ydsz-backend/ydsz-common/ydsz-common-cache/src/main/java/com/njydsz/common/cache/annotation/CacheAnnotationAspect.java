package com.njydsz.common.cache.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * 缓存注解 AOP 切面 — {@code @Cached} / {@code @CacheInvalidate} / {@code @CacheRefresh} 声明式缓存
 *
 * <p>拦截标注了 {@link Cached}、{@link CacheInvalidate} 的方法，通过 SpEL 表达式解析缓存 key，
 * 自动从 {@link CacheManager} 获取或创建缓存实例，实现声明式缓存读写/失效。
 *
 * <p>功能说明：
 *
 * <ul>
 *   <li>{@code @Cached}：方法返回值自动缓存，命中时跳过方法执行
 *   <li>{@code @CacheInvalidate}：方法执行后/前清除指定缓存条目
 *   <li>支持 SpEL key 表达式（如 {@code #userId}、{@code #user.id}）
 *   <li>支持 condition 条件缓存
 *   <li>支持 unlessNull 空值排除
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Cached(name = "users", key = "#userId", expireAfterWrite = 30, timeUnit = TimeUnit.MINUTES)
 * public User getUser(String userId) {
 *   return userDao.findById(userId);
 * }
 *
 * @CacheInvalidate(name = "users", key = "#user.id")
 * public void updateUser(User user) {
 *   userDao.update(user);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@Aspect
public class CacheAnnotationAspect {

  private static final Logger log = LoggerFactory.getLogger(CacheAnnotationAspect.class);

  private final CacheManager cacheManager;
  private final SpelExpressionParser parser = new SpelExpressionParser();
  private final DefaultParameterNameDiscoverer parameterNameDiscoverer =
      new DefaultParameterNameDiscoverer();

  /** 缓存最后刷新时间戳（cacheName:key → nanoTime） */
  private final ConcurrentHashMap<String, Long> refreshTimestamps = new ConcurrentHashMap<>();

  /**
   * 创建缓存注解切面
   *
   * @param cacheManager Spring CacheManager（通常为 YdszCacheManager）
   */
  public CacheAnnotationAspect(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  /**
   * 拦截 {@code @Cached} 注解方法 — 先查缓存，未命中则执行方法并缓存结果
   *
   * @param joinPoint AOP 连接点
   * @param cached 缓存注解
   * @return 缓存值或方法执行结果
   * @throws Throwable 方法执行异常
   */
  @Around("@annotation(cached)")
  public Object aroundCached(ProceedingJoinPoint joinPoint, Cached cached) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Object[] args = joinPoint.getArgs();

    // 解析缓存 key
    Object cacheKey = resolveKey(cached.key(), method, args);
    if (cacheKey == null) {
      return joinPoint.proceed();
    }

    // 条件判断
    if (!cached.condition().isEmpty()) {
      Boolean condition = evaluateExpression(cached.condition(), method, args, Boolean.class);
      if (Boolean.FALSE.equals(condition)) {
        return joinPoint.proceed();
      }
    }

    org.springframework.cache.Cache springCache = getOrCreateCache(cached.name());
    if (springCache == null) {
      log.warn("无法获取缓存实例: {}, 直接执行方法", cached.name());
      return joinPoint.proceed();
    }

    // 先查缓存
    org.springframework.cache.Cache.ValueWrapper valueWrapper = springCache.get(cacheKey);
    if (valueWrapper != null) {
      Object cachedValue = valueWrapper.get();
      if (cachedValue == null && cached.unlessNull()) {
        // 空值缓存标记，直接返回 null
        return null;
      }
      // 检查 @CacheRefresh 注解
      CacheRefresh cacheRefresh = method.getAnnotation(CacheRefresh.class);
      if (cacheRefresh != null && cacheRefresh.refreshAfterWrite() > 0) {
        handleCacheRefresh(cacheRefresh, springCache, cached.name(), cacheKey, joinPoint, method, args);
      }
      return cachedValue;
    }

    // 缓存未命中，执行方法
    Object result = joinPoint.proceed();

    // 空值排除
    if (result == null && cached.unlessNull()) {
      return null;
    }

    // 写入缓存
    springCache.put(cacheKey, result);
    // 记录刷新时间戳
    CacheRefresh cacheRefresh = method.getAnnotation(CacheRefresh.class);
    if (cacheRefresh != null && cacheRefresh.refreshAfterWrite() > 0) {
      refreshTimestamps.put(cached.name() + ":" + cacheKey, System.nanoTime());
    }
    return result;
  }

  /**
   * 拦截 {@code @CacheInvalidate} 注解方法 — 方法执行后/前清除缓存
   *
   * @param joinPoint AOP 连接点
   * @param invalidate 缓存失效注解
   * @return 方法执行结果
   * @throws Throwable 方法执行异常
   */
  @Around("@annotation(invalidate)")
  public Object aroundCacheInvalidate(
      ProceedingJoinPoint joinPoint, CacheInvalidate invalidate) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Object[] args = joinPoint.getArgs();

    org.springframework.cache.Cache springCache = getOrCreateCache(invalidate.name());
    if (springCache == null) {
      log.warn("无法获取缓存实例: {}, 跳过缓存失效", invalidate.name());
      return joinPoint.proceed();
    }

    // 方法执行前清除
    if (invalidate.beforeInvocation()) {
      evictCache(springCache, invalidate, method, args);
    }

    Object result;
    try {
      result = joinPoint.proceed();
    } catch (Throwable e) {
      // 方法执行前清除时，异常不回滚清除
      if (!invalidate.beforeInvocation()) {
        throw e;
      }
      throw e;
    }

    // 方法执行后清除
    if (!invalidate.beforeInvocation()) {
      evictCache(springCache, invalidate, method, args);
    }

    return result;
  }

  /** 执行缓存清除操作 */
  private void evictCache(
      org.springframework.cache.Cache springCache, CacheInvalidate invalidate, Method method, Object[] args) {
    if (invalidate.allEntries()) {
      springCache.clear();
      log.debug("清除全部缓存: name={}", invalidate.name());
    } else {
      Object cacheKey = resolveKey(invalidate.key(), method, args);
      if (cacheKey != null) {
        springCache.evict(cacheKey);
        log.debug("清除缓存条目: name={}, key={}", invalidate.name(), cacheKey);
      }
    }
  }

  /** 获取或创建 Spring Cache 实例 */
  private org.springframework.cache.Cache getOrCreateCache(String cacheName) {
    try {
      return cacheManager.getCache(cacheName);
    } catch (Exception e) {
      log.warn("获取缓存实例失败: name={}", cacheName, e);
      return null;
    }
  }

  /** 解析 SpEL key 表达式 */
  private Object resolveKey(String keyExpression, Method method, Object[] args) {
    if (keyExpression == null || keyExpression.isEmpty()) {
      // 默认使用方法参数的 hashCode
      if (args == null || args.length == 0) {
        return "empty";
      }
      if (args.length == 1) {
        return args[0];
      }
      return Arrays.hashCode(args);
    }
    return evaluateExpression(keyExpression, method, args, Object.class);
  }

  /** 评估 SpEL 表达式 */
  private <T> T evaluateExpression(
      String expression, Method method, Object[] args, Class<T> expectedResultType) {
    EvaluationContext context = createEvaluationContext(method, args);
    Expression exp = parser.parseExpression(expression);
    return exp.getValue(context, expectedResultType);
  }

  /**
   * 处理 @CacheRefresh 逻辑
   *
   * <p>当缓存命中且超过刷新间隔时：
   * <ul>
   *   <li>SWR 模式：返回旧值 + 异步刷新
   *   <li>非 SWR 模式：异步刷新（不阻塞当前请求）
   * </ul>
   */
  private void handleCacheRefresh(
      CacheRefresh cacheRefresh,
      org.springframework.cache.Cache springCache,
      String cacheName,
      Object cacheKey,
      ProceedingJoinPoint joinPoint,
      Method method,
      Object[] args) {
    String refreshKey = cacheName + ":" + cacheKey;
    Long lastRefresh = refreshTimestamps.get(refreshKey);
    long now = System.nanoTime();
    long refreshIntervalNanos = cacheRefresh.timeUnit().toNanos(cacheRefresh.refreshAfterWrite());

    if (lastRefresh != null && (now - lastRefresh) < refreshIntervalNanos) {
      // 未到刷新间隔，跳过
      return;
    }

    // 更新刷新时间戳
    refreshTimestamps.put(refreshKey, now);

    if (cacheRefresh.staleWhileRevalidate()) {
      // SWR 模式：异步刷新，不阻塞当前请求
      CompletableFuture.runAsync(
          () -> {
            try {
              Object freshValue = joinPoint.proceed();
              if (freshValue != null) {
                springCache.put(cacheKey, freshValue);
                log.debug("SWR 异步刷新完成: cache={}, key={}", cacheName, cacheKey);
              }
            } catch (Throwable e) {
              log.warn("SWR 异步刷新失败: cache={}, key={}", cacheName, cacheKey, e);
            }
          },
          ForkJoinPool.commonPool());
    } else {
      // 非 SWR 模式：异步刷新
      CompletableFuture.runAsync(
          () -> {
            try {
              Object freshValue = joinPoint.proceed();
              if (freshValue != null) {
                springCache.put(cacheKey, freshValue);
                log.debug("异步刷新完成: cache={}, key={}", cacheName, cacheKey);
              }
            } catch (Throwable e) {
              log.warn("异步刷新失败: cache={}, key={}", cacheName, cacheKey, e);
            }
          },
          ForkJoinPool.commonPool());
    }
  }
  private EvaluationContext createEvaluationContext(Method method, Object[] args) {
    MethodBasedEvaluationContext context =
        new MethodBasedEvaluationContext(null, method, args, parameterNameDiscoverer);
    // 注册方法参数为 SpEL 变量
    Parameter[] parameters = method.getParameters();
    for (int i = 0; i < parameters.length && i < args.length; i++) {
      context.setVariable(parameters[i].getName(), args[i]);
    }
    return context;
  }
}
