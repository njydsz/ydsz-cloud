package com.njydsz.common.cache.aspect;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.annotation.CacheKeyBuilder;
import com.njydsz.common.cache.annotation.MultiLevelCacheable;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.api.CacheProtectionGuard;

/**
 * {@link MultiLevelCacheable} 注解的 AOP 切面实现。
 *
 * <p>在方法调用外层包入 L1（本地 {@link com.njydsz.common.cache.YdszCache}） + L2（Redis StringOps）
 * 两级缓存，执行流程：
 *
 * <ol>
 *   <li>解析 {@link MultiLevelCacheable#key()} SpEL 表达式 → 生成完整缓存键</li>
 *   <li>L1 命中（非空值占位保护判断） → 直接返回</li>
 *   <li>L2（Redis）命中 → {@link L2ValueSerializer#deserialize} 回填 L1，返回</li>
 *   <li>未命中 → 执行方法体 → 写入 L1 + L2</li>
 * </ol>
 *
 * <p>防穿透：启用 {@link MultiLevelCacheable#useProtection()} 时，借助 {@link
 * com.njydsz.common.cache.api.CacheProtectionGuard} 对同一 key 的并发加载去重（per-key 单飞信号 +
 * 空值占位符注册），避免击穿 DB。
 *
 * <p>L1 缓存生命周期由切面内部维护（与注解声明同生命周期），按「注解实例 + 方法签名」维度独立构建， TTL 与最大容量以各方法声明的 {@link MultiLevelCacheable#localTTL()} / {@link
 * MultiLevelCacheable#localMaxSize()} 为准。
 *
 * <p><b>前置条件：</b>
 *
 * <ul>
 *   <li>Spring AOP + AspectJ 位于 classpath（{@code spring-aop}、{@code aspectjweaver}）</li>
 *   <li>启用 @EnableAspectJAutoProxy</li>
 *   <li>L2：Spring 容器中存在 {@link StringRedisTemplate} Bean 时启用 L2；否则降级为 L1 only</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see MultiLevelCacheable
 * @see CacheKeyBuilder
 * @see com.njydsz.common.cache.api.CacheProtectionGuard
 */
@Aspect
@Component
public class MultiLevelCacheAspect {

  private static final Logger LOG = LoggerFactory.getLogger(MultiLevelCacheAspect.class);

  /** L2 空值占位字符串（与 MultiLevelCacheTemplate.EMPTY_RESULT_MARKER 语义一致） */
  private static final String EMPTY_RESULT_MARK = "__NULL__";

  /** 空值占位符 TTL 比率（占位 TTL = 正常 TTL / 此值，至少 1 秒） */
  private static final int NULL_TTL_RATIO = 3;

  /**
   * L1 本地缓存注册表：key = 注解身份（方法签名 + localTTL + localMaxSize + remoteTTL），
   * value = 为该注解配置构建的 YdszCache 实例。
   *
   * <p>同一注解声明（同方法同一注解）共享一个 L1 缓存，避免重复创建。
   */
  private final Map<CacheIdentity, Cache<Object, Object>> l1CacheRegistry = new ConcurrentHashMap<>();

  /** L2 由 StringRedisTemplate 提供，缺失时降级 L1-only */
  private final ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider;

  /** L2 序列化器（默认 DefaultL2ValueSerializer） */
  private final L2ValueSerializer l2ValueSerializer;

  /** per-key 锁（防击穿辅助：useProtection=false 时的轻量保护，避免惊群） */
  private final Map<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

  /**
   * 构造切面，注入可选的 Redis 模板与序列化器。
   *
   * @param stringRedisTemplateProvider Redis 模板提供器（可为空，空时 L2 降级禁用）
   * @param l2ValueSerializer           L2 值序列化器（可为空时，使用默认实现）
   */
  public MultiLevelCacheAspect(
      ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider,
      ObjectProvider<L2ValueSerializer> l2ValueSerializer) {
    this.stringRedisTemplateProvider = stringRedisTemplateProvider;
    L2ValueSerializer serializer = l2ValueSerializer.getIfAvailable();
    this.l2ValueSerializer = serializer != null ? serializer : DefaultL2ValueSerializer.create();
  }

