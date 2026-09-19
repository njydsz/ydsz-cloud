package com.njydsz.common.safe.cache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SafeCache 工厂辅助类
 *
 * <p>封装 ydzs-common-cache 可选依赖的加载逻辑：通过反射检查 {@code YdszCacheBridge} 是否可加载，
 * 可加载时创建基于 Caffeine 语义的 SafeCache，否则退化为 {@link ConcurrentTtlSafeCache}。
 *
 * <p>使用方无需在编译期引入 cache 模块，运行时自动识别 classpath 并选择实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class SafeCacheFactoryHelper {

  private static final Logger LOG = LoggerFactory.getLogger(SafeCacheFactoryHelper.class);

  private SafeCacheFactoryHelper() {
    throw new UnsupportedOperationException("工具类不可实例化");
  }

  /**
   * 创建 SafeCache 实例。
   *
   * <p>优先尝试使用 ydzs-common-cache 创建高性能缓存；如果 cache 模块不在 classpath 上，
   * 自动退化为 ConcurrentTtlSafeCache。
   *
   * @param expireAfterWrite 写入后过期时间
   * @param timeUnit 时间单位
   * @param maxSize 最大条目数（0 表示不限制）
   * @param <K> 键类型
   * @param <V> 值类型
   * @return SafeCache 实例
   */
  // 反射调用 YdszCacheBridge.create() 返回值类型为 SafeCache，泛型参数在反射中擦除；使用原始类型 Method 触发 unchecked 警告，此处忽略
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <K, V> SafeCache<K, V> createCache(
      long expireAfterWrite, TimeUnit timeUnit, long maxSize) {
    try {
      // 反射加载 YdszCacheBridge，如果 ydzs-common-cache 不在 classpath 则抛出 NoClassDefFoundError
      Class<?> bridgeClass = Class.forName("com.njydsz.common.safe.cache.YdszCacheBridge");
      Method createMethod =
          bridgeClass.getMethod("create", long.class, TimeUnit.class, long.class);
      SafeCache<K, V> cache =
          (SafeCache<K, V>) createMethod.invoke(null, expireAfterWrite, timeUnit, maxSize);
      LOG.info("SafeCache 已创建(YdszCacheBridge): ttl={}, unit={}, maxSize={}, clazz={}",
          expireAfterWrite, timeUnit.name(), maxSize > 0 ? maxSize : "unlimited",
          bridgeClass.getSimpleName());
      return cache;
    } catch (InvocationTargetException e) {
      // YdszCacheBridge.create 内部可能抛出 NoClassDefFoundError（ydsz-common-cache 不可用）
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof NoClassDefFoundError) {
        LOG.info("ydsz-common-cache 不可用，退化到 ConcurrentTtlSafeCache");
        return new ConcurrentTtlSafeCache<>(expireAfterWrite, timeUnit, maxSize);
      }
      LOG.warn("YdszCacheBridge 调用异常，退化到 ConcurrentTtlSafeCache: {}", cause.getMessage());
      return new ConcurrentTtlSafeCache<>(expireAfterWrite, timeUnit, maxSize);
    } catch (NoClassDefFoundError | ClassNotFoundException e) {
      // ydzs-common-cache 不在 classpath 上
      LOG.info("ydsz-common-cache 不在线，退化到 ConcurrentTtlSafeCache: {}", e.getMessage());
      return new ConcurrentTtlSafeCache<>(expireAfterWrite, timeUnit, maxSize);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      LOG.warn("SafeCache 工厂反射调用失败，退化到 ConcurrentTtlSafeCache: {}", e.getMessage());
      return new ConcurrentTtlSafeCache<>(expireAfterWrite, timeUnit, maxSize);
    }
  }
}
