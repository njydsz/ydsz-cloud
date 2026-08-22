package com.njydsz.common.cache.builder;

/**
 * 缓存类型枚举
 *
 * <p>当前支持的缓存类型（精简版 1.0.0）：
 *
 * <ul>
 *   <li>TINYLFU：Window-TinyLFU 算法（参考 Caffeine），命中率最优，通用场景默认
 *   <li>STRIPED：高性能分段锁并发缓存，高并发写入场景首选
 * </ul>
 *
 * <p>历史类型（LRU / WEIGHTED / CONCURRENT / ENHANCED_LOADING / ASYNC）已统一收敛到 TINYLFU 或
 * STRIPED。业务接入方无需指定类型时，使用默认值 TINYLFU 即可满足绝大多数场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum CacheType {

  /**
   * Window-TinyLFU 算法（参考 Caffeine）
   *
   * <p>命中率最优，适用于绝大多数场景，为默认类型。
   */
  TINYLFU,

  /**
   * 高性能分段锁并发缓存
   *
   * <p>使用 StripedLock 实现高并发写入，适用于写多读少的高并发场景。
   */
  STRIPED
}
