package com.njydsz.cronjob.server.core.dag;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.dag.DagNodeStatus;
import com.njydsz.cronjob.domain.repository.DagInstanceRepository;
import com.njydsz.cronjob.domain.repository.DagNodeInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.server.core.maintenance.ScanTask;

/**
 * P2-4: 审批节点超时扫描任务。
 *
 * <p>周期性扫描 {@code ydsz_job_dag_node_instance} 表中状态为
 * {@link DagNodeStatus#WAITING_FOR_APPROVAL} 的节点，检查其等待时间是否超过
 * DAG 定义中各审批节点的 {@code approvalTimeoutMinutes} 配置。
 *
 * <p>对于超时节点：
 *
 * <ol>
 *   <li>自动标记为 {@link DagNodeStatus#APPROVAL_REJECTED}（终态）</li>
 *   <li>触发 DAG 实例终结检查（按 DAG 级 failStrategy 处理）</li>
 * </ol>
 *
 * <h3>超时判定逻辑</h3>
 *
 * <p>审批节点的超时时间存储在 DAG 定义 JSON 的 {@code approvalTimeoutMinutes} 字段中，
 * 而非节点实例表。每次扫描需要：
 *
 * <ol>
 *   <li>查询所有 WAITING_FOR_APPROVAL 节点（按 started_at 升序）
 *   <li>加载 DAG 定义获取各节点的超时配置
 *   <li>以 {@code startedAt + approvalTimeoutMinutes} 为超时阈值逐一判定
 *   <li>若 DAG 定义中未配置超时（null 或 ≤0），使用默认超时（60 分钟）
 * </ol>
 *
 * <h3>默认超时</h3>
 *
 * <p>当 DAG 定义中某审批节点未配置 {@code approvalTimeoutMinutes} 时，默认 60 分钟后
 * 自动拒绝。该默认值可通过覆写 {@link #defaultTimeoutMinutes()} 调整。
 *
 * <h3>幂等性保证</h3>
 *
 * <p>审批超时节点的状态更新通过 {@code updateById} 完成。由于超时检查基于时间计算，
 * 同一节点不会被重复判定为超时状态。{@code MaintenanceScheduler} 通过分布式锁保证
 * 单节点执行扫描任务。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ApprovalTimeoutScanTask implements ScanTask {

  /** 默认审批超时时间（分钟），当 DAG 定义中未配置时使用 */
  private static final int DEFAULT_TIMEOUT_MINUTES = 60;

  /** 每次扫描最大处理的批次数 */
  private static final int BATCH_LIMIT = 100;

  /** 审批超时扫描间隔（毫秒）：60 秒 */
  private static final long SCAN_INTERVAL_MS = 60_000L;

  /** 毫秒到分钟的换算系数 */
  private static final long MILLIS_PER_MINUTE = 60_000L;

  private final DagNodeInstanceRepository dagNodeInstanceRepository;
  private final DagInstanceRepository dagInstanceRepository;
  private final JobDagRepository jobDagRepository;
  private final DagDefinitionCodec dagDefinitionCodec;
  private final DagInstanceExecutor dagInstanceExecutor;

  @Override
  public String name() {
    return "dag-approval-timeout";
  }

  @Override
  public long intervalMs() {
    return SCAN_INTERVAL_MS;
  }

  @Override
  public void scan() {
    // 查询所有 WAITING_FOR_APPROVAL 状态的节点（按 started_at 升序，先等待的先处理）
    List<JobDagNodeInstanceVO> waitingNodes = dagNodeInstanceRepository.findWaitingApprovalNodes(BATCH_LIMIT);

    if (waitingNodes.isEmpty()) {
      return;
    }

    log.debug("[ApprovalTimeout] 扫描发现 {} 个 WAITING_FOR_APPROVAL 节点，逐一检查超时...", waitingNodes.size());

    int rejected = 0;
    for (JobDagNodeInstanceVO node : waitingNodes) {
      try {
        if (isNodeTimedOut(node)) {
          rejectTimedOutNode(node);
          rejected++;
        }
      } catch (Exception e) {
        log.warn(
            "[ApprovalTimeout] 处理审批超时节点异常: instanceId={} jobKey={} reason={}",
            node.getDagInstanceId(),
            node.getJobKey(),
            e.getMessage());
      }
    }

    if (rejected > 0) {
      log.info("[ApprovalTimeout] 审批超时自动拒绝完成: count={}", rejected);
    }
  }

  /**
   * 判断审批节点是否超时。
   *
   * <p>从 DAG 定义中读取节点的 {@code approvalTimeoutMinutes}，未配置时使用默认值。
   *
   * @param node 节点实例 VO
   * @return true 表示已超时
   */
  private boolean isNodeTimedOut(JobDagNodeInstanceVO node) {
    int timeoutMinutes = resolveTimeoutMinutes(node);
    if (timeoutMinutes <= 0) {
      timeoutMinutes = DEFAULT_TIMEOUT_MINUTES;
    }
    if (node.getStartedAt() == null) {
      // 无开始时间，视为刚刚进入等待，不判定超时
      return false;
    }
    LocalDateTime deadline = node.getStartedAt().plusMinutes(timeoutMinutes);
    return LocalDateTime.now().isAfter(deadline);
  }

  /**
   * 解析审批节点的超时分钟数（从 DAG 定义中获取）。
   *
   * @param node 节点实例 VO
   * @return 超时分钟数；未配置或解析失败返回 -1
   */
  private int resolveTimeoutMinutes(JobDagNodeInstanceVO node) {
    try {
      JobDagInstanceVO instance = dagInstanceRepository.findById(node.getDagInstanceId()).orElse(null);
      if (instance == null) {
        return -1;
      }
      JobDagVO dag = jobDagRepository.findById(instance.getDagId()).orElse(null);
      if (dag == null) {
        return -1;
      }
      DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
      DagNode dagNode = definition.findNode(node.getJobKey());
      if (dagNode == null) {
        return -1;
      }
      Integer timeout = dagNode.approvalTimeoutMinutes();
      return timeout != null ? timeout : -1;
    } catch (Exception e) {
      log.warn(
          "[ApprovalTimeout] 解析审批超时配置异常: instanceId={} jobKey={} reason={}",
          node.getDagInstanceId(),
          node.getJobKey(),
          e.getMessage());
      return -1;
    }
  }

  /**
   * 自动拒绝超时节点（WAITING_FOR_APPROVAL → APPROVAL_REJECTED）。
   *
   * <p>审批节点不走 dispatchTaskNode 的 RUNNING 流程，markFinished 的 CAS 条件不适用，
   * 故使用 updateById 直接更新状态。
   *
   * @param node 超时节点实例
   */
  private void rejectTimedOutNode(JobDagNodeInstanceVO node) {
    LocalDateTime now = LocalDateTime.now();
    long waitedMs =
        node.getStartedAt() != null ? ChronoUnit.MILLIS.between(node.getStartedAt(), now) : 0;

    // 使用 updateById 更新节点（审批节点不走 RUNNING 的 markFinished CAS 路径）
    node.setNodeStatus(DagNodeStatus.APPROVAL_REJECTED.name());
    node.setFinishedAt(now);
    node.setDurationMs(waitedMs);
    node.setErrorMessage("审批超时自动拒绝: 等待 " + (waitedMs / MILLIS_PER_MINUTE) + " 分钟未审批");
    dagNodeInstanceRepository.updateById(node);

    log.info(
        "[ApprovalTimeout] 审批超时自动拒绝: instanceId={} jobKey={} waitedMs={}",
        node.getDagInstanceId(),
        node.getJobKey(),
        waitedMs);

    // 触发 DAG 实例终结检查（审批拒绝等同于节点失败）
    dagInstanceExecutor.onApprovalRejected(node.getDagInstanceId());
  }
}
