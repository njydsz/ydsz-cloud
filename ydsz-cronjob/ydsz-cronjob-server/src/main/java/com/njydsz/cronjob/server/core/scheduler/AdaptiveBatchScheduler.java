package com.njydsz.cronjob.server.core.scheduler;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.metrics.CronjobMetrics;

/**
 * 批量调度指标采集器（P1-1）。
 *
 * <p>采集系统实时负载指标（CPU、内存、线程池活跃度）并发布到 Prometheus：
 *
 * <ul>
 *   <li>CPU 使用率 — 通过 {@link com.sun.management.OperatingSystemMXBean#getCpuLoad()}
 *   <li>堆内存使用率 — 通过 {@link MemoryMXBean#getHeapMemoryUsage()}
 *   <li>线程池活跃度 — 由 {@link
 *       com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher} 定期调用 {@link
 *       #updatePoolActive(int, int)} 更新
 * </ul>
 *
 * <p><b>简化说明</b>：原反向控制逻辑（根据负载动态调整 batchSize）已移除， batchSize 由配置固定值决定。
 * 本类仅负责指标采集与暴露，不参与调度控制决策。
 *
 * <p>仅在 {@code ydsz.cronjob.adaptive-batch.enabled=true} 时启用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(name = "cronjobMetrics")
@ConditionalOnProperty(name = "ydsz.cronjob.adaptive-batch.enabled", havingValue = "true")
public class AdaptiveBatchScheduler {

  private final CronjobProperties cronjobProperties;
  private final ObjectProvider<CronjobMetrics> cronjobMetricsProvider;

  /** 线程池活跃度（由 DefaultTaskDispatcher 更新，0-100） */
  private final AtomicInteger poolActivePct = new AtomicInteger(0);

  private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
  private final OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();

  /**
   * 初始化指标采集器：打印生效中的配置参数。
   *
   * <p>仅当 {@code ydsz.cronjob.adaptive-batch.enabled=true} 且有 {@code cronjobMetrics} Bean 时注册。
   * 无独立线程池或锁资源需预分配。
   */
  @PostConstruct
  public void init() {
    CronjobProperties.AdaptiveBatch config = cronjobProperties.getAdaptiveBatch();
    log.info(
        "[BatchMetrics] 初始化完成, evalInterval={}s cpuThreshold={} memThreshold={} poolThreshold={}",
        config.getEvalIntervalSeconds(),
        config.getCpuThreshold(),
        config.getMemThreshold(),
        config.getPoolActiveThreshold());
  }

  /**
   * 定时采集系统负载指标并发布到 Prometheus。
   *
   * <p>使用 Spring @Scheduled 注解，间隔由 {@code evalIntervalSeconds} 控制。
   * 仅采集与上报，不做调度控制决策。
   */
  @Scheduled(fixedDelayString = "#{${ydsz.cronjob.adaptive-batch.eval-interval-seconds:10} * 1000}")
  public void collectMetrics() {
    try {
      double cpuUsage = getCpuUsage();
      double memUsage = getMemUsage();
      double poolActive = poolActivePct.get();

      // 更新 Prometheus 指标
      CronjobMetrics metrics = cronjobMetricsProvider.getIfAvailable();
      if (metrics != null) {
        metrics.setSystemLoadScore(calculateLoadScore(cpuUsage, memUsage, poolActive));
      }

      log.debug(
          "[BatchMetrics] 指标采集: cpu={}%, mem={}%, pool={}%",
          String.format("%.1f", cpuUsage),
          String.format("%.1f", memUsage),
          String.format("%.1f", poolActive));
    } catch (Exception e) {
      log.warn("[BatchMetrics] 指标采集异常: {}", e.getMessage());
    }
  }

  /**
   * 更新线程池活跃度（由 DefaultTaskDispatcher 定期调用）。
   *
   * @param activeThreads 活跃线程数
   * @param maxThreads 最大线程数
   */
  public void updatePoolActive(int activeThreads, int maxThreads) {
    if (maxThreads <= 0) {
      return;
    }
    int pct = (int) Math.min(100.0, (double) activeThreads / maxThreads * 100);
    poolActivePct.set(pct);
  }

  /**
   * 获取 CPU 使用率（百分比，0-100）。
   *
   * <p>使用 {@link com.sun.management.OperatingSystemMXBean#getCpuLoad()}， 返回 -1 时回退为 0。
   */
  private double getCpuUsage() {
    try {
      if (osMXBean
          instanceof
          com.sun.management.OperatingSystemMXBean
                  sunOs) { // FQN-OK: name conflict with java.lang.management.OperatingSystemMXBean
        double load = sunOs.getCpuLoad();
        return load >= 0 ? load * 100 : 0;
      }
    } catch (Exception ignored) {
      // 降级处理
    }
    return 0;
  }

  /** 获取堆内存使用率（百分比，0-100）。 */
  private double getMemUsage() {
    try {
      long used = memoryMXBean.getHeapMemoryUsage().getUsed();
      long max = memoryMXBean.getHeapMemoryUsage().getMax();
      if (max <= 0) {
        return 0;
      }
      return (double) used / max * 100;
    } catch (Exception ignored) {
      return 0;
    }
  }

  /**
   * 计算综合负载评分（0-1），仅用于指标暴露。
   *
   * <p>当任一指标超过对应阈值时，该项权重放大；均未超过时，按基线权重计算。
   */
  private double calculateLoadScore(double cpuUsage, double memUsage, double poolActive) {
    CronjobProperties.AdaptiveBatch config = cronjobProperties.getAdaptiveBatch();
    double cpuScore = Math.min(1.0, cpuUsage / 100.0);
    double memScore = Math.min(1.0, memUsage / 100.0);
    double poolScore = Math.min(1.0, poolActive / 100.0);

    double cpuWeight = cpuUsage > config.getCpuThreshold() ? 0.5 : 0.4;
    double memWeight = memUsage > config.getMemThreshold() ? 0.4 : 0.3;
    double poolWeight = poolActive > config.getPoolActiveThreshold() ? 0.4 : 0.3;

    double totalWeight = cpuWeight + memWeight + poolWeight;
    return (cpuScore * cpuWeight + memScore * memWeight + poolScore * poolWeight) / totalWeight;
  }

  /**
   * 容器销毁钩子：仅打印关闭日志。
   *
   * <p>本类不持有线程池或外部连接，无需要主动释放的资源。
   */
  @PreDestroy
  public void shutdown() {
    log.info("[BatchMetrics] 关闭");
  }
}
