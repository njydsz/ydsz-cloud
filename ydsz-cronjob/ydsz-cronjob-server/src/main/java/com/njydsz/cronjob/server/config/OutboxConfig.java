package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * Outbox 事务性事件发布配置（P0-2 优化：扫描间隔可配置化）。
 *
 * <p><b>迁移说明（26.09.29）：</b>自建 {@code OutboxEvent} / {@code OutboxScanTask} 体系收敛至
 * ydsz-common-event {@code OutboxProcessor} + {@code @EventListener} 订阅模式。
 * 本类配置项在过渡期保留（避免 yml 前缀绑定报错），新写入链路使用 common-event {@code EventProperties}。
 *
 * <p>对应配置前缀 {@code ydsz.cronjob.outbox.*}（过渡期兼容，新代码不再使用）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.29 迁移至 ydsz-common-event OutboxProcessor，标记过渡期兼容
 */
@Data
public class OutboxConfig {

  /** 默认扫描间隔（毫秒）：1s */
  private static final long DEFAULT_SCAN_INTERVAL_MS = 1000L;

  /** 默认批量处理事件数 */
  private static final int DEFAULT_BATCH_SIZE = 100;

  /** 扫描间隔（毫秒，默认 1s），可根据事件投递 SLA 调整 */
  private long scanIntervalMs = DEFAULT_SCAN_INTERVAL_MS;

  /** 每次扫描批量处理的事件数（默认 100） */
  private int batchSize = DEFAULT_BATCH_SIZE;
}
