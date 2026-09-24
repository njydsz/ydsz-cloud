package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多级缓存配置属性。
 *
 * <p>绑定配置前缀 {@code ydsz.agent.cache}，控制 LLM 对话响应语义缓存的启用、TTL、
 * 相似度阈值、L1/L2 分层策略与最大条目数。默认不开启缓存（isEnabled=false），
 * L1 使用 Caffeine 本地缓存（容量 200，5 分钟过期），L2 使用 Redis（60 分钟 TTL）。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CacheProperties {
  private static final int DEFAULT_TTL_MINUTES = 60;
  private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;
  private static final int DEFAULT_L1_MAX_SIZE = 200;
  private static final int DEFAULT_L1_EXPIRE_MINUTES = 5;

  private boolean isEnabled = false;
  private int ttlMinutes = DEFAULT_TTL_MINUTES;
  private int maxSize = 1000;
  private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
  private String type = "caffeine";
  private int l1MaxSize = DEFAULT_L1_MAX_SIZE;
  private int l1ExpireMinutes = DEFAULT_L1_EXPIRE_MINUTES;
}
