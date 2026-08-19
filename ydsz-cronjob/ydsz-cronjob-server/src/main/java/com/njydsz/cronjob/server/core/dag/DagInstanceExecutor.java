package com.njydsz.cronjob.server.core.dag;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.cronjob.domain.dag.DagInstanceStatus;
import com.njydsz.cronjob.domain.dag.DagNodeStatus;
import com.njydsz.cronjob.infra.entity.dag.JobDag;
import com.njydsz.cronjob.infra.entity.dag.JobDagInstance;
import com.njydsz.cronjob.infra.entity.dag.JobDagNodeInstance;
import com.njydsz.cronjob.infra.entity.job.Job;
import com.njydsz.cronjob.infra.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.dag.JobDagInstanceMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.core.TaskCompletedEvent;
import com.njydsz.cronjob.server.core.dispatch.TaskDispatcher;

/**
 * DAG 实例执行器。
 *
 * <p>负责基于 DAG 定义（{@link DagDefinition}）执行 DAG 实例：
 *
 * <ol>
 *   <li>{@link #execute(String)}：创建节点实例，派发起始节点（无入边）
 *   <li>{@link #onTaskCompleted(TaskCompletedEvent)}：监听任务完成事件，通过查询节点实例表判断是否为 DAG 节点，更新节点状态并触发后继
 *   <li>所有节点完成后，更新 DAG 实例终态（SUCCESS/FAILED/PARTIAL_SUCCESS）
 * </ol>
 *
 * <p>支持 TASK / SUB_WORKFLOW / APPROVAL 三种节点类型。CONDITION / LOOP / PARALLEL_GATEWAY 控制节点
 * 已于 v1.2.0 移除，建议使用工作流引擎（Flowable/Camunda）替代复杂编排场景。
 *
 * <h3>跨节点上下文传递（P2-5）</h3>
 *
 * <p>节点执行结果写入 DAG 实例级上下文（{@code contextJson}），后继节点可通过 {@link JobDagInstanceMapper#updateContext}
 * 读取。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagInstanceExecutor {

  private final JobDagInstanceMapper dagInstanceMapper;
  private final JobDagNodeInstanceMapper dagNodeInstanceMapper;
  private final JobDagMapper dagMapper;
  private final JobMapper jobMapper;
  private final JobLogMapper jobLogMapper;
  private final DagDefinitionCodec dagDefinitionCodec;
  private final TaskDispatcher taskDispatcher;

  /**
   * 异步执行 DAG 实例。
   *
   * <p>步骤：
   *
   * <ol>
   *   <li>加载 DAG 实例与定义
   *   <li>标记实例为 RUNNING
   *   <li>为每个节点创建节点实例（PENDING）
   *   <li>派发所有起始节点（无入边）
   * </ol>
   *
   * @param dagInstanceId DAG 实例 ID
   */
  @Async
  public void execute(String dagInstanceId) {
    try {
      doExecute(dagInstanceId);
    } catch (Exception e) {
      log.error("[DagInstance] 执行异常: instanceId={} reason={}", dagInstanceId, e.getMessage(), e);
      markInstanceFailed(dagInstanceId, "DAG 执行异常: " + e.getMessage());
    }
  }

  /**
   * P0-F1: 重新派发指定 DAG 实例中所有 PENDING 且前置已成功的节点。
   *
   * <p>供 DAG 恢复（PAUSED → RUNNING）后使用。原实现（DagInstanceControlService）误调用
   * {@link #execute(String)}，但 {@link #doExecute} 开头 CAS 要求实例为 PENDING 状态，
   * RUNNING 实例会直接 return，导致恢复后 PENDING 节点永远不会被派发。
   *
   * <p>本方法直接扫描节点实例并逐个 {@link #dispatchNode}，不受实例状态守卫影响。
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 实际派发的节点数
   */
  public int redeliverPendingNodes(String dagInstanceId) {
    JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
    if (instance == null) {
      log.warn("[DagInstance] 重投递失败, 实例不存在: instanceId={}", dagInstanceId);
      return 0;
    }
    JobDag dag = dagMapper.selectById(instance.getDagId());
    if (dag == null) {
      log.warn("[DagInstance] 重投递失败, DAG 定义不存在: instanceId={} dagId={}", dagInstanceId, instance.getDagId());
      return 0;
    }
    DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
    List<JobDagNodeInstance> nodes = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
    int dispatched = 0;
    for (JobDagNodeInstance node : nodes) {
      if (!DagNodeStatus.PENDING.name().equals(node.getNodeStatus())) {
        continue;
      }
      // 优先按原始 jobKey 查找；兼容 LOOP iter 后缀（历史数据）
      DagNode dagNode = definition.findNode(node.getJobKey());
      if (dagNode == null && node.getJobKey() != null && node.getJobKey().contains("#")) {
        dagNode = definition.findNode(node.getJobKey().split("#")[0]);
      }
      if (dagNode == null) {
        continue;
      }
      if (areAllPredecessorsSuccessful(dagInstanceId, dagNode.jobKey(), definition)) {
        dispatchNode(dagInstanceId, instance.getDagId(), dagNode, definition);
        dispatched++;
      }
    }
    if (dispatched > 0) {
      log.info(
          "[DagInstance] 恢复后重新派发 PENDING 节点: instanceId={} count={}",
          dagInstanceId,
          dispatched);
    }
    return dispatched;
  }

  /**
   * 监听任务完成事件，更新 DAG 节点状态并触发后继。
   *
   * <p>通过查询节点实例表判断是否为 DAG 节点：
   *
   * <ul>
   *   <li>匹配 PENDING/RUNNING 状态的节点实例 → 是 DAG 节点，处理
   *   <li>无匹配 → 非 DAG 节点，跳过
   * </ul>
   */
  @Async
  @EventListener
  public void onTaskCompleted(TaskCompletedEvent event) {
    try {
      handleNodeCompletion(event);
    } catch (Exception e) {
      log.error("[DagInstance] 节点完成处理异常: jobId={} reason={}", event.jobId(), e.getMessage(), e);
    }
  }

  // ==================== 核心执行逻辑 ====================

  private void doExecute(String dagInstanceId) {
    JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
    if (instance == null) {
      log.warn("[DagInstance] 实例不存在: instanceId={}", dagInstanceId);
      return;
    }
    JobDag dag = dagMapper.selectById(instance.getDagId());
    if (dag == null) {
      markInstanceFailed(dagInstanceId, "DAG 定义不存在");
      return;
    }

    // P1-3: DAG 超时检查
    if (dag.getTimeoutMs() != null && dag.getTimeoutMs() > 0 && instance.getStartedAt() != null) {
      long elapsedMs = Duration.between(instance.getStartedAt(), LocalDateTime.now()).toMillis();
      if (elapsedMs > dag.getTimeoutMs()) {
        log.warn(
            "[DagInstance] DAG 已超时, 标记为 TIMEOUT: instanceId={} elapsedMs={} timeoutMs={}",
            dagInstanceId,
            elapsedMs,
            dag.getTimeoutMs());
        markInstanceFailed(
            dagInstanceId,
            "DAG 超时: elapsed=" + elapsedMs + "ms, timeout=" + dag.getTimeoutMs() + "ms");
        return;
      }
    }
    DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
    log.info(
        "[DagInstance] 开始执行: instanceId={} dagKey={} nodes={} edges={}",
        dagInstanceId,
        dag.getDagKey(),
        definition.nodeCount(),
        definition.edges().size());

    // 标记 RUNNING
    int updated = dagInstanceMapper.markRunning(dagInstanceId, LocalDateTime.now());
    if (updated == 0) {
      log.warn("[DagInstance] 实例非 PENDING 状态, 跳过执行: instanceId={}", dagInstanceId);
      return;
    }

    // 创建节点实例
    List<DagNode> nodes = definition.nodes();
    for (DagNode node : nodes) {
      JobDagNodeInstance nodeInstance = new JobDagNodeInstance();
      nodeInstance.setDagInstanceId(dagInstanceId);
      nodeInstance.setDagId(instance.getDagId());
      nodeInstance.setJobId(node.jobId());
      nodeInstance.setJobKey(node.jobKey());
      nodeInstance.setNodeStatus(DagNodeStatus.PENDING.name());
      nodeInstance.setRetryCount(0);
      // P2-6: 从 Job 读取 maxRetries，支持 RETRY 失败策略
      nodeInstance.setMaxRetries(resolveNodeMaxRetries(node.jobId()));
      nodeInstance.setTenantId(instance.getTenantId());
      dagNodeInstanceMapper.insert(nodeInstance);
    }

    // 更新总节点数
    JobDagInstance update = new JobDagInstance();
    update.setId(dagInstanceId);
    update.setTotalNodes(nodes.size());
    update.setSuccessNodes(0);
    update.setFailedNodes(0);
    update.setSkippedNodes(0);
    dagInstanceMapper.updateById(update);

    // 派发起始节点（无入边）
    List<DagNode> rootNodes = definition.rootNodes();
    for (DagNode node : rootNodes) {
      dispatchNode(dagInstanceId, instance.getDagId(), node, definition);
    }

    // 如果没有起始节点（理论上不会，已校验无环），直接标记完成
    if (rootNodes.isEmpty()) {
      finalizeInstance(dagInstanceId);
    }
  }

  /**
   * 派发单个 DAG 节点任务。
   *
   * <p>所有节点类型（TASK / SUB_WORKFLOW / APPROVAL）均通过 {@link TaskDispatcher#dispatch} 执行，
   * 区别由 {@link com.njydsz.cronjob.domain.job.JobHandler} 实现内部处理。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param dagId DAG 定义 ID
   * @param node DAG 节点
   * @param definition DAG 定义
   */
  private void dispatchNode(
      String dagInstanceId, String dagId, DagNode node, DagDefinition definition) {
    dispatchTaskNode(dagInstanceId, dagId, node, definition);
  }

  /** 派发 TASK 类型节点（现有逻辑：调用 handler 执行）。 */
  private void dispatchTaskNode(
      String dagInstanceId, String dagId, DagNode node, DagDefinition definition) {
    Job job = jobMapper.selectById(node.jobId());
    if (job == null) {
      log.warn(
          "[DagInstance] 节点任务不存在, 标记 FAILED: instanceId={} jobKey={}",
          dagInstanceId,
          node.jobKey());
      markNodeFailed(dagInstanceId, node.jobKey(), "任务不存在");
      return;
    }
    if (!"NORMAL".equals(job.getStatus())) {
      log.info(
          "[DagInstance] 节点任务非 NORMAL 状态, 标记 SKIPPED: instanceId={} jobKey={} status={}",
          dagInstanceId,
          node.jobKey(),
          job.getStatus());
      markNodeSkipped(dagInstanceId, node.jobKey());
      return;
    }

    // 标记节点 RUNNING
    JobDagNodeInstance nodeInstance =
        dagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, node.jobId());
    if (nodeInstance == null) {
      log.warn("[DagInstance] 节点实例不存在: instanceId={} jobId={}", dagInstanceId, node.jobId());
      return;
    }
    dagNodeInstanceMapper.markRunning(nodeInstance.getId(), LocalDateTime.now());

    // 派发任务（triggerType=DEPENDENT, 抢锁）
    String logId = taskDispatcher.dispatch(job, null, "DEPENDENT");
    log.info(
        "[DagInstance] 节点派发: instanceId={} jobKey={} logId={}",
        dagInstanceId,
        node.jobKey(),
        logId);

    // 如果 dispatch 同步返回 logId 且任务已执行完成（MANUAL 触发同步执行），
    // 节点状态可能已经通过事件更新，这里不重复处理
    if (logId != null) {
      // 更新节点实例的 logId
      JobDagNodeInstance update = new JobDagNodeInstance();
      update.setId(nodeInstance.getId());
      update.setLogId(logId);
      dagNodeInstanceMapper.updateById(update);
    }
  }

  // ==================== 节点完成处理 ====================

  private void handleNodeCompletion(TaskCompletedEvent event) {
    // 查询是否有 PENDING/RUNNING 状态的节点实例匹配此 jobId
    // 注意：一个 jobId 可能同时属于多个 DAG 实例（不同 DAG 定义包含同一任务）
    // 这里只处理最先匹配的一个（PENDING/RUNNING 状态）
    List<JobDagNodeInstance> candidates = findRunningNodesByJobId(event.jobId());
    if (candidates.isEmpty()) {
      return; // 非 DAG 节点，跳过
    }

    for (JobDagNodeInstance nodeInstance : candidates) {
      processNodeCompletion(nodeInstance, event);
    }
  }

  /**
   * 查询 PENDING/RUNNING 状态的节点实例。
   *
   * <p>P0-4 修复：使用 {@link JobDagNodeInstanceMapper#selectAllByDagInstanceAndJob} 返回 ALL 匹配实例（含 LOOP
   * iter 实例），避免 LOOP 场景下同一 jobId 的多个实例 仅返回一条导致部分 iter 完成事件丢失。
   *
   * <p>P1-P4 性能优化：原实现先查所有 RUNNING 实例再逐实例查节点，复杂度 O(D×N)（D=运行中实例数）。
   * 改为单条 SQL 按 job_id 过滤活跃节点（PENDING/RUNNING），复杂度 O(1)。PAUSED 实例的 PENDING 节点
   * 由 {@link #processNodeCompletion} 的实例状态检查兜底跳过，语义与原实现一致。
   */
  private List<JobDagNodeInstance> findRunningNodesByJobId(String jobId) {
    List<JobDagNodeInstance> activeNodes = dagNodeInstanceMapper.selectActiveByJobId(jobId);
    if (activeNodes.isEmpty()) {
      return List.of();
    }
    return activeNodes.stream()
        .filter(
            ni ->
                ni != null
                    && (DagNodeStatus.PENDING.name().equals(ni.getNodeStatus())
                        || DagNodeStatus.RUNNING.name().equals(ni.getNodeStatus())))
        .toList();
  }

  private void processNodeCompletion(JobDagNodeInstance nodeInstance, TaskCompletedEvent event) {
    String dagInstanceId = nodeInstance.getDagInstanceId();
    DagNodeStatus finalStatus = event.success() ? DagNodeStatus.SUCCESS : DagNodeStatus.FAILED;
    LocalDateTime now = LocalDateTime.now();
    long durationMs =
        nodeInstance.getStartedAt() != null
            ? ChronoUnit.MILLIS.between(nodeInstance.getStartedAt(), now)
            : 0;

    // P2-5: 通过 logId 查询 JobLog 获取节点执行结果
    String nodeResultJson = null;
    if (event.success() && event.logId() != null) {
      try {
        JobLog jobLog = jobLogMapper.selectById(event.logId());
        if (jobLog != null) {
          nodeResultJson = jobLog.getResultJson();
        }
      } catch (Exception e) {
        log.warn(
            "[DagInstance] 查询节点执行结果异常, 忽略上下文合并: logId={} reason={}", event.logId(), e.getMessage());
      }
    }

    // 更新节点状态（含 resultJson）
    int updated =
        dagNodeInstanceMapper.markFinished(
            nodeInstance.getId(),
            finalStatus.name(),
            now,
            durationMs,
            nodeResultJson,
            event.success() ? null : "任务执行失败",
            event.logId());

    // P0-4: markFinished 返回 0 表示节点状态非 RUNNING（CAS 失败）。
    // 典型场景：LOOP 原始 body 节点处于 PENDING（doExecute 创建但从未 dispatch），
    // 被 findRunningNodesByJobId 选中后进入本方法。此时跳过后续处理，避免误触发后继。
    if (updated == 0) {
      log.debug(
          "[DagInstance] 节点状态非 RUNNING, CAS 失败跳过完成处理: instanceId={} jobKey={} currentStatus={}",
          dagInstanceId,
          nodeInstance.getJobKey(),
          nodeInstance.getNodeStatus());
      return;
    }

    log.info(
        "[DagInstance] 节点完成: instanceId={} jobKey={} status={}",
        dagInstanceId,
        nodeInstance.getJobKey(),
        finalStatus);

    // P2-5: 节点成功时，将结果合并到 DAG 实例级上下文
    if (event.success() && nodeResultJson != null) {
      mergeNodeResultToContext(dagInstanceId, nodeInstance.getJobKey(), nodeResultJson);
    }

    // 加载 DAG 实例和定义
    JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
    if (instance == null || !DagInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
      // P1-4: 实例非 RUNNING 状态（如 PAUSED/CANCELED），不触发后继
      log.info(
          "[DagInstance] 实例非 RUNNING 状态, 不触发后继: instanceId={} status={}",
          dagInstanceId,
          instance == null ? "null" : instance.getStatus());
      return;
    }
    JobDag dag = dagMapper.selectById(instance.getDagId());
    if (dag == null) {
      return;
    }
    DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());

    if (event.success()) {
      // 节点成功：触发后继
      triggerSuccessors(dagInstanceId, instance.getDagId(), nodeInstance.getJobKey(), definition);
    } else {
      // 节点失败：根据 DAG 级失败策略处理（P2-6 增强）
      DagFailureStrategy dagStrategy = DagFailureStrategy.parse(dag.getFailStrategy());
      handleNodeFailure(dagInstanceId, instance.getDagId(), nodeInstance, definition, dagStrategy);
    }

    // 检查是否所有节点完成
    finalizeInstance(dagInstanceId);
  }

  /**
   * P2-6: 节点失败时的策略处理。
   *
   * <p>支持四种 DAG 级失败策略：
   *
   * <ul>
   *   <li>{@link DagFailureStrategy#RETRY}：若 retryCount &lt; maxRetries，重置为 PENDING 并重新派发； 否则按
   *       {@link DagFailureStrategy#ABORT} 处理
   *   <li>{@link DagFailureStrategy#ABORT}：标记所有未完成节点为 SKIPPED
   *   <li>{@link DagFailureStrategy#SKIP_SUBSEQUENT}：仅跳过失败节点的直接后继，其他分支继续
   *   <li>{@link DagFailureStrategy#CONTINUE}：仍触发后继（通知/清理类）
   * </ul>
   */
  private void handleNodeFailure(
      String dagInstanceId,
      String dagId,
      JobDagNodeInstance nodeInstance,
      DagDefinition definition,
      DagFailureStrategy dagStrategy) {
    String jobKey = nodeInstance.getJobKey();
    // P2-6: RETRY 策略优先处理
    if (dagStrategy == DagFailureStrategy.RETRY) {
      if (tryRetryNode(nodeInstance, definition)) {
        log.info(
            "[DagInstance] RETRY 重试节点: instanceId={} jobKey={} retry={}/{}",
            dagInstanceId,
            jobKey,
            nodeInstance.getRetryCount() + 1,
            nodeInstance.getMaxRetries());
        return; // 重试中，不触发后继也不 finalize
      }
      // 重试次数用尽，降级为 ABORT
      log.info(
          "[DagInstance] RETRY 重试次数用尽, 按 ABORT 处理: instanceId={} jobKey={}", dagInstanceId, jobKey);
      skipPendingNodes(dagInstanceId);
      return;
    }

    if (dagStrategy == DagFailureStrategy.ABORT) {
      skipPendingNodes(dagInstanceId);
      log.info("[DagInstance] ABORT, 跳过未完成节点: instanceId={}", dagInstanceId);
    } else if (dagStrategy == DagFailureStrategy.SKIP_SUBSEQUENT) {
      // P2-6: 仅跳过失败节点的直接后继（递归跳过后继的后继）
      skipSubsequentNodes(dagInstanceId, jobKey, definition);
      log.info(
          "[DagInstance] SKIP_SUBSEQUENT, 跳过失败节点后继: instanceId={} jobKey={}",
          dagInstanceId,
          jobKey);
    } else {
      // CONTINUE: 仍然触发后继（仅 CONTINUE 边级策略的边触发）
      triggerSuccessors(dagInstanceId, dagId, jobKey, definition, false);
    }
  }

  /**
   * P2-6: 尝试重试节点。
   *
   * <p>P0-3 修复：当节点实例的 jobKey 带 LOOP iter 后缀（{@code #loop<i>}）时， {@link DagDefinition#findNode}
   * 无法匹配（DAG 定义中只有原始 jobKey）。 修复方案：先尝试原始 jobKey 查找；若失败则去除 {@code #loop} 后缀后重试。 这样即使 LOOP iter
   * 实例意外进入重试路径（理论上 P0-4 修复后不会发生）， 也能正确找到 DagNode 并重新派发，避免 markRetry 成功但节点卡死在 PENDING。
   *
   * @return true 表示重试已触发；false 表示重试次数用尽
   */
  private boolean tryRetryNode(JobDagNodeInstance nodeInstance, DagDefinition definition) {
    int updated = dagNodeInstanceMapper.markRetry(nodeInstance.getId());
    if (updated == 0) {
      return false; // 重试次数用尽或状态非 FAILED
    }
    // 重新查询节点实例获取最新状态（retryCount 已递增）
    JobDagNodeInstance refreshed = dagNodeInstanceMapper.selectById(nodeInstance.getId());
    if (refreshed == null) {
      return false;
    }
    // P0-3: 优先使用原始 jobKey 查找 DagNode；LOOP iter 后缀场景下去缀后重试
    DagNode node = definition.findNode(refreshed.getJobKey());
    if (node == null && isLoopIterJobKey(refreshed.getJobKey())) {
      String strippedKey = stripLoopSuffix(refreshed.getJobKey());
      node = definition.findNode(strippedKey);
      log.debug(
          "[DagInstance] RETRY 使用去缀 jobKey 查找 DagNode: original={} stripped={}",
          refreshed.getJobKey(),
          strippedKey);
    }
    if (node == null) {
      // markRetry 已成功但 findNode 失败，回滚节点状态避免卡死 PENDING
      log.warn(
          "[DagInstance] RETRY findNode 失败, 节点状态异常: instanceId={} jobKey={}",
          refreshed.getDagInstanceId(),
          refreshed.getJobKey());
      return false;
    }
    dispatchNode(refreshed.getDagInstanceId(), refreshed.getDagId(), node, definition);
    return true;
  }

  /**
   * P2-6: 跳过失败节点的所有直接后继（递归跳过后继的后继）。
   *
   * <p>与 {@link #skipPendingNodes} 的区别：本方法只跳过失败节点的后继链路，不影响其他分支的 PENDING 节点。
   */
  private void skipSubsequentNodes(
      String dagInstanceId, String failedJobKey, DagDefinition definition) {
    List<DagEdge> outgoing = definition.outgoingEdges(failedJobKey);
    for (DagEdge edge : outgoing) {
      skipNodeAndSubsequent(dagInstanceId, edge.to(), definition);
    }
  }

  /** 递归跳过指定节点及其后继（仅 PENDING 状态才跳过）。 */
  private void skipNodeAndSubsequent(
      String dagInstanceId, String jobKey, DagDefinition definition) {
    DagNode node = definition.findNode(jobKey);
    if (node == null) {
      return;
    }
    JobDagNodeInstance nodeInstance =
        dagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, node.jobId());
    if (nodeInstance != null && DagNodeStatus.PENDING.name().equals(nodeInstance.getNodeStatus())) {
      dagNodeInstanceMapper.markSkipped(nodeInstance.getId());
      log.debug(
          "[DagInstance] SKIP_SUBSEQUENT 跳过节点: instanceId={} jobKey={}", dagInstanceId, jobKey);
    }
    // 递归跳过后继
    for (DagEdge edge : definition.outgoingEdges(jobKey)) {
      skipNodeAndSubsequent(dagInstanceId, edge.to(), definition);
    }
  }

  /**
   * P2-6: 从 Job 读取 maxRetries（节点级重试上限）。
   *
   * @return Job.maxRetries；任务不存在或为 null 返回 0
   */
  private int resolveNodeMaxRetries(String jobId) {
    try {
      Job job = jobMapper.selectById(jobId);
      if (job != null && job.getMaxRetries() != null) {
        return job.getMaxRetries();
      }
    } catch (Exception e) {
      log.warn("[DagInstance] 读取 maxRetries 异常, 默认 0: jobId={} reason={}", jobId, e.getMessage());
    }
    return 0;
  }

  /**
   * 触发指定节点的后继节点（仅当后继的所有前置都成功时才派发）。
   *
   * <p>P2-6: 支持边级失败策略。当前置节点成功时，所有边都触发； 当前置节点失败时（CONTINUE_ON_FAIL 场景），仅边级策略为 CONTINUE_ON_FAIL 的边才触发。
   */
  private void triggerSuccessors(
      String dagInstanceId, String dagId, String completedJobKey, DagDefinition definition) {
    triggerSuccessors(dagInstanceId, dagId, completedJobKey, definition, true);
  }

  /**
   * 触发指定节点的后继节点（带前置成功标志，支持边级策略）。
   *
   * @param predecessorSuccess 前置节点是否成功；false 时仅 CONTINUE_ON_FAIL 边触发
   */
  private void triggerSuccessors(
      String dagInstanceId,
      String dagId,
      String completedJobKey,
      DagDefinition definition,
      boolean predecessorSuccess) {
    List<DagEdge> outgoing = definition.outgoingEdges(completedJobKey);
    for (DagEdge edge : outgoing) {
      DagNode successor = definition.findNode(edge.to());
      if (successor == null) {
        continue;
      }
      // P2-6: 边级失败策略判断
      if (!predecessorSuccess) {
        DagFailureStrategy edgeStrategy = edge.resolveFailStrategy();
        if (!edgeStrategy.shouldTriggerOnFailure()) {
          log.debug(
              "[DagInstance] 边级策略不触发后继: instanceId={} edge={}→{} strategy={}",
              dagInstanceId,
              edge.from(),
              edge.to(),
              edgeStrategy);
          continue;
        }
      }
      // 检查后继的所有前置是否都成功（CONTINUE_ON_FAIL 场景下，失败的前置也算"完成"）
      if (areAllPredecessorsSuccessful(dagInstanceId, edge.to(), definition)) {
        dispatchNode(dagInstanceId, dagId, successor, definition);
      }
    }
  }

  /**
   * 检查指定节点的所有前置节点是否都成功完成。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey 当前节点 jobKey
   * @param definition DAG 定义
   * @return true 表示所有前置节点均已成功完成
   */
  private boolean areAllPredecessorsSuccessful(
      String dagInstanceId, String jobKey, DagDefinition definition) {
    List<DagEdge> incoming = definition.incomingEdges(jobKey);
    if (incoming.isEmpty()) {
      return true;
    }
    for (DagEdge edge : incoming) {
      DagNode predDagNode = definition.findNode(edge.from());
      JobDagNodeInstance predNode =
          dagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, predDagNode.jobId());
      if (predNode == null || !DagNodeStatus.SUCCESS.name().equals(predNode.getNodeStatus())) {
        return false;
      }
    }
    return true;
  }

  /** 将所有 PENDING 状态的节点标记为 SKIPPED。 */
  private void skipPendingNodes(String dagInstanceId) {
    List<JobDagNodeInstance> nodes = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
    for (JobDagNodeInstance node : nodes) {
      if (DagNodeStatus.PENDING.name().equals(node.getNodeStatus())) {
        dagNodeInstanceMapper.markSkipped(node.getId());
      }
    }
  }

  // ==================== DAG 实例终态处理 ====================

  /** 检查 DAG 实例是否所有节点都已完成，如是则更新终态。 */
  private void finalizeInstance(String dagInstanceId) {
    List<JobDagNodeInstance> nodes = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
    if (nodes.isEmpty()) {
      return;
    }
    int total = nodes.size();
    int success = 0, failed = 0, skipped = 0, pending = 0, running = 0;
    for (JobDagNodeInstance node : nodes) {
      DagNodeStatus st = DagNodeStatus.parse(node.getNodeStatus());
      if (st == null) continue;
      switch (st) {
        case SUCCESS -> success++;
        case FAILED, APPROVAL_REJECTED -> failed++;
        case SKIPPED -> skipped++;
        case PENDING, WAITING_FOR_APPROVAL -> pending++;
        case RUNNING -> running++;
        case RETRYING -> pending++;
      }
    }
    // 还有未完成的节点，不结束
    if (pending > 0 || running > 0) {
      return;
    }

    // 所有节点完成，确定 DAG 终态
    DagInstanceStatus finalStatus;
    String errorMessage = null;
    if (failed == 0 && skipped == 0) {
      finalStatus = DagInstanceStatus.SUCCESS;
    } else if (success == 0) {
      finalStatus = DagInstanceStatus.FAILED;
      errorMessage = "所有节点执行失败";
    } else {
      finalStatus = DagInstanceStatus.PARTIAL_SUCCESS;
      errorMessage = "部分节点失败: failed=" + failed + " skipped=" + skipped;
    }

    LocalDateTime now = LocalDateTime.now();
    JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
    long durationMs =
        instance != null && instance.getStartedAt() != null
            ? ChronoUnit.MILLIS.between(instance.getStartedAt(), now)
            : 0;

    dagInstanceMapper.markFinished(
        dagInstanceId,
        finalStatus.name(),
        now,
        durationMs,
        errorMessage,
        total,
        success,
        failed,
        skipped);

    // 更新 DAG 定义的统计计数
    if (instance != null) {
      dagMapper.updateResultStats(instance.getDagId(), finalStatus == DagInstanceStatus.SUCCESS);
    }
    log.info(
        "[DagInstance] 执行完成: instanceId={} status={} total={} success={} failed={} skipped={} durationMs={}",
        dagInstanceId,
        finalStatus,
        total,
        success,
        failed,
        skipped,
        durationMs);
  }

  private void markInstanceFailed(String dagInstanceId, String errorMessage) {
    try {
      JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
      if (instance == null) return;
      LocalDateTime now = LocalDateTime.now();
      long durationMs =
          instance.getStartedAt() != null
              ? ChronoUnit.MILLIS.between(instance.getStartedAt(), now)
              : 0;
      dagInstanceMapper.markFinished(
          dagInstanceId,
          DagInstanceStatus.FAILED.name(),
          now,
          durationMs,
          errorMessage,
          0,
          0,
          0,
          0);
    } catch (Exception e) {
      log.error("[DagInstance] 标记实例 FAILED 异常: instanceId={}", dagInstanceId, e);
    }
  }

  private void markNodeFailed(String dagInstanceId, String jobKey, String errorMessage) {
    JobDagNodeInstance node =
        dagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, jobKey);
    if (node == null) return;
    LocalDateTime now = LocalDateTime.now();
    long durationMs =
        node.getStartedAt() != null ? ChronoUnit.MILLIS.between(node.getStartedAt(), now) : 0;
    dagNodeInstanceMapper.markFinished(
        node.getId(), DagNodeStatus.FAILED.name(), now, durationMs, null, errorMessage, null);
  }

  private void markNodeSkipped(String dagInstanceId, String jobKey) {
    JobDagNodeInstance node =
        dagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, jobKey);
    if (node == null) return;
    dagNodeInstanceMapper.markSkipped(node.getId());
  }

  // ==================== P2-5: 跨节点上下文传递 ====================

  /**
   * 将节点执行结果合并到 DAG 实例级上下文（contextJson）。
   *
   * <p>P0-1 并发安全修复：使用 PostgreSQL {@code jsonb ||} 操作符在 DB 层面原子合并， 消除 read-modify-write
   * 竞态。并行网关（PARALLEL_GATEWAY）多分支同时写 contextJson 时不再丢失数据。
   *
   * <p>合并策略：构造 {@code {"jobKey": nodeResult}} 片段，通过 {@link JobDagInstanceMapper#mergeContextAtomic}
   * 原子写入。 相同 jobKey 的后写覆盖先写（重试场景），不同 jobKey 各自保留。
   *
   * @param dagInstanceId DAG 实例 ID
   * @param jobKey 节点 jobKey（作为 contextJson 的 key）
   * @param nodeResultJson 节点执行结果 JSON
   */
  private void mergeNodeResultToContext(
      String dagInstanceId, String jobKey, String nodeResultJson) {
    try {
      // 构造待合并的 JSON 片段: {"jobKey": <nodeResult>}
      Object parsed;
      try {
        parsed = YdszJson.parseObject(nodeResultJson);
      } catch (Exception parseEx) {
        parsed = nodeResultJson;
      }
      ObjectNode mergeFragment = new ObjectNode();
      mergeFragment.put(jobKey, parsed);
      String mergeJson = YdszJson.toJson(mergeFragment);

      // 使用 PostgreSQL jsonb || 原子合并，消除 read-modify-write 竞态
      dagInstanceMapper.mergeContextAtomic(dagInstanceId, mergeJson);
      log.debug("[DagInstance] 上下文原子合并: instanceId={} jobKey={}", dagInstanceId, jobKey);
    } catch (Exception e) {
      log.warn(
          "[DagInstance] 上下文合并异常, 不影响主流程: instanceId={} jobKey={} reason={}",
          dagInstanceId,
          jobKey,
          e.getMessage());
    }
  }

  /** 解析 contextJson，空值或异常时返回空 ObjectNode。 */
  private ObjectNode parseContextJson(String contextJson) {
    if (contextJson == null || contextJson.isBlank()) {
      return new ObjectNode();
    }
    try {
      ObjectNode parsed = YdszJson.parseObject(contextJson);
      if (parsed != null) {
        return parsed;
      }
    } catch (Exception ignored) {
      // contextJson 非法时返回空对象，避免覆盖
    }
    return new ObjectNode();
  }

  /** 判断 jobKey 是否携带 LOOP iter 后缀（{@code #loop<i>}，历史 LOOP 节点生成的迭代实例）。 */
  private boolean isLoopIterJobKey(String jobKey) {
    return jobKey != null && jobKey.contains("#loop");
  }

  /** 去除 LOOP iter 后缀返回原始 jobKey；无后缀时原样返回。 */
  private String stripLoopSuffix(String jobKey) {
    if (jobKey == null) {
      return null;
    }
    int idx = jobKey.indexOf("#loop");
    return idx > 0 ? jobKey.substring(0, idx) : jobKey;
  }

  /**
   * P2-5: 获取 DAG 实例级上下文（供业务侧查询跨节点传递的参数）。
   *
   * <p>业务侧可在节点执行时调用本方法获取上游节点的执行结果：
   *
   * <pre>{@code
   * Map<String, Object> context = dagInstanceExecutor.getDagContext(dagInstanceId);
   * Object upstreamResult = context.get("upstreamJobKey");
   * }</pre>
   *
   * @param dagInstanceId DAG 实例 ID
   * @return 上下文 JSON 对象（不可变副本）；实例不存在或无上下文返回空对象
   */
  public Map<String, Object> getDagContext(String dagInstanceId) {
    JobDagInstance instance = dagInstanceMapper.selectById(dagInstanceId);
    if (instance == null) {
      return new LinkedHashMap<>();
    }
    ObjectNode parsed = parseContextJson(instance.getContextJson());
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) parsed.asValue();
    return result;
  }
}
