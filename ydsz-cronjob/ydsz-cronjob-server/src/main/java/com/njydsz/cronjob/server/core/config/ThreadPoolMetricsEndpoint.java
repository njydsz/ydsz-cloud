package com.njydsz.cronjob.server.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

/**
 * Spring Boot Actuator 自定义端点 — 线程池运行时指标查询。
 *
 * <p>端点 ID：{@code threadpools}
 *
 * <p>支持的端点操作：
 *
 * <ul>
 *   <li>{@code GET /actuator/threadpools} — 查询所有已注册线程池的综合指标
 *   <li>{@code GET /actuator/threadpools/{poolName}} — 查询指定线程池详细指标
 * </ul>
 *
 * <p>使用前提：Spring Boot Actuator 在 classpath 中，且 {@link CronjobThreadPoolRegistry} 已注册。
 *
 * <p>配置示例：
 *
 * <pre>{@code
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,threadpools
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Endpoint(id = "threadpools")
public class ThreadPoolMetricsEndpoint {

  private final CronjobThreadPoolRegistry registry;

  public ThreadPoolMetricsEndpoint(CronjobThreadPoolRegistry registry) {
    this.registry = registry;
  }

  /**
   * 读取所有已注册线程池的综合指标。
   *
   * @return 所有线程池指标 + 汇总信息
   */
  @ReadOperation
  public Map<String, Object> allPoolMetrics() {
    List<CronjobThreadPoolRegistry.ThreadPoolMetrics> metricsList = registry.getMetrics();
    Map<String, Object> result = new LinkedHashMap<>(16);

    int totalPools = metricsList.size();
    int totalActive = 0;
    int totalQueueSize = 0;
    long totalCompleted = 0;

    Map<String, Object> pools = new LinkedHashMap<>(16);
    for (CronjobThreadPoolRegistry.ThreadPoolMetrics m : metricsList) {
      Map<String, Object> poolDetail = new LinkedHashMap<>(16);
      poolDetail.put("corePoolSize", m.corePoolSize());
      poolDetail.put("maximumPoolSize", m.maximumPoolSize());
      poolDetail.put("activeCount", m.activeCount());
      poolDetail.put("queueSize", m.queueSize());
      poolDetail.put("completedTaskCount", m.completedTaskCount());
      poolDetail.put("largestPoolSize", m.largestPoolSize());
      poolDetail.put(
          "utilization",
          m.maximumPoolSize() > 0
              ? String.format("%.1f%%", (double) m.activeCount() / m.maximumPoolSize() * 100)
              : "N/A");
      pools.put(m.name(), poolDetail);

      totalActive += m.activeCount();
      totalQueueSize += m.queueSize();
      totalCompleted += m.completedTaskCount();
    }

    result.put(
        "summary",
        Map.of(
            "totalPools", totalPools,
            "totalActiveThreads", totalActive,
            "totalQueueSize", totalQueueSize,
            "totalCompletedTasks", totalCompleted));
    result.put("pools", pools);
    return result;
  }

  /**
   * 读取指定线程池的详细指标。
   *
   * @param poolName 线程池名称
   * @return 线程池详细指标
   */
  @ReadOperation
  public Map<String, Object> poolMetrics(@Selector String poolName) {
    CronjobThreadPoolRegistry pool = registry;
    if (pool == null) {
      return Map.of("error", "ThreadPoolRegistry not available");
    }
    List<CronjobThreadPoolRegistry.ThreadPoolMetrics> allMetrics = pool.getMetrics();
    return allMetrics.stream()
        .filter(m -> m.name().equals(poolName))
        .findFirst()
        .map(
            m -> {
              Map<String, Object> detail = new LinkedHashMap<>(16);
              detail.put("name", m.name());
              detail.put("corePoolSize", m.corePoolSize());
              detail.put("maximumPoolSize", m.maximumPoolSize());
              detail.put("activeCount", m.activeCount());
              detail.put("queueSize", m.queueSize());
              detail.put("completedTaskCount", m.completedTaskCount());
              detail.put("largestPoolSize", m.largestPoolSize());
              detail.put(
                  "utilization",
                  m.maximumPoolSize() > 0
                      ? String.format(
                          "%.1f%%", (double) m.activeCount() / m.maximumPoolSize() * 100)
                      : "N/A");
              return detail;
            })
        .orElse(Map.of("error", "Pool not found: " + poolName));
  }
}