  // ============================== 切面入口 ==============================

  /**
   * 环绕通知 — 拦截带 {@link MultiLevelCacheable} 注解的方法调用，包入多级缓存逻辑。
   *
   * @param joinPoint         切点
   * @param multiLevelCacheable 方法上的注解实例
   * @return 方法返回值或缓存值
   * @throws Throwable 方法执行异常
   */
  @Around("@annotation(multiLevelCacheable)")
  public Object aroundCacheable(ProceedingJoinPoint joinPoint, MultiLevelCacheable multiLevelCacheable)
      throws Throwable {
    // 解析 SpEL 生成完整缓存 key
    String cacheKey = CacheKeyBuilder.resolveKey(multiLevelCacheable.key(), joinPoint);
    if (cacheKey == null) {
      // SpEL 求值失败时放弃缓存，直放
      LOG.warn("多级缓存 key 解析为 null，直放方法: {}", joinPoint.getSignature());
      return joinPoint.proceed();
    }

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();
    Class<?> returnType = method.getReturnType();

    // 获取对应 L1 缓存
    Cache<Object, Object> l1Cache = getOrCreateL1Cache(multiLevelCacheable, signature);

    // ---------------- L1 查询 ----------------
    Object l1Hit = l1Cache.getIfPresent(cacheKey);
    if (l1Hit != null) {
      if (EMPTY_RESULT_MARK.equals(l1Hit)) {
        return nullGuard(returnType);
      }
      return l1Hit;
    }

    // 空值占位命中（防穿透）
    if (CacheProtectionGuard.isNullPlaceholderKey(l1Cache, cacheKey)) {
      return nullGuard(returnType);
    }

    // ---------------- L2 查询（可选） ----------------
    StringRedisTemplate redis = stringRedisTemplateProvider.getIfAvailable();
    boolean l2Enabled = (redis != null);

    if (l2Enabled) {
      Object l2Hit = lookupL2(redis, cacheKey, returnType);
      if (l2Hit != null) {
        // L2 命中 → 回填 L1（不含空标记判断，回填的都是真值）
        l1Cache.put(cacheKey, l2Hit);
        return l2Hit;
      }
      if (isL2NullMarker(redis, cacheKey)) {
        // L2 存在空标记 → 回填 L1 并返回 null
        l1Cache.put(cacheKey, (Object) EMPTY_RESULT_MARK);
        return nullGuard(returnType);
      }
    }

    // ---------------- 未命中 → 加载并回写 ----------------
    if (multiLevelCacheable.useProtection()) {
      // 防击穿模式：使用 CacheProtectionGuard 单飞加载 + 空值占位
      Object loaded =
          CacheProtectionGuard.getWithProtection(
              l1Cache,
              cacheKey,
              k -> {
                try {
                  return loadFromSource(joinPoint, cacheKey, returnType, redis, l2Enabled, multiLevelCacheable);
                } catch (RuntimeException e) {
                  throw e;
                } catch (Throwable t) {
                  throw new RuntimeException("多级缓存 loadFromSource 异常: " + t.getMessage(), t);
                }
              },
              nullPlaceholderTtlMs(multiLevelCacheable.remoteTTL()),
              l2Enabled ? multiLevelCacheable.remoteTTL() : multiLevelCacheable.localTTL());
      return unwrapGuard(loaded, returnType);
    }

    // 非防击穿模式：轻量 per-key 锁防止惊群
    ReentrantLock lock = keyLocks.computeIfAbsent(cacheKey, k -> new ReentrantLock());
    if (!lock.tryLock()) {
      // 未取得锁，等待持有者完成加载后从 L1 重读
      try {
        lock.lock();
      } finally {
        lock.unlock();
      }
      Object latest = l1Cache.getIfPresent(cacheKey);
      if (latest != null) {
        return EMPTY_RESULT_MARK.equals(latest) ? nullGuard(returnType) : latest;
      }
      // 极端情况：加载完成后已被驱逐 → 重试一次
    }
    try {
      // double-check
      Object afterLock = l1Cache.getIfPresent(cacheKey);
      if (afterLock != null) {
        return EMPTY_RESULT_MARK.equals(afterLock) ? nullGuard(returnType) : afterLock;
      }
      Object loaded =
          loadFromSource(joinPoint, cacheKey, returnType, redis, l2Enabled, multiLevelCacheable);
      return unwrapGuard(loaded, returnType);
    } finally {
      lock.unlock();
      keyLocks.remove(cacheKey, lock);
    }
  }

