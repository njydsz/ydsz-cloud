package com.njydsz.cronjob.server.config;

import lombok.Data;

/**
 * Outbox 事务性事件发布配置（P0-2 优化：扫描间隔可配置化）。
 *
 * <p>控制 OutboxScanTask 的扫描行为，保证事件投递的实时性与系统负载之间的平衡。
 *
 * <p>对应配置前缀 {@code ydsz.cronjob.outbox.*}。
 *
 * <h3>配置项说明</h3>
 *
 * <ul>
 *   <li>{@link #scanIntervalMs} 扫描间隔（毫秒），默认 1000ms，可根据事件投递 SLA 调整
 *   <li>{@link #batchSize} 每次扫描批量处理的事件数，默认 100
 * </ul>
 *
 * <p>依据《云顶编码规范》§24 配置管理规范：所有可调整参数必须通过配置项暴露，禁止硬编码。
 *
 * @author ydsz-team
 * @since 26.09.01
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
