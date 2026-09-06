package com.njydsz.common.thread.actuator;

import java.util.List;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.njydsz.common.thread.registry.ThreadPoolRegistry;

/**
 * Spring Boot Actuator 端点：{@code /actuator/threadpools}。
 *
 * <p>暴露 {@link ThreadPoolRegistry} 中所有已注册线程池的实时指标，
 * 便于运维人员通过 HTTP 接口快速查看线程池运行状态。
 *
 * <p>使用方式：
 *
 * <ul>
 *   <li>{@code GET /actuator/threadpools} - 返回所有线程池的指标快照列表
 *   <li>{@code GET /actuator/threadpools/{poolName}} - 返回指定线程池的指标快照
 * </ul>
 *
 * <p>返回的 JSON 示例：
 *
 * <pre>{@code
 * {
 *   "threadPools": [
 *     {
 *       "poolName": "flowQueue",
 *       "corePoolSize": 2,
 *       "maximumPoolSize": 8,
 *       "activeCount": 0,
 *       "poolSize": 2,
 *       "queueSize": 0,
 *       "queueCapacity": 256,
 *       "completedTaskCount": 1523,
 *       "largestPoolSize": 3,
 *       "taskCount": 1523
 *     }
 *   ],
 *   "totalCount": 1
 * }
 * }</pre>
 *
 * <p>启用条件：classpath 上存在 Spring Boot Actuator（{@code spring-boot-actuator} 已声明为 optional 依赖）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Endpoint(id = "threadpools")
public class ThreadPoolMetricsEndpoint {

  /**
   * 读取所有线程池的指标快照。
   *
   * @return 包含所有线程池指标快照的响应
   */
  @ReadOperation
  public ThreadPoolMetricsResponse listThreadPools() {
    List<ThreadPoolRegistry.ThreadPoolMetricsSnapshot> snapshots =
        ThreadPoolRegistry.snapshotMetrics();
    return new ThreadPoolMetricsResponse(snapshots, snapshots.size());
  }

  /**
   * 读取指定线程池的指标快照。
   *
   * @param poolName 线程池名称
   * @return 指定线程池的指标快照；不存在返回 {@code null}
   */
  @ReadOperation
  @Nullable
  public ThreadPoolRegistry.ThreadPoolMetricsSnapshot getThreadPool(@Selector String poolName) {
    return ThreadPoolRegistry.snapshotMetrics(poolName);
  }

  /**
   * 线程池指标端点响应体。
   *
   * @param threadPools 线程池指标快照列表
   * @param totalCount 线程池总数
   */
  public record ThreadPoolMetricsResponse(
      @NonNull List<ThreadPoolRegistry.ThreadPoolMetricsSnapshot> threadPools, int totalCount) {

    public List<ThreadPoolRegistry.ThreadPoolMetricsSnapshot> getThreadPools() {
      return threadPools;
    }

    public int getTotalCount() {
      return totalCount;
    }
  }
}
