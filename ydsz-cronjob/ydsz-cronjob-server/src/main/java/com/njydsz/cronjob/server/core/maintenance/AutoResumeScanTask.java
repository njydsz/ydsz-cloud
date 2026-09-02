package com.njydsz.cronjob.server.core.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.core.dispatch.AutoResumeScanner;

/**
 * 自动恢复扫描任务（P2-O3：统一扫描器）。
 *
 * <p>委托 {@link AutoResumeScanner} 扫描 AUTO_PAUSED 状态的任务并尝试自动恢复。
 * 扫描间隔由配置 {@code ydsz.cronjob.auto-resume.interval-ms} 控制（默认 60s）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AutoResumeScanTask implements ScanTask {

  private final AutoResumeScanner autoResumeScanner;

  @Override
  public String name() {
    return "auto-resume";
  }

  @Override
  public void scan() {
    autoResumeScanner.scan();
  }

  @Override
  public long intervalMs() {
    // 使用 AutoResumeScanner 的固定间隔（60s）
    return DEFAULT_SCAN_INTERVAL_MS;
  }

  @Override
  public String lockKey() {
    return "cronjob:auto-resume";
  }
}
