package com.njydsz.common.cache.support;

/**
 * 自定义过期策略接口
 *
 * <p>允许为每个缓存条目动态计算过期时间，而非使用全局固定的过期策略。 参考 Caffeine 的 Expiry 设计。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * Expiry<String, Data> expiry = (key, value, currentTimeNanos) -> {
 *     // 根据数据类型返回不同的过期时间
 *     return value.isHot() ? TimeUnit.MINUTES.toNanos(5) : TimeUnit.HOURS.toNanos(1);
 * };
 *
 * Cache<String, Data> cache = YdszCache.newBuilder()
 *     .expireAfter(expiry)
 *     .maximumSize(10000)
 *     .build();
 * }</pre>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 * @author ydsz-team
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface Expiry<K, V> {

  /**
   * 计算条目创建后的过期时间
   *
   * @param key 缓存键
   * @param value 缓存值
   * @param currentTimeNanos 当前时间（纳秒）
   * @return 过期时间（纳秒），从 currentTimeNanos 开始计算
   */
  long expireAfterCreate(K key, V value, long currentTimeNanos);

  /**
   * 计算条目更新后的过期时间
   *
   * <p>默认实现返回与创建时相同的过期时间。
   *
   * @param key 缓存键
   * @param value 缓存值
   * @param currentTimeNanos 当前时间（纳秒）
   * @return 过期时间（纳秒），从 currentTimeNanos 开始计算
   */
  default long expireAfterUpdate(K key, V value, long currentTimeNanos) {
    return expireAfterCreate(key, value, currentTimeNanos);
  }

  /**
   * 计算条目读取后的过期时间
   *
   * <p>默认实现返回 Long.MAX_VALUE，表示读取不会延长过期时间。
   *
   * @param key 缓存键
   * @param value 缓存值
   * @param currentTimeNanos 当前时间（纳秒）
   * @return 过期时间（纳秒），从 currentTimeNanos 开始计算；返回 Long.MAX_VALUE 表示不延长
   */
  default long expireAfterRead(K key, V value, long currentTimeNanos) {
    return Long.MAX_VALUE;
  }
}
