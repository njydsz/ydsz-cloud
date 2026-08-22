package com.njydsz.cronjob.server.core.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.core.alert.AlertScanner;

/**
 * 告警扫描任务（P2-O3：统一扫描器）。
 *
 * <p>委托 {@link AlertScanner} 扫描 FAIL_RATE / DURATION_P95 类型告警规则。
 * 扫描间隔由配置 {@code ydsz.cronjob.alert.scan-interval-ms} 控制（默认 5min）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AlertScanTask implements ScanTask {

  private final AlertScanner alertScanner;

  @Override
  public String name() {
    return "alert-scan";
  }

  @Override
  public void scan() {
    alertScanner.scan();
  }

  @Override
  public long intervalMs() {
    // 使用 AlertScanner 的固定间隔（5min）
    return 300000L;
  }

  @Override
  public String lockKey() {
    return "cronjob:alert-scan";
  }
}
