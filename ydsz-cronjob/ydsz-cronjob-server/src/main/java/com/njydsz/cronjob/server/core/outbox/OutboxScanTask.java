package com.njydsz.cronjob.server.core.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.core.maintenance.ScanTask;

/**
 * Outbox 事件发布扫描任务（P2-O3：统一扫描器 + P0-2：Outbox 模式）。
 *
 * <p>委托 {@link OutboxPublisher} 扫描并发布待处理的 Outbox 事件。
 * 扫描间隔固定 1s（高频），保证事件投递的实时性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class OutboxScanTask implements ScanTask {

  private final OutboxPublisher outboxPublisher;

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
    // 高频扫描：1s 间隔，保证事件投递实时性
    return 1000L;
  }

  @Override
  public String lockKey() {
    return "cronjob:scan:outbox-publish";
  }
}
