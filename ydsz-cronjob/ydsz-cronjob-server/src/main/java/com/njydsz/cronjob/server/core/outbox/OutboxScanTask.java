package com.njydsz.cronjob.server.core.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.maintenance.ScanTask;

/**
 * Outbox 事件发布扫描任务（P2-O3：统一扫描器 + P0-2：Outbox 模式）。
 *
 * <p>委托 {@link OutboxPublisher} 扫描并发布待处理的 Outbox 事件。
 * 扫描间隔可通过 {@code ydsz.cronjob.outbox.scan-interval-ms} 配置（默认 1000ms），
 * 保证事件投递的实时性与系统负载之间的平衡。
 *
 * <h3>云顶编码规范 §24 配置管理规范</h3>
 *
 * <p>扫描间隔配置化，避免硬编码，支持不同 SLA 场景灵活调整。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OutboxScanTask implements ScanTask {

  /** 默认扫描间隔（毫秒）：1s */
  private static final long DEFAULT_SCAN_INTERVAL_MS = 1000L;

  private final OutboxPublisher outboxPublisher;
  private final CronjobProperties cronjobProperties;

  @Override
  public String name() {
    return "outbox-publish";
  }

  @Override
  public void scan() {
    outboxPublisher.publishPending();
  }

  @Override
  public long intervalMs() {
    // 扫描间隔可配置，默认 1s，保证事件投递实时性
    long configuredInterval = cronjobProperties.getOutbox().getScanIntervalMs();
    return configuredInterval > 0 ? configuredInterval : DEFAULT_SCAN_INTERVAL_MS;
  }

  @Override
  public String lockKey() {
    return "cronjob:scan:outbox-publish";
  }
}