  // ============================== 加载 & 回写 ==============================

  /**
   * 执行方法体并使用结果回写 L1 + L2。
   *
   * <p>返回 null 时写入空值占位（防穿透）；非 null 时写入真值。
   *
   * @param joinPoint 切点
   * @param cacheKey  缓存键
   * @param returnType 方法返回类型
   * @param redis     Redis 模板（可能 null）
   * @param l2Enabled 是否启用 L2
   * @param annotation 注解
   * @return 原始方法返回值（null 表示回源为空）
   */
  private Object loadFromSource(
      ProceedingJoinPoint joinPoint,
      String cacheKey,
      Class<?> returnType,
      StringRedisTemplate redis,
      boolean l2Enabled,
      MultiLevelCacheable annotation)
      throws Throwable {
    Object result = joinPoint.proceed();

    if (result == null) {
      // 空值占位（防穿透），使用缩减 TTL
      Cache<Object, Object> l1 = getOrCreateL1Cache(annotation, (MethodSignature) joinPoint.getSignature());
      l1.put(cacheKey, (Object) EMPTY_RESULT_MARK);
      if (l2Enabled && redis != null) {
        long nullMs = nullPlaceholderTtlMs(annotation.remoteTTL());
        redis.opsForValue().set(cacheKey, EMPTY_RESULT_MARK, Duration.ofMillis(nullMs));
      }
      return null;
    }

    // 真值：写入 L1
    Cache<Object, Object> l1 = getOrCreateL1Cache(annotation, (MethodSignature) joinPoint.getSignature());
    l1.put(cacheKey, result);

    // 真值：写入 L2
    if (l2Enabled && redis != null) {
      writeToL2(redis, cacheKey, result, annotation.remoteTTL());
    }
    return result;
  }

  // ============================== L1 缓存实例管理 ==============================

  /**
   * 根据注解配置获取或创建 L1 缓存。
   *
   * @param annotation 注解（提供 localTTL / localMaxSize 配置）
   * @param signature  方法签名（用于命名）
   * @return L1 缓存实例
   */
  private Cache<Object, Object> getOrCreateL1Cache(MultiLevelCacheable annotation, MethodSignature signature) {
    CacheIdentity identity = new CacheIdentity(signature.getMethod(), annotation.localTTL(), annotation.localMaxSize(), annotation.remoteTTL());
    return l1CacheRegistry.computeIfAbsent(
        identity,
        id -> {
          String cacheName =
              "mlevel:" + id.method.getDeclaringClass().getSimpleName() + "." + id.method.getName();
          return YdszCache.<Object, Object>newBuilder()
              .name(cacheName)
              .maximumSize(id.localMaxSize)
              .expireAfterWrite(id.localTTL, TimeUnit.MILLISECONDS)
              .recordStats()
              .build();
        });
  }

  // ============================== L2 访问辅助方法 ==============================

  /**
   * 从 L2（Redis）读取并反序列化。
   *
   * @param redis      Redis 模板
   * @param cacheKey   缓存键
   * @param targetType 方法返回类型
   * @return 命中值或 null
   */
  private Object lookupL2(StringRedisTemplate redis, String cacheKey, Class<?> targetType) {
    try {
      String raw = redis.opsForValue().get(cacheKey);
      if (raw == null) {
        return null;
      }
      if (EMPTY_RESULT_MARK.equals(raw)) {
        return null;
      }
      return l2ValueSerializer.deserialize(raw, targetType);
    } catch (Exception e) {
      LOG.warn("L2 读取失败, key={}, error={}", cacheKey, e.getMessage());
      return null;
    }
  }

