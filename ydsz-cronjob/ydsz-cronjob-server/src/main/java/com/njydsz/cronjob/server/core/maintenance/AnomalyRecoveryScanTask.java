package com.njydsz.cronjob.server.core.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.core.healing.AnomalyRecoveryScanner;

/**
 * 异常修复扫描任务（P2-O3：统一扫描器）。
 *
 * <p>委托 {@link AnomalyRecoveryScanner} 执行离线节点恢复、卡死任务修复、AUTO_PAUSED 恢复。
 * 扫描间隔由配置 {@code ydsz.cronjob.anomaly-recovery.scan-interval-ms} 控制（默认 30s）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AnomalyRecoveryScanTask implements ScanTask {

  private final AnomalyRecoveryScanner anomalyRecoveryScanner;

  @Override
  public String name() {
    return "anomaly-recovery";
  }

  @Override
  public void scan() {
    anomalyRecoveryScanner.scan();
  }

  @Override
  public long intervalMs() {
    // 使用 AnomalyRecoveryScanner 的固定间隔（30s）
    return DEFAULT_SCAN_INTERVAL_MS;
  }

  @Override
  public String lockKey() {
    return "cronjob:anomaly-recovery";
  }
}
