package com.njydsz.cronjob.server.core.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.core.dispatch.JobScanner;

/**
 * 任务派发扫描任务（P2-O3：统一扫描器）。
 *
 * <p>委托 {@link JobScanner} 执行实际的扫描+派发逻辑。扫描间隔由配置
 * {@code ydsz.cronjob.scanner.interval-ms} 控制（默认 5s）。
 *
 * <p>此实现仅为适配层，保持 {@link JobScanner} 的原有行为不变。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class JobDispatchScanTask implements ScanTask {

  private final JobScanner jobScanner;

  @Override
  public String name() {
    return "job-dispatch";
  }

  @Override
  public void scan() {
    jobScanner.scan();
  }

  @Override
  public long intervalMs() {
    // 使用 JobScanner 的固定间隔（5s）
    return DEFAULT_SCAN_INTERVAL_MS;
  }

  @Override
  public String lockKey() {
    return "cronjob:scan:job-dispatch";
  }
}