  /**
   * 将值写入 L2。
   *
   * @param redis   Redis 模板
   * @param cacheKey 缓存键
   * @param value    缓存值
   * @param ttlMs    TTL（毫秒）
   */
  private void writeToL2(StringRedisTemplate redis, String cacheKey, Object value, long ttlMs) {
    try {
      String serialized = l2ValueSerializer.serialize(value);
      if (serialized == null) {
        // 序列化器返回 null 表示「不写入 L2」
        return;
      }
      redis.opsForValue().set(cacheKey, serialized, Duration.ofMillis(ttlMs));
    } catch (Exception e) {
      LOG.warn("L2 写入失败, key={}, error={}", cacheKey, e.getMessage());
    }
  }

  /**
   * 判断 L2 当前 key 是否存储了空值占位标记。
   *
   * @param redis   Redis 模板
   * @param cacheKey 缓存键
   * @return true 表示存在空值标记
   */
  private boolean isL2NullMarker(StringRedisTemplate redis, String cacheKey) {
    try {
      String raw = redis.opsForValue().get(cacheKey);
      return EMPTY_RESULT_MARK.equals(raw);
    } catch (Exception e) {
      return false;
    }
  }

  // ============================== 辅助方法 ==============================

  /**
   * 空值占位 TTL（毫秒）：取正常远程 TTL 的 {@value #NULL_TTL_RATIO} 分之一，至少 1 秒。
   *
   * @param normalTtlMs 正常 TTL（毫秒）
   * @return 空值占位 TTL（毫秒），至少 1000
   */
  private long nullPlaceholderTtlMs(long normalTtlMs) {
    return Math.max(1_000L, normalTtlMs / NULL_TTL_RATIO);
  }

  /**
   * 将 null 统一转换为与返回类型相符的"空"值。
   *
   * <p>基本类型返回默认值（如 int → 0），引用类型返回 null。
   *
   * @param returnType 方法返回类型
   * @return 对应空值
   */
  private static Object nullGuard(Class<?> returnType) {
    if (returnType.isPrimitive()) {
      if (returnType == int.class || returnType == long.class || returnType == short.class
          || returnType == byte.class || returnType == char.class) {
        return 0;
      }
      if (returnType == double.class || returnType == float.class) {
        return 0.0;
      }
      if (returnType == boolean.class) {
        return false;
      }
    }
    return null;
  }

  /**
   * 解包内部空值占位标记。
   *
   * @param loaded    加载结果（可能为 EMPTY_RESULT_MARK）
   * @param returnType 返回类型
   * @return 对外暴露的值
   */
  private static Object unwrapGuard(Object loaded, Class<?> returnType) {
    if (EMPTY_RESULT_MARK.equals(loaded)) {
      return nullGuard(returnType);
    }
    return loaded;
  }

  // ============================== 内部值对象 ==============================

  /**
   * L1 缓存身份标识（基于方法 + 注解配置参数），用作 {@link #l1CacheRegistry} 的 key。
   *
   * <p>注意：方法 + localTTL + localMaxSize + remoteTTL 的组合唯一决定一个 L1 实例。
   */
  private static final class CacheIdentity {
    private final Method method;
    private final long localTTL;
    private final long localMaxSize;
    private final long remoteTTL;

    CacheIdentity(Method method, long localTTL, long localMaxSize, long remoteTTL) {
      this.method = method;
      this.localTTL = localTTL;
      this.localMaxSize = localMaxSize;
      this.remoteTTL = remoteTTL;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CacheIdentity that)) return false;
      return localTTL == that.localTTL
          && localMaxSize == that.localMaxSize
          && remoteTTL == that.remoteTTL
          && Objects.equals(method, that.method);
    }

    @Override
    public int hashCode() {
      return Objects.hash(method, localTTL, localMaxSize, remoteTTL);
    }
  }
}
