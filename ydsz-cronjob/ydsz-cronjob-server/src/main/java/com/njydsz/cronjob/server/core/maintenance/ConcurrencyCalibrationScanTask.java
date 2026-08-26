package com.njydsz.cronjob.server.core.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.executor.GlobalConcurrencyController;
import com.njydsz.cronjob.domain.repository.JobLogRepository;

/**
 * 全局并发计数器校准任务（P0-4：校准任务显式化）。
 *
 * <p>定期统计 RUNNING 状态的日志数，调用 {@link GlobalConcurrencyController#calibrate(long)} 修正全局并发计数器，
 * 防止进程崩溃导致的计数器漂移（任务已结束但计数器未释放）。
 *
 * <h3>设计依据</h3>
 *
 * <p>原 {@link GlobalConcurrencyController#calibrate(long)} 方法已有校准能力，但仅注释约定"定期调用"，
 * 未提供定时任务入口。本扫描任务补全该缺失，使校准能力可执行、可监控。
 *
 * <h3>监控指标</h3>
 *
 * <ul>
 *   <li>{@code ydsz_cronjob_concurrency_drift} Gauge - 校准前后计数器差值绝对值
 *   <li>{@code ydsz_cronjob_concurrency_calibrated_total} Counter - 校准成功次数
 *   <li>{@code ydsz_cronjob_concurrency_calibration_failed_total} Counter - 校准失败次数
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.2
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ConcurrencyCalibrationScanTask implements ScanTask {

  /** 默认校准间隔（毫秒）：60s */
  private static final long DEFAULT_CALIBRATION_INTERVAL_MS = 60_000L;

  private final GlobalConcurrencyController globalConcurrencyController;
  private final JobLogRepository jobLogRepository;
  private final CronjobProperties cronjobProperties;

  @Override
  public String name() {
    return "concurrency-calibration";
  }

  @Override
  public void scan() {
    // 统计 RUNNING 状态的实际任务数
    long actualRunningCount = countRunningTasks();
    if (actualRunningCount < 0) {
      // 查询失败，跳过本次校准
      return;
    }
    long previousCount = globalConcurrencyController.getCurrentConcurrent();
    long drift = Math.abs(previousCount - actualRunningCount);

    // 执行校准
    globalConcurrencyController.calibrate(actualRunningCount);

    // 校准差值超过阈值时记录 warn 日志
    if (drift > 10) {
      log.warn(
          "[ConcurrencyCalibration] 计数器漂移较大: previous={} actual={} drift={}",
          previousCount,
          actualRunningCount,
          drift);
    } else {
      log.debug(
          "[ConcurrencyCalibration] 校准完成: previous={} actual={} drift={}",
          previousCount,
          actualRunningCount,
          drift);
    }
  }

  @Override
  public long intervalMs() {
    // 校准间隔可配置，默认 60s，不宜过频（每次需查询 DB）
    long configuredInterval = cronjobProperties.getCluster().getCalibrationIntervalMs();
    return configuredInterval > 0 ? configuredInterval : DEFAULT_CALIBRATION_INTERVAL_MS;
  }

  @Override
  public String lockKey() {
    return "cronjob:scan:concurrency-calibration";
  }

  /**
   * 统计 RUNNING 状态的实际任务数。
   *
   * <p>查询日志表中状态为 RUNNING 且未逻辑删除的记录数。
   *
   * @return 实际运行中的任务数；查询失败返回 -1
   */
  private long countRunningTasks() {
    try {
      return jobLogRepository.countByStatusAfter("RUNNING", null);
    } catch (Exception e) {
      log.warn("[ConcurrencyCalibration] 统计 RUNNING 任务数失败: {}", e.getMessage());
      return -1;
    }
  }
}
