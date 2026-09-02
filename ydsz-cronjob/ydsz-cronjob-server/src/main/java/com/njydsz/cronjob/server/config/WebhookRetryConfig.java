package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * P1-3: Webhook 重试补偿配置。
 *
 * <p>控制 Webhook 失败后的补偿表扫描行为，包括扫描间隔、单次批量处理数量、最大重试次数。
 *
 * <h3>配置示例</h3>
 *
 * <pre>{@code
 * ydsz:
 *   cronjob:
 *     webhook-retry:
 *       scan-interval-ms: 30000       # 每 30s 扫描一次补偿表
 *       batch-size: 50                # 每次最多处理 50 条
 *       max-retry-count: 5            # 最多重试 5 次
 *       dead-retention-days: 30       # 死信保留天数
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.4
 */
@Data
public class WebhookRetryConfig {

  /** 默认扫描间隔：30 秒 */
  private static final long DEFAULT_SCAN_INTERVAL_MS = 30_000L;

  /** 默认单次批量处理数量：50 条 */
  private static final int DEFAULT_BATCH_SIZE = 50;

  /** 默认最大重试次数：5 次 */
  private static final int DEFAULT_MAX_RETRY_COUNT = 5;

  /** 默认死信保留天数：30 天 */
  private static final int DEFAULT_DEAD_RETENTION_DAYS = 30;

  /** 补偿表扫描间隔（毫秒），默认 30s */
  private long scanIntervalMs = DEFAULT_SCAN_INTERVAL_MS;

  /** 单次批量处理数量，默认 50 条 */
  private int batchSize = DEFAULT_BATCH_SIZE;

  /** 最大重试次数，默认 5 次 */
  private int maxRetryCount = DEFAULT_MAX_RETRY_COUNT;

  /** 死信保留天数，超过后可由定时清理任务删除 */
  private int deadRetentionDays = DEFAULT_DEAD_RETENTION_DAYS;
}
