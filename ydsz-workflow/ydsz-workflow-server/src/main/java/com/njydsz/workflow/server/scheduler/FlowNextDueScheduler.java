package com.njydsz.workflow.server.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.service.FlowSlaService;

/**
 * Next-Due 动态调度器（P1-1）
 *
 * <p>基于任务截止时间（dueAt）的<b>动态调度器</b>，替代传统的固定频率 cron 扫描。 核心思想：查询最近一个即将到期的任务，精确调度触发时间，避免无效轮询。
 *
 * <p><b>调度策略：</b>
 *
 * <ul>
 *   <li>系统启动或每次执行完成后，查询最近的待办任务 dueAt
 *   <li>若存在 dueAt 在 future 的任务，动态调度到该时间点触发
 *   <li>若所有任务已过期或不存在待办，退化为固定延迟兜底扫描
 *   <li>通过 {@link DistributedScheduled} 保证集群单节点执行
 * </ul>
 *
 * <p><b>性能优势：</b>相比固定 30 分钟轮询，动态调度在任务稀疏场景下大幅减少无效 DB 查询； 在任务密集场景下保证准时触发（误差 &lt; 1s）。
 *
 * <p><b>事务边界：</b>调度器本身不开启事务，仅触发 {@link FlowSlaService} 执行 SLA 检查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowNextDueScheduler implements SchedulingConfigurer {

  /** 运行时任务仓储，查询最近 dueAt */
  private final FlowRunTaskRepository runTaskRepository;

  /** SLA 服务，处理到期任务的升级/通知逻辑 */
  private final FlowSlaService slaService;

  /** Spring 任务调度器，支持动态取消/重调度 */
  private final TaskScheduler taskScheduler;

  /** 当前调度句柄，用于取消已调度任务 */
  private volatile ScheduledFuture<?> currentScheduled;

  /** 默认兜底扫描间隔（当无 dueAt 任务时） */
  private static final Duration DEFAULT_FALLBACK_INTERVAL = Duration.ofMinutes(30);

  /** 最小调度间隔（防止过于频繁的调度） */
  private static final Duration MIN_SCHEDULE_INTERVAL = Duration.ofSeconds(10);

  /** 最大提前调度间隔（防止调度过远的任务） */
  private static final Duration MAX_AHEAD_SCHEDULE = Duration.ofHours(24);

  /**
   * 系统启动后初始化调度。
   *
   * <p>通过 {@link PostConstruct} 在 Bean 初始化完成后触发首次调度计算。
   */
  @PostConstruct
  public void init() {
    scheduleNext();
  }

  /**
   * Bean 销毁前取消当前调度。
   *
   * <p>通过 {@link PreDestroy} 在 Spring 容器关闭时优雅取消已调度任务，避免重复执行。
   */
  @PreDestroy
  public void destroy() {
    cancelCurrent();
  }

  /**
   * 配置 Spring 调度器（实现 {@link SchedulingConfigurer}）。
   *
   * <p>注册一个固定频率的兜底触发器，确保即使动态调度失败，系统仍能周期性检查。
   *
   * @param taskRegistrar 调度注册器
   */
  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.setScheduler(taskScheduler);
    // 兜底触发器：每 30 分钟检查一次（仅在动态调度未触发时生效）
    taskRegistrar.addFixedDelayTask(this::fallbackScan, DEFAULT_FALLBACK_INTERVAL.toMillis());
  }

  /**
   * 核心调度逻辑：查询最近 dueAt 并动态调度。
   *
   * <p>通过 {@link DistributedScheduled} 保证集群单节点执行。获取不到锁的节点直接跳过。
   */
  @DistributedScheduled(lockKey = "flow:next-due:schedule", leaseTime = 60)
  public void scheduleNext() {
    try {
      doScheduleNext();
    } catch (Exception e) {
      log.error("[NextDue] 动态调度异常: {}", e.getMessage(), e);
    }
  }

  /**
   * 兜底扫描：当动态调度未覆盖时，周期性检查到期任务。
   */
  private void fallbackScan() {
    try {
      log.debug("[NextDue] 兜底扫描触发");
      slaService.scanAndProcess();
    } catch (Exception e) {
      log.error("[NextDue] 兜底扫描异常: {}", e.getMessage(), e);
    }
  }

  /**
   * 执行动态调度计算。
   *
   * <ol>
   *   <li>查询最近的待办任务 dueAt
   *   <li>计算距离 now 的偏移量
   *   <li>在 [MIN_SCHEDULE_INTERVAL, MAX_AHEAD_SCHEDULE] 范围内调度
   *   <li>若无有效 dueAt，退化为兜底间隔
   * </ol>
   */
  private void doScheduleNext() {
    // 查询最近的待办任务（带 dueAt 的）
    FlowRunTaskVO nearestTask = findNearestDueTask();

    if (nearestTask == null || nearestTask.getDueAt() == null) {
      log.debug("[NextDue] 无带截止时间的待办任务，跳过动态调度");
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    Duration delay = Duration.between(now, nearestTask.getDueAt());

    // 已过期任务：立即处理
    if (delay.isNegative() || delay.isZero()) {
      log.info("[NextDue] 发现已过期任务，立即处理: taskId={} dueAt={}", nearestTask.getId(), nearestTask.getDueAt());
      slaService.scanAndProcess();
      return;
    }

    // 限制最大提前调度间隔
    if (delay.compareTo(MAX_AHEAD_SCHEDULE) > 0) {
      delay = MAX_AHEAD_SCHEDULE;
      log.debug("[NextDue] 超出最大提前调度间隔，截断至 {}h", MAX_AHEAD_SCHEDULE.toHours());
    }

    // 限制最小调度间隔
    if (delay.compareTo(MIN_SCHEDULE_INTERVAL) < 0) {
      delay = MIN_SCHEDULE_INTERVAL;
    }

    // 取消旧调度，创建新调度
    cancelCurrent();
    Date triggerTime = Date.from(now.plus(delay).atZone(ZoneId.systemDefault()).toInstant());
    currentScheduled = taskScheduler.schedule(this::onScheduledTrigger, triggerTime);

    log.info(
        "[NextDue] 动态调度已设置: triggerIn={}s taskId={} dueAt={}",
        delay.getSeconds(),
        nearestTask.getId(),
        nearestTask.getDueAt());
  }

  /**
   * 调度触发时的回调。
   *
   * <ol>
   *   <li>处理所有到期任务
   *   <li>重新计算下一次调度
   * </ol>
   */
  private void onScheduledTrigger() {
    log.info("[NextDue] 动态调度触发，开始处理到期任务");
    try {
      slaService.scanAndProcess();
    } catch (Exception e) {
      log.error("[NextDue] 到期任务处理异常: {}", e.getMessage(), e);
    } finally {
      // 处理完成后重新调度下一次
      scheduleNext();
    }
  }

  /**
   * 查询最近的待办任务（按 dueAt 升序）。
   *
   * @return 最近的待办任务，无则返回 null
   */
  private FlowRunTaskVO findNearestDueTask() {
    // 查询未来 24 小时内到期的待办任务，取第一条
    List<FlowRunTaskVO> candidates = runTaskRepository.selectSlaCandidates(1);
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    return candidates.get(0);
  }

  /**
   * 取消当前已调度的任务。
   */
  private void cancelCurrent() {
    if (currentScheduled != null && !currentScheduled.isCancelled()) {
      currentScheduled.cancel(false);
      currentScheduled = null;
    }
  }
}
