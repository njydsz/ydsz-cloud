package com.njydsz.agent.domain.config.properties;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆配置项。
 *
 * <p>YAML 前缀：{@code ydsz.agent.memory}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryProperties {

  /** Redis 过期默认时间（小时） */
  private static final int DEFAULT_TTL_HOURS = 24;

  /** 摘要压缩默认阈值（消息数） */
  private static final int DEFAULT_SUMMARY_THRESHOLD = 20;

  /** 摘要压缩默认保留的最近消息数 */
  private static final int DEFAULT_SUMMARY_KEEP_RECENT = 5;

  /** 画像中保留的 Top 领域数 */
  private static final int DEFAULT_PROFILE_TOP_DOMAINS = 5;

  /** 是否启用记忆 */
  private boolean isEnabled = true;

  /** Token 字符比例估算系数（中英混合） */
  private BigDecimal tokenCharRatio = new BigDecimal("2.5");

  /** 最大保留消息数 */
  private int maxMessages = 10;

  /** 记忆类型: in-memory / redis / database */
  private String type = "in-memory";

  /** Redis 过期时间（小时） */
  private int ttlHours = DEFAULT_TTL_HOURS;

  /** 是否启用摘要压缩 */
  private boolean isSummaryEnabled = false;

  /** 摘要压缩阈值（消息数达到该值时触发摘要） */
  private int summaryThreshold = DEFAULT_SUMMARY_THRESHOLD;

  /** 摘要压缩时保留的最近消息数 */
  private int summaryKeepRecent = DEFAULT_SUMMARY_KEEP_RECENT;

  /** 画像中保留的 Top 领域数 */
  private int profileTopDomains = DEFAULT_PROFILE_TOP_DOMAINS;

  /** 是否用 LLM 做画像分析（更准但贵） */
  private boolean isLlmAnalysisEnabled = false;
}
