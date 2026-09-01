package com.njydsz.cronjob.server.core.dag;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.cronjob.domain.dag.DagInstanceStatus;
import com.njydsz.cronjob.domain.dag.DagNodeStatus;
import com.njydsz.cronjob.domain.repository.DagInstanceRepository;
import com.njydsz.cronjob.domain.repository.DagNodeInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * DAG 工作流控制服务（P1-4 暂停/恢复/手动重试）。
 *
 * <p>提供对运行中 DAG 实例的控制操作：
 *
 * <ul>
 *   <li>{@link #pause(String)}: 暂停 DAG 实例，阻止 PENDING 节点被派发
 *   <li>{@link #resume(String)}: 恢复暂停的 DAG 实例，重新派发 PENDING 节点
 *   <li>{@link #cancel(String)}: 取消 DAG 实例，跳过所有未完成节点
 *   <li>{@link #retryNode(String, String)}: 手动重试指定失败节点
 * </ul>
 *
 * <h3>暂停/恢复语义</h3>
 *
 * <ul>
 *   <li>暂停后，正在执行的节点继续执行（无法中断），但不会派发新的 PENDING 节点
 *   <li>恢复后，重新派发所有 PENDING 状态的节点（包括暂停期间变为 PENDING 的节点）
 *   <li>暂停期间到达终态的 DAG 实例不能被暂停/恢复
 * </ul>
 *
 * <h3>手动重试语义</h3>
 *
 * <ul>
 *   <li>仅 FAILED 状态的节点可以重试
 *   <li>重试时重置节点状态为 PENDING 并重新派发
 *   <li>重试不影响其他节点的状态
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DagInstanceControlService {

  private final DagInstanceRepository dagInstanceRepository;
  private final DagNodeInstanceRepository dagNodeInstanceRepository;
  private final JobDagRepository jobDagRepository;
  private final JobRepository jobRepository;
  private final DagDefinitionCodec dagDefinitionCodec;
  private final DagInstanceExecutor dagInstanceExecutor;

  /**
   * 暂停 DAG 实例。
   *
   * <p>将 DAG 实例状态从 RUNNING 改为 PAUSED。 正在执行的节点继续执行，但不会派发新的 PENDING 节点。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return true 暂停成功；false 实例不存在或非 RUNNING 状态
   */
  public boolean pause(String dagInstanceId) {
    int updated = dagInstanceRepository.markPaused(dagInstanceId);
    if (updated > 0) {
      log.info("[DagControl] DAG 实例已暂停: instanceId={}", dagInstanceId);
      return true;
    }
    log.warn("[DagControl] DAG 实例暂停失败（非 RUNNING 状态或不存在）: instanceId={}", dagInstanceId);
    return false;
  }

  /**
   * 恢复暂停的 DAG 实例。
   *
   * <p>将 DAG 实例状态从 PAUSED 改为 RUNNING， 并重新派发所有 PENDING 状态的节点。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return true 恢复成功；false 实例不存在或非 PAUSED 状态
   */
  public boolean resume(String dagInstanceId) {
    int updated = dagInstanceRepository.markResumed(dagInstanceId);
    if (updated == 0) {
      log.warn("[DagControl] DAG 实例恢复失败（非 PAUSED 状态或不存在）: instanceId={}", dagInstanceId);
      return false;
    }
    log.info("[DagControl] DAG 实例已恢复: instanceId={}", dagInstanceId);

    // P0-F1: 重新派发所有 PENDING 且前置成功的节点。
    dagInstanceExecutor.redeliverPendingNodes(dagInstanceId);
    return true;
  }

  /**
   * 取消 DAG 实例。
   *
   * <p>将 DAG 实例状态改为 CANCELED，并跳过所有未完成节点。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return true 取消成功；false 实例不存在或已终态
   */
  public boolean cancel(String dagInstanceId) {
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      log.warn("[DagControl] DAG 实例不存在: instanceId={}", dagInstanceId);
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    long durationMs =
        instance.getStartedAt() != null
            ? ChronoUnit.MILLIS.between(instance.getStartedAt(), now)
            : 0;
    int updated = dagInstanceRepository.markCanceled(dagInstanceId, now, durationMs);
    if (updated == 0) {
      log.warn("[DagControl] DAG 实例取消失败（已终态或不存在）: instanceId={}", dagInstanceId);
      return false;
    }
    // 跳过所有 PENDING/RUNNING 状态的节点
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    int skipped = 0;
    for (JobDagNodeInstanceVO node : nodes) {
      if (DagNodeStatus.PENDING.name().equals(node.getNodeStatus())
          || DagNodeStatus.RUNNING.name().equals(node.getNodeStatus())) {
        dagNodeInstanceRepository.markSkipped(node.getId());
        skipped++;
      }
    }
    log.info("[DagControl] DAG 实例已取消: instanceId={} skippedNodes={}", dagInstanceId, skipped);
    return true;
  }

  /**
   * 手动重试指定失败节点。
   *
   * <p>将节点状态从 FAILED 重置为 PENDING，然后重新派发。 如果节点的所有前置节点都已成功完成，则立即派发；否则等待前置完成后再派发。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey 节点 jobKey
   * @return true 重试成功；false 节点不存在或非 FAILED 状态
   */
  public boolean retryNode(String dagInstanceId, String jobKey) {
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      log.warn("[DagControl] DAG 实例不存在: instanceId={}", dagInstanceId);
      return false;
    }
    if (DagInstanceStatus.parse(instance.getInstanceStatus()) != null
        && DagInstanceStatus.parse(instance.getInstanceStatus()).isTerminal()) {
      log.warn(
          "[DagControl] DAG 实例已终态, 无法重试节点: instanceId={} status={}",
          dagInstanceId,
          instance.getInstanceStatus());
      return false;
    }

    // 查找节点实例
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    JobDagNodeInstanceVO targetNode = null;
    for (JobDagNodeInstanceVO node : nodes) {
      if (jobKey.equals(node.getJobKey())
          && DagNodeStatus.FAILED.name().equals(node.getNodeStatus())) {
        targetNode = node;
        break;
      }
    }
    if (targetNode == null) {
      log.warn("[DagControl] 未找到 FAILED 状态的节点: instanceId={} jobKey={}", dagInstanceId, jobKey);
      return false;
    }

    // 重置节点状态为 PENDING
    dagNodeInstanceRepository.markSkipped(targetNode.getId()); // 先标记为 SKIPPED
    // 重新插入一条新的节点实例（避免状态冲突）
    JobDagNodeInstanceVO retryNode = new JobDagNodeInstanceVO();
    retryNode.setDagInstanceId(dagInstanceId);
    retryNode.setDagId(instance.getDagId());
    retryNode.setJobId(targetNode.getJobId());
    retryNode.setJobKey(targetNode.getJobKey() + "#retry" + System.currentTimeMillis());
    retryNode.setNodeStatus(DagNodeStatus.PENDING.name());
    retryNode.setRetryCount(
        targetNode.getRetryCount() != null ? targetNode.getRetryCount() + 1 : 1);
    retryNode.setMaxRetries(targetNode.getMaxRetries());
    retryNode.setTenantId(instance.getTenantId());
    dagNodeInstanceRepository.insert(retryNode);

    log.info(
        "[DagControl] 节点重试: instanceId={} jobKey={} retryCount={}",
        dagInstanceId,
        jobKey,
        retryNode.getRetryCount());

    // 加载 DAG 定义并派发节点
    JobDagVO dag = jobDagRepository.findById(instance.getDagId()).orElse(null);
    if (dag == null) {
      log.error("[DagControl] DAG 定义不存在: dagId={}", instance.getDagId());
      return false;
    }
    DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
    DagNode dagNode = definition.findNode(jobKey);
    if (dagNode == null) {
      log.error("[DagControl] DAG 节点定义不存在: jobKey={}", jobKey);
      return false;
    }

    // 检查前置是否都成功
    if (areAllPredecessorsSuccessful(dagInstanceId, jobKey, definition, nodes)) {
      // 直接派发
      dispatchRetryNode(dagInstanceId, instance.getDagId(), dagNode, retryNode);
    } else {
      log.info("[DagControl] 前置未全部成功, 节点等待自动触发: instanceId={} jobKey={}", dagInstanceId, jobKey);
    }
    return true;
  }

  /**
   * P1-7: 跳过指定节点（单节点级控制）。
   *
   * <p>将节点状态从 PENDING 或 FAILED 改为 SKIPPED，然后推进后继节点。 仅非终态节点可跳过。跳过后后继节点的前置条件检查会跳过该节点。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey 节点 jobKey
   * @return true 跳过成功；false 节点不存在或已终态
   */
  public boolean skipNode(String dagInstanceId, String jobKey) {
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      log.warn("[DagControl] DAG 实例不存在: instanceId={}", dagInstanceId);
      return false;
    }
    if (DagInstanceStatus.parse(instance.getInstanceStatus()) != null
        && DagInstanceStatus.parse(instance.getInstanceStatus()).isTerminal()) {
      log.warn(
          "[DagControl] DAG 实例已终态, 无法跳过节点: instanceId={} status={}",
          dagInstanceId,
          instance.getInstanceStatus());
      return false;
    }
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    for (JobDagNodeInstanceVO node : nodes) {
      if (jobKey.equals(node.getJobKey())
          && !DagNodeStatus.parse(node.getNodeStatus()).isTerminal()) {
        dagNodeInstanceRepository.markSkipped(node.getId());
        log.info("[DagControl] 节点已跳过: instanceId={} jobKey={}", dagInstanceId, jobKey);
        // 触发后继节点检查
        dagInstanceExecutor.execute(dagInstanceId);
        return true;
      }
    }
    log.warn("[DagControl] 未找到可跳过的节点: instanceId={} jobKey={}", dagInstanceId, jobKey);
    return false;
  }

  /**
   * P1-7: 强制完成指定节点（单节点级控制）。
   *
   * <p>将节点状态从 PENDING/RUNNING/FAILED 改为 SUCCESS，然后推进后继节点。 适用于"已知可忽略"的失败节点，强制标记成功后继续执行后继。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey 节点 jobKey
   * @return true 强制成功；false 节点不存在或已终态
   */
  public boolean forceCompleteNode(String dagInstanceId, String jobKey) {
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      log.warn("[DagControl] DAG 实例不存在: instanceId={}", dagInstanceId);
      return false;
    }
    if (DagInstanceStatus.parse(instance.getInstanceStatus()) != null
        && DagInstanceStatus.parse(instance.getInstanceStatus()).isTerminal()) {
      log.warn(
          "[DagControl] DAG 实例已终态, 无法强制完成节点: instanceId={} status={}",
          dagInstanceId,
          instance.getInstanceStatus());
      return false;
    }
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    for (JobDagNodeInstanceVO node : nodes) {
      if (jobKey.equals(node.getJobKey())
          && !DagNodeStatus.parse(node.getNodeStatus()).isTerminal()) {
        dagNodeInstanceRepository.markFinished(
            node.getId(),
            DagNodeStatus.SUCCESS.name(),
            LocalDateTime.now(),
            0,
            null,
            "手动强制完成",
            null);
        log.info("[DagControl] 节点已强制完成: instanceId={} jobKey={}", dagInstanceId, jobKey);
        // 触发后继节点检查
        dagInstanceExecutor.execute(dagInstanceId);
        return true;
      }
    }
    log.warn("[DagControl] 未找到可强制完成的节点: instanceId={} jobKey={}", dagInstanceId, jobKey);
    return false;
  }

  /**
   * P1-6: 审批指定节点（APPROVAL 节点）。
   *
   * <p>将 WAITING_FOR_APPROVAL 状态的节点改为 SUCCESS（通过）或 APPROVAL_REJECTED（拒绝）。 审批通过后推进后继节点；审批拒绝后按 DAG 级
   * failStrategy 处理。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey 节点 jobKey
   * @param approved true=审批通过, false=审批拒绝
   * @param comment 审批意见（可为 null）
   * @return true 审批成功；false 节点不存在或非 WAITING_FOR_APPROVAL 状态
   */
  public boolean approveNode(
      String dagInstanceId, String jobKey, boolean approved, String comment) {
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      log.warn("[DagControl] DAG 实例不存在: instanceId={}", dagInstanceId);
      return false;
    }
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    for (JobDagNodeInstanceVO node : nodes) {
      if (jobKey.equals(node.getJobKey())
          && DagNodeStatus.WAITING_FOR_APPROVAL.name().equals(node.getNodeStatus())) {
        DagNodeStatus newStatus =
            approved ? DagNodeStatus.SUCCESS : DagNodeStatus.APPROVAL_REJECTED;
        String resultJson = comment != null ? YdszJson.toJson(Map.of("comment", comment)) : null;
        dagNodeInstanceRepository.markFinished(
            node.getId(),
            newStatus.name(),
            LocalDateTime.now(),
            0,
            null,
            approved ? "审批通过" : "审批拒绝",
            resultJson);
        log.info(
            "[DagControl] 节点审批{}: instanceId={} jobKey={} comment={}",
            approved ? "通过" : "拒绝",
            dagInstanceId,
            jobKey,
            comment);
        if (approved) {
          // 审批通过：触发后继节点派发
          dagInstanceExecutor.redeliverPendingNodes(dagInstanceId);
        } else {
          // 审批拒绝：触发 DAG 实例终结检查（按 failStrategy 处理）
          dagInstanceExecutor.onApprovalRejected(dagInstanceId);
        }
        return true;
      }
    }
    log.warn(
        "[DagControl] 未找到 WAITING_FOR_APPROVAL 状态的节点: instanceId={} jobKey={}",
        dagInstanceId,
        jobKey);
    return false;
  }

  /** 派发重试节点。 */
  private void dispatchRetryNode(
      String dagInstanceId, String dagId, DagNode dagNode, JobDagNodeInstanceVO retryNode) {
    JobVO job = jobRepository.findById(dagNode.jobId()).orElse(null);
    if (job == null) {
      log.warn("[DagControl] 重试节点任务不存在: jobKey={}", dagNode.jobKey());
      dagNodeInstanceRepository.markFinished(
          retryNode.getId(),
          DagNodeStatus.FAILED.name(),
          LocalDateTime.now(),
          0,
          null,
          "任务不存在",
          null);
      return;
    }
    // 标记 RUNNING
    dagNodeInstanceRepository.markRunning(retryNode.getId(), LocalDateTime.now());
    // 通过事件触发派发（复用 DagInstanceExecutor 的逻辑）
    dagInstanceExecutor.execute(dagInstanceId);
  }

  /** 检查指定节点的所有前置节点是否都成功完成。 */
  private boolean areAllPredecessorsSuccessful(
      String dagInstanceId,
      String jobKey,
      DagDefinition definition,
      List<JobDagNodeInstanceVO> nodes) {
    List<DagEdge> incoming = definition.incomingEdges(jobKey);
    if (incoming.isEmpty()) {
      return true;
    }
    for (DagEdge edge : incoming) {
      DagNode predNode = definition.findNode(edge.from());
      if (predNode == null) {
        continue;
      }
      String lookupId = predNode.jobId() != null ? predNode.jobId() : predNode.jobKey();
      boolean found = false;
      for (JobDagNodeInstanceVO node : nodes) {
        if (lookupId.equals(node.getJobId()) || predNode.jobKey().equals(node.getJobKey())) {
          if (!DagNodeStatus.SUCCESS.name().equals(node.getNodeStatus())) {
            return false;
          }
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }
}
