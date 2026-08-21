package com.njydsz.common.cache.api;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 缓存策略查询接口 — 运行时策略检查与调整
 *
 * <p>参考 Caffeine 的 cache.policy() 设计，允许在运行时查询和调整缓存策略。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * Cache<String, User> cache = YdszCache.newBuilder()
 *     .maximumSize(1000)
 *     .build();
 *
 * // 查询淘汰策略
 * cache.policy().eviction().ifPresent(eviction -> {
 *     System.out.println("最大容量: " + eviction.getMaximum());
 *     eviction.setMaximum(2000); // 动态调整
 * });
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CachePolicy {

  /**
   * 获取淘汰策略信息
   *
   * @return 淘汰策略（如果缓存支持淘汰）
   */
  Optional<EvictionPolicy> eviction();

  /**
   * 获取过期策略信息
   *
   * @return 过期策略（如果缓存支持过期）
   */
  Optional<ExpirationPolicy> expiration();

  /** 淘汰策略接口 */
  interface EvictionPolicy {

    /**
     * 获取最大容量
     *
     * @return 返回值说明
     */
    OptionalLong getMaximum();

    /**
     * 设置最大容量
     *
     * @param maximumSize maximumSize 参数
     */
    void setMaximum(long maximumSize);

    /**
     * 获取当前加权大小（如果使用权重）
     *
     * @return 返回值说明
     */
    OptionalLong weightedSize();

    /**
     * 是否使用权重
     *
     * @return 返回值说明
     */
    boolean isWeighted();
  }

  /** 过期策略接口 */
  interface ExpirationPolicy {

    /**
     * 获取写入后过期时间（纳秒），0 表示不过期
     *
     * @return 返回值说明
     */
    long getExpiresAfterWriteNanos();

    /**
     * 设置写入后过期时间
     *
     * @param expireAfterWriteNanos expireAfterWriteNanos 参数
     */
    void setExpiresAfterWriteNanos(long expireAfterWriteNanos);

    /**
     * 获取访问后过期时间（纳秒），0 表示不过期
     *
     * @return 返回值说明
     */
    long getExpiresAfterAccessNanos();

    /**
     * 设置访问后过期时间
     *
     * @param expireAfterAccessNanos expireAfterAccessNanos 参数
     */
    void setExpiresAfterAccessNanos(long expireAfterAccessNanos);

    /**
     * 是否使用自定义过期策略
     *
     * @return 返回值说明
     */
    boolean isCustomExpiry();
  }
}
