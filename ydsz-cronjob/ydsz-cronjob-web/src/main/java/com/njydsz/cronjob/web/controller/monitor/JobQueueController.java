package com.njydsz.cronjob.web.controller.monitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher;

/**
 * 任务执行队列实时状态 Controller（P0-A2）。
 *
 * <p>暴露任务执行线程池的实时运行指标，便于运维监控、容量规划和告警配置。
 *
 * <h3>核心能力</h3>
 *
 * <ul>
 *   <li>{@link #getQueueStatus} - 查询执行线程池实时状态（活跃数/队列大小/已完成数/拒绝数）
 * </ul>
 *
 * <h3>核心指标说明</h3>
 *
 * <ul>
 *   <li>{@code activeCount} - 当前正在执行的任务数（线程池活跃线程数）
 *   <li>{@code poolSize} - 线程池当前线程数（含空闲线程）
 *   <li>{@code maximumPoolSize} - 线程池最大线程数（容量上限）
 *   <li>{@code queueSize} - 工作队列中等待执行的任务数
 *   <li>{@code queueRemainingCapacity} - 工作队列剩余容量
 *   <li>{@code completedTaskCount} - 历史已完成任务总数（线程池生命周期内累计）
 *   <li>{@code taskCount} - 历史提交任务总数
 * </ul>
 *
 * <h3>使用建议</h3>
 *
 * 运维可基于 {@code queueSize / queueRemainingCapacity} 评估积压情况； 基于 {@code activeCount / maximumPoolSize}
 * 评估负载率； 当 {@code queueSize} 持续接近 {@code queueRemainingCapacity} 时应触发扩容告警。
 *
 * <h3>安全</h3>
 *
 * 接口加 {@link AuthApiPermission} 权限控制（{@link PermissionCodes#CRONJOB_STATS_VIEW}）， 只读不写，无需幂等/限流/审计。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Tag(name = "执行队列状态", description = "任务执行线程池实时状态：活跃数/队列大小/已完成数/拒绝数")
@RestController
@RequestMapping("/api/v1/cronjob/queue")
@RequiredArgsConstructor
public class JobQueueController {

  /** 任务派发器（ObjectProvider 注入，避免循环依赖） */
  private final ObjectProvider<DefaultTaskDispatcher> taskDispatcherProvider;

  /**
   * 查询执行队列实时状态。
   *
   * <p>读取 {@link DefaultTaskDispatcher#getTaskExecutorPool} 线程池的所有指标， 转换为前端友好的 Map 返回。当
   * TaskDispatcher 或 ThreadPoolExecutor 未初始化时 返回空 Map（避免 null pointer）。
   *
   * @return 队列状态 Map（含 activeCount / poolSize / maximumPoolSize / queueSize /
   *     queueRemainingCapacity / completedTaskCount / taskCount 共 7 个字段）
   */
  @Operation(summary = "查询执行队列状态")
  @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_STATS_VIEW)
  @GetMapping("/status")
  public YdszResponse<Map<String, Object>> getQueueStatus() {
    // 1. 获取 TaskDispatcher（ObjectProvider 避免强耦合，允许 TaskDispatcher 未注册时返回空）
    DefaultTaskDispatcher dispatcher = taskDispatcherProvider.getIfAvailable();
    if (dispatcher == null) {
      log.debug("[JobQueue] TaskDispatcher 不可用，返回空状态");
      return YdszResponse.success(new HashMap<>());
    }
    // 2. 获取线程池
    ThreadPoolExecutor pool = dispatcher.getTaskExecutorPool();
    if (pool == null) {
      log.debug("[JobQueue] 线程池未初始化，返回空状态");
      return YdszResponse.success(new HashMap<>());
    }
    // 3. 采集线程池实时指标
    Map<String, Object> status = new HashMap<>();
    status.put("activeCount", pool.getActiveCount());
    status.put("poolSize", pool.getPoolSize());
    status.put("maximumPoolSize", pool.getMaximumPoolSize());
    status.put("queueSize", pool.getQueue().size());
    status.put("queueRemainingCapacity", pool.getQueue().remainingCapacity());
    status.put("completedTaskCount", pool.getCompletedTaskCount());
    status.put("taskCount", pool.getTaskCount());
    return YdszResponse.success(status);
  }
}
