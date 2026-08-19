package com.njydsz.agent.domain.gateway;

/**
 * LLM 缓存指标记录 SPI
 *
 * <p>由 infra 层 {@code CachedLlmClient} 依赖（domain 接口，符合 DDD 分层）， 具体指标采集实现由 server 层
 * {@code AgentMetrics}（或自定义实现）提供，避免 infra 层反向依赖 server 层。
 *
 * <p><b>线程安全</b>：实现类需保证并发计数安全（如基于 Micrometer Counter / AtomicLong）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CacheMetricsRecorder {

  /**
   * 记录一次缓存命中。
   *
   * @param provider Provider 名称
   */
  void recordCacheHit(String provider);

  /**
   * 记录一次缓存未命中。
   *
   * @param provider Provider 名称
   */
  void recordCacheMiss(String provider);
}
