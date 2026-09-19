package com.njydsz.common.locales.util;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * i18n 简易 LRU 缺失 key 负缓存
 *
 * <p>缓存「key + Locale → 消息不存在」的结果，避免开发环境（{@code devCacheSeconds=0}）下重复遍历所有 basename
 * Properties 文件导致的性能损耗。负缓存的大小由构造参数 {@code capacity} 限定，超限时通过 LRU 驱逐最久未使用的条目。
 *
 * <p><b>线程安全：</b>通过 {@link ReadWriteLock} 保护读写操作，读操作（{@link #isMissing}）并发互不阻塞，写操作（{@link
 * #markMissing} / {@link #clear}）独占锁。高频读取场景下读锁吞吐足够。
 *
 * <p><b>使用约束：</b>仅由 {@link MessageSourceHolder#resolve(String, Object[], Locale)} 在内部调用，输入 key 已校验非
 * null。返回值 {@code true} 表示该 key 在当前 Locale 下已知不存在，可直接走快速失败路径。
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see MessageSourceHolder
 */
final class I18nNegativeCache {

  /** 默认缓存容量：覆盖平均每模块 200 个 key miss 的典型场景 */
  static final int DEFAULT_CAPACITY = 500;

  private final int capacity;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final LinkedHashMap<String, Boolean> cache;

  /**
   * 构造指定容量的负缓存。
   *
   * @param capacity 最大条目数（建议 200-1000，取决于业务模块数量）
   */
  I18nNegativeCache(int capacity) {
    this.capacity = capacity;
    this.cache =
        new LinkedHashMap<String, Boolean>(capacity, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > I18nNegativeCache.this.capacity;
          }
        };
  }

  /**
   * 快速判断当前 key + Locale 组合是否已知缺失。
   *
   * <p>命中时返回 {@code true}，调用方可直接返回 key 本身，无需走底层 MessageSource 多 basename 扫表路径。
   *
   * @param key i18n 消息键（非 null）
   * @param locale 区域设置（非 null）
   * @return true = 该 key+Locale 已知不存在
   */
  boolean isMissing(String key, Locale locale) {
    String cacheKey = buildCacheKey(key, locale);
    lock.readLock().lock();
    try {
      return Boolean.TRUE.equals(cache.get(cacheKey));
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 标记当前 key + Locale 组合为「已确认缺失」。
   *
   * <p>在底层 MessageSource 返回 key 本身（表示 useCodeAsDefaultMessage=true 兜底）时调用。负缓存生效后，下次相同+
   * Locale 查询将走快速路径。
   *
   * @param key i18n 消息键（非 null）
   * @param locale 区域设置（非 null）
   */
  void markMissing(String key, Locale locale) {
    String cacheKey = buildCacheKey(key, locale);
    lock.writeLock().lock();
    try {
      cache.put(cacheKey, Boolean.TRUE);
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 清空缓存（用于测试或开发热加载后刷新）。
   */
  void clear() {
    lock.writeLock().lock();
    try {
      cache.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 获取当前缓存条目数（仅用于监控/测试）。
   *
   * @return 当前缓存中的条目数
   */
  int size() {
    lock.readLock().lock();
    try {
      return cache.size();
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 构造缓存 key：key + "|" + locale.toString()
   *
   * <p>Locale.toString() 格式如 "zh_CN"、"en_US"，与 {@link Locale#equals} 语义匹配，故可作为缓存 key 的组成部分。
   */
  private String buildCacheKey(String key, Locale locale) {
    return key + "|" + locale.toString();
  }
}
