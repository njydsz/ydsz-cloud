package com.njydsz.cronjob.server.core.metrics;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.server.core.executor.RunningTaskCounter;

/**
 * 系统指标采集器（本机 CPU / 内存 / 运行任务数）。
 *
 * <p>供 {@code JobNodeHeartbeat} 心跳上报使用：每个执行器节点周期性采集本机资源指标，
 * 写入 {@code ydsz_job_node} 表，供 Leader 侧节点选择策略（LeastLoadNodeSelector / WorkerNodeSelector）消费。
 *
 * <h3>采集来源</h3>
 *
 * <ul>
 *   <li>CPU：JMX {@link com.sun.management.OperatingSystemMXBean#getCpuLoad()}（JVM 进程视角）
 *   <li>内存：JMX 物理内存总量与可用量换算使用率
 *   <li>运行任务数：复用 {@link RunningTaskCounter}（Redis 集群级计数）
 * </ul>
 *
 * <p>采集失败时返回安全默认值（CPU/内存 0，任务数 0），不影响心跳主流程。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SystemMetricsCollector {

  /** 指标失败时的默认值（0 表示未知/无负载） */
  private static final BigDecimal DEFAULT_METRIC_VALUE = BigDecimal.ZERO;

  /** 百分比换算系数 */
  private static final int PERCENT_SCALE = 100;

  /** 内存使用率小数精度（保留 4 位） */
  private static final int MEMORY_USAGE_SCALE = 4;

  private final RunningTaskCounter runningTaskCounter;

  /**
   * 构造系统指标采集器。
   *
   * @param runningTaskCounter 运行中任务计数器（Redis 集群级）
   */
  public SystemMetricsCollector(RunningTaskCounter runningTaskCounter) {
    this.runningTaskCounter = runningTaskCounter;
  }

  /**
   * 采集本机 CPU 使用率。
   *
   * @return CPU 使用率（0.0 ~ 1.0）；采集失败返回 0
   */
  public BigDecimal collectCpuUsage() {
    try {
      OperatingSystemMXBean osBean =
          (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
      double load = osBean.getCpuLoad();
      return load >= 0 ? BigDecimal.valueOf(load) : DEFAULT_METRIC_VALUE;
    } catch (Exception e) {
      log.debug("[SysMetrics] CPU 采集失败, 返回默认值: reason={}", e.getMessage());
      return DEFAULT_METRIC_VALUE;
    }
  }

  /**
   * 采集本机内存使用率（百分比）。
   *
   * @return 内存使用率（0.0 ~ 1.0）；采集失败返回 0
   */
  public BigDecimal collectMemUsagePct() {
    try {
      OperatingSystemMXBean osBean =
          (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
      long total = osBean.getTotalPhysicalMemorySize();
      long free = osBean.getFreePhysicalMemorySize();
      if (total <= 0 || free < 0) {
        return DEFAULT_METRIC_VALUE;
      }
      BigDecimal usage =
          BigDecimal.ONE.subtract(
              BigDecimal.valueOf(free)
                  .divide(BigDecimal.valueOf(total), MEMORY_USAGE_SCALE, RoundingMode.HALF_UP));
      return usage
          .multiply(BigDecimal.valueOf(PERCENT_SCALE))
          .min(BigDecimal.valueOf(PERCENT_SCALE));
    } catch (Exception e) {
      log.debug("[SysMetrics] 内存采集失败, 返回默认值: reason={}", e.getMessage());
      return DEFAULT_METRIC_VALUE;
    }
  }

  /**
   * 采集集群级运行中任务数。
   *
   * @return 运行中任务数（Redis 计数，异常时返回 0）
   */
  public int collectRunningCount() {
    try {
      long count = runningTaskCounter.getCount();
      return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    } catch (Exception e) {
      log.debug("[SysMetrics] 运行任务数采集失败, 返回 0: reason={}", e.getMessage());
      return 0;
    }
  }
}
