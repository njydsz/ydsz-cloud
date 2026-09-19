package com.njydsz.common.cache.spring;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Spring Cache 访问辅助类。
 *
 * <p>封装对 Spring {@link Cache} 的直接引用，供 {@link YdszCacheableAspect} 使用。设计意图：YDSZ
 * Cache 接口与 Spring Cache 接口同名（{@code Cache}），Java 不允许在同一源文件的 import 区域引入
 * 两个同名类，因此将 Spring Cache 访问逻辑隔离到独立源文件，使两个 Cache 接口都能通过简单名使用。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
final class SpringCacheHelper {

  private SpringCacheHelper() {}

  /**
   * 从缓存管理器查找值。
   *
   * @param manager 缓存管理器
   * @param cacheName 缓存名称
   * @param key 缓存 key
   * @return 缓存值（不存在返回 null）
   */
  static Object lookupCacheValue(CacheManager manager, String cacheName, String key) {
    try {
      Cache cache = manager.getCache(cacheName);
      if (cache == null) {
        return null;
      }
      Object nativeValue = cache.get(key);
      return unwrapIfValueWrapper(nativeValue);
    } catch (Exception e) {
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
  static void putCacheValue(CacheManager manager, String cacheName, String key, Object value) {
    try {
      Cache cache = manager.getCache(cacheName);
      if (cache != null) {
        cache.put(key, value);
      }
    } catch (Exception e) {
      // 写入异常由调用方记录
    }
  }

  /**
   * 获取底层原生缓存对象。
   *
   * @param manager 缓存管理器
   * @param cacheName 缓存名称
   * @return 原生缓存对象（可能为 null）
   */
  static Object getNativeCache(CacheManager manager, String cacheName) {
    try {
      Cache cache = manager.getCache(cacheName);
      return cache != null ? cache.getNativeCache() : null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 解包 Spring Cache.ValueWrapper。
   *
   * @param nativeValue 原生缓存值
   * @return 解包后的值
   */
  private static Object unwrapIfValueWrapper(Object nativeValue) {
    if (nativeValue == null) {
      return null;
    }
    if (nativeValue instanceof Cache.ValueWrapper wrapper) {
      return wrapper.get();
    }
    return nativeValue;
  }
}
