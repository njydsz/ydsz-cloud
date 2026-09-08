package com.njydsz.workflow.server.timer;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.workflow.domain.enums.FlowTimeoutStrategy;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.repository.FlowTimerRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.domain.vo.FlowTimerVO;

/**
 * 超时自动转办定时任务（P1-6）
 *
 * <p>每 60 秒扫描 ydzsz_flow_timer 表中超时的任务，根据配置的 {@code timeoutStrategy} 执行不同策略：
 *
 * <ul>
 *   <li>AUTO_PASS — 自动通过（标记超时自动审批）
 *   <li>TRANSFER_ADMIN — 转交管理员
 *   <li>TRANSFER_SUPERIOR — 转交上级
 *   <li>REMIND — 仅催办（发送提醒通知）
 * </ul>
 *
 * <p>使用 {@link YdszDistributedLock} 防止多节点并发重复处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowTimeoutJob {

  /** 单次扫描最大处理数量，防止单次处理过多任务 */
  private static final int MAX_BATCH_SIZE = 100;

  private final FlowTimerRepository timerRepository;

  private final FlowRunTaskRepository runTaskRepository;

  private final FlowTimeoutHandler timeoutHandler;

  /**
   * 每 60 秒扫描超时任务并执行对应策略
   *
   * <p>通过 {@link YdszDistributedLock} 保证集群单节点执行，获取不到锁的节点直接跳过。
   */
  @Scheduled(fixedDelayString = "${ydsz.flow.timeout.scan-interval-ms:60000}")
  @YdszDistributedLock(
      key = "'flow:timeout:job:scan'",
      waitTime = 0,
      leaseTime = 120,
      message = "超时处理任务正在其他节点执行")
  /**
   * 处理超时任务：扫描到期定时器、触发超时动作（自动通过 / 转办 / 催办）。
   */
  public void processTimeouts() {
    try {
      doProcessTimeouts();
    } catch (Exception e) {
      log.error("[Flow-TimeoutJob] 超时处理异常: {}", e.getMessage(), e);
    }
  }

  /**
   * 执行超时处理逻辑
   *
   * <ol>
   *   <li>查询 fireAt &lt;= now 且 status='PENDING' 的定时器
   *   <li>逐个查询关联的运行时任务
   *   <li>解析超时策略（从任务节点配置或定时器表字段）
   *   <li>委托 {@link FlowTimeoutHandler} 执行策略
   *   <li>标记定时器为 FIRED
   * </ol>
   */
  private void doProcessTimeouts() {
    LocalDateTime now = LocalDateTime.now();
    List<FlowTimerVO> dueTimers = timerRepository.findDueTimers(now, MAX_BATCH_SIZE);

    if (dueTimers == null || dueTimers.isEmpty()) {
      return;
    }

    log.info("[Flow-TimeoutJob] 扫描到 {} 个超时定时器", dueTimers.size());

    int successCount = 0;
    int failCount = 0;

    for (FlowTimerVO timer : dueTimers) {
      try {
        processSingleTimer(timer);
        successCount++;
      } catch (Exception e) {
        failCount++;
        log.error(
            "[Flow-TimeoutJob] 处理定时器异常: timerId={} taskId={} err={}",
            timer.getId(),
            timer.getTaskId(),
            e.getMessage(),
            e);
      } finally {
        // 无论成功失败，标记定时器为已触发，避免重复处理
        try {
          timerRepository.markFired(timer.getId());
        } catch (Exception e) {
          log.warn("[Flow-TimeoutJob] 标记定时器已触发失败: timerId={}", timer.getId());
        }
      }
    }

    log.info("[Flow-TimeoutJob] 超时处理完成: success={} fail={}", successCount, failCount);
  }

  /**
   * 处理单个超时定时器
   *
   * @param timer 超时定时器
   */
  private void processSingleTimer(FlowTimerVO timer) {
    String taskId = timer.getTaskId();
    if (taskId == null || taskId.isBlank()) {
      log.warn("[Flow-TimeoutJob] 定时器未关联任务: timerId={}", timer.getId());
      return;
    }

    FlowRunTaskVO task = runTaskRepository.findById(taskId).orElse(null);
    if (task == null) {
      log.warn("[Flow-TimeoutJob] 定时器关联任务不存在: timerId={} taskId={}", timer.getId(), taskId);
      return;
    }

    // 从定时器表的节点配置中解析超时策略
    FlowTimeoutStrategy strategy = resolveStrategy(timer);
    log.info(
        "[Flow-TimeoutJob] 处理超时: timerId={} taskId={} nodeCode={} strategy={}",
        timer.getId(),
        taskId,
        timer.getNodeCode(),
        strategy.getCode());

    // 委托处理器执行超时策略
    String result = timeoutHandler.handleTimeout(task, strategy);
    log.info("[Flow-TimeoutJob] 超时处理结果: timerId={} result={}", timer.getId(), result);
  }

  /**
   * 解析超时策略
   *
   * <p>从定时器 timerType 中解析：timerType 格式为 "TIMEOUT_{STRATEGY}"，
   * 如 "TIMEOUT_AUTO_PASS"、"TIMEOUT_TRANSFER_ADMIN"。
   * 未配置时默认 REMIND。
   *
   * @param timer 定时器 VO
   * @return 超时策略枚举
   */
  private FlowTimeoutStrategy resolveStrategy(FlowTimerVO timer) {
    String timerType = timer.getTimerType();
    if (timerType != null && timerType.startsWith("TIMEOUT_")) {
      String strategyCode = timerType.substring("TIMEOUT_".length());
      return FlowTimeoutStrategy.of(strategyCode);
    }
    // 默认策略：仅提醒
    return FlowTimeoutStrategy.REMIND;
  }
}