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
import com.njydsz.cronjob.domain.repository.DagInstanceRepository;
import com.njydsz.cronjob.domain.repository.DagNodeInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobLogRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;
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
 * <p>节点执行结果写入 DAG 实例级上下文（{@code contextJson}），后继节点可通过
 * {@link DagInstanceRepository#mergeContextAtomic} 读取。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DagInstanceExecutor {

  private final DagInstanceRepository dagInstanceRepository;
  private final DagNodeInstanceRepository dagNodeInstanceRepository;
  private final JobDagRepository jobDagRepository;
  private final JobRepository jobRepository;
  private final JobLogRepository jobLogRepository;
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
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      log.warn("[DagInstance] 重投递失败, 实例不存在: instanceId={}", dagInstanceId);
      return 0;
    }
    JobDagVO dag = jobDagRepository.findById(instance.getDagId()).orElse(null);
    if (dag == null) {
      log.warn("[DagInstance] 重投递失败, DAG 定义不存在: instanceId={} dagId={}", dagInstanceId, instance.getDagId());
      return 0;
    }
    DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    int dispatched = 0;
    for (JobDagNodeInstanceVO node : nodes) {
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
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      log.warn("[DagInstance] 实例不存在: instanceId={}", dagInstanceId);
      return;
    }
    JobDagVO dag = jobDagRepository.findById(instance.getDagId()).orElse(null);
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
    int updated = dagInstanceRepository.markRunning(dagInstanceId, LocalDateTime.now());
    if (updated == 0) {
      log.warn("[DagInstance] 实例非 PENDING 状态, 跳过执行: instanceId={}", dagInstanceId);
      return;
    }

    // 创建节点实例
    List<DagNode> nodes = definition.nodes();
    for (DagNode node : nodes) {
      JobDagNodeInstanceVO nodeInstance = new JobDagNodeInstanceVO();
      nodeInstance.setDagInstanceId(dagInstanceId);
      nodeInstance.setDagId(instance.getDagId());
      nodeInstance.setJobId(node.jobId());
      nodeInstance.setJobKey(node.jobKey());
      nodeInstance.setNodeStatus(DagNodeStatus.PENDING.name());
      nodeInstance.setRetryCount(0);
      // P2-6: 从 Job 读取 maxRetries，支持 RETRY 失败策略
      nodeInstance.setMaxRetries(resolveNodeMaxRetries(node.jobId()));
      nodeInstance.setTenantId(instance.getTenantId());
      dagNodeInstanceRepository.insert(nodeInstance);
    }

    // 更新总节点数
    JobDagInstanceVO update = new JobDagInstanceVO();
    update.setId(dagInstanceId);
    update.setTotalNodes(nodes.size());
    update.setSuccessNodes(0);
    update.setFailedNodes(0);
    update.setSkippedNodes(0);
    dagInstanceRepository.update(update);

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
    JobVO job = jobRepository.findById(node.jobId()).orElse(null);
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
    JobDagNodeInstanceVO nodeInstance =
        dagNodeInstanceRepository.findByDagInstanceAndJob(dagInstanceId, node.jobId());
    if (nodeInstance == null) {
      log.warn("[DagInstance] 节点实例不存在: instanceId={} jobId={}", dagInstanceId, node.jobId());
      return;
    }
    dagNodeInstanceRepository.markRunning(nodeInstance.getId(), LocalDateTime.now());

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
      JobDagNodeInstanceVO update = new JobDagNodeInstanceVO();
      update.setId(nodeInstance.getId());
      update.setLogId(logId);
      dagNodeInstanceRepository.updateById(update);
    }
  }

  // ==================== 节点完成处理 ====================

  private void handleNodeCompletion(TaskCompletedEvent event) {
    // 查询是否有 PENDING/RUNNING 状态的节点实例匹配此 jobId
    // 注意：一个 jobId 可能同时属于多个 DAG 实例（不同 DAG 定义包含同一任务）
    // 这里只处理最先匹配的一个（PENDING/RUNNING 状态）
    List<JobDagNodeInstanceVO> candidates = findRunningNodesByJobId(event.jobId());
    if (candidates.isEmpty()) {
      return; // 非 DAG 节点，跳过
    }

    for (JobDagNodeInstanceVO nodeInstance : candidates) {
      processNodeCompletion(nodeInstance, event);
    }
  }

  /**
   * 查询 PENDING/RUNNING 状态的节点实例。
   *
   * <p>P1-P4 性能优化：单条 SQL 按 job_id 过滤活跃节点（PENDING/RUNNING），复杂度 O(1)。
   *
   * @param jobId 任务 ID
   * @return 活跃状态的节点实例列表
   */
  private List<JobDagNodeInstanceVO> findRunningNodesByJobId(String jobId) {
    List<JobDagNodeInstanceVO> activeNodes = dagNodeInstanceRepository.selectActiveByJobId(jobId);
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

  private void processNodeCompletion(JobDagNodeInstanceVO nodeInstance, TaskCompletedEvent event) {
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
        JobLogVO jobLog = jobLogRepository.findById(event.logId()).orElse(null);
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
        dagNodeInstanceRepository.markFinished(
            nodeInstance.getId(),
            finalStatus.name(),
            now,
            durationMs,
            nodeResultJson,
            event.success() ? null : "任务执行失败",
            event.logId());

    // P0-4: markFinished 返回 0 表示节点状态非 RUNNING（CAS 失败）。
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
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null || !DagInstanceStatus.RUNNING.name().equals(instance.getInstanceStatus())) {
      // P1-4: 实例非 RUNNING 状态（如 PAUSED/CANCELED），不触发后继
      log.info(
          "[DagInstance] 实例非 RUNNING 状态, 不触发后继: instanceId={} status={}",
          dagInstanceId,
          instance == null ? "null" : instance.getInstanceStatus());
      return;
    }
    JobDagVO dag = jobDagRepository.findById(instance.getDagId()).orElse(null);
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
   * <p>支持四种 DAG 级失败策略：RETRY / ABORT / SKIP_SUBSEQUENT / CONTINUE。
   */
  private void handleNodeFailure(
      String dagInstanceId,
      String dagId,
      JobDagNodeInstanceVO nodeInstance,
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
        return;
      }
      // 重试次数用尽，降级为 ABORT
      log.info("[DagInstance] RETRY 重试次数用尽, 按 ABORT 处理: instanceId={} jobKey={}", dagInstanceId, jobKey);
      skipPendingNodes(dagInstanceId);
      return;
    }

    if (dagStrategy == DagFailureStrategy.ABORT) {
      skipPendingNodes(dagInstanceId);
      log.info("[DagInstance] ABORT, 跳过未完成节点: instanceId={}", dagInstanceId);
    } else if (dagStrategy == DagFailureStrategy.SKIP_SUBSEQUENT) {
      skipSubsequentNodes(dagInstanceId, jobKey, definition);
      log.info(
          "[DagInstance] SKIP_SUBSEQUENT, 跳过失败节点后继: instanceId={} jobKey={}",
          dagInstanceId,
          jobKey);
    } else {
      // CONTINUE: 仍然触发后继
      triggerSuccessors(dagInstanceId, dagId, jobKey, definition, false);
    }
  }

  private boolean tryRetryNode(JobDagNodeInstanceVO nodeInstance, DagDefinition definition) {
    int updated = dagNodeInstanceRepository.markRetry(nodeInstance.getId());
    if (updated == 0) {
      return false;
    }
    // 重新查询节点实例获取最新状态（retryCount 已递增）
    JobDagNodeInstanceVO refreshed = dagNodeInstanceRepository.findById(nodeInstance.getId());
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
      log.warn(
          "[DagInstance] RETRY findNode 失败, 节点状态异常: instanceId={} jobKey={}",
          refreshed.getDagInstanceId(),
          refreshed.getJobKey());
      return false;
    }
    dispatchNode(refreshed.getDagInstanceId(), refreshed.getDagId(), node, definition);
    return true;
  }

  private void skipSubsequentNodes(
      String dagInstanceId, String failedJobKey, DagDefinition definition) {
    List<DagEdge> outgoing = definition.outgoingEdges(failedJobKey);
    for (DagEdge edge : outgoing) {
      skipNodeAndSubsequent(dagInstanceId, edge.to(), definition);
    }
  }

  private void skipNodeAndSubsequent(
      String dagInstanceId, String jobKey, DagDefinition definition) {
    DagNode node = definition.findNode(jobKey);
    if (node == null) {
      return;
    }
    JobDagNodeInstanceVO nodeInstance =
        dagNodeInstanceRepository.findByDagInstanceAndJobKey(dagInstanceId, jobKey);
    if (nodeInstance != null && DagNodeStatus.PENDING.name().equals(nodeInstance.getNodeStatus())) {
      dagNodeInstanceRepository.markSkipped(nodeInstance.getId());
      log.debug("[DagInstance] SKIP_SUBSEQUENT 跳过节点: instanceId={} jobKey={}", dagInstanceId, jobKey);
    }
    // 递归跳过后继
    for (DagEdge edge : definition.outgoingEdges(jobKey)) {
      skipNodeAndSubsequent(dagInstanceId, edge.to(), definition);
    }
  }

  private int resolveNodeMaxRetries(String jobId) {
    try {
      JobVO job = jobRepository.findById(jobId).orElse(null);
      if (job != null && job.getMaxRetries() != null) {
        return job.getMaxRetries();
      }
    } catch (Exception e) {
      log.warn("[DagInstance] 读取 maxRetries 异常, 默认 0: jobId={} reason={}", jobId, e.getMessage());
    }
    return 0;
  }

  private void triggerSuccessors(
      String dagInstanceId, String dagId, String completedJobKey, DagDefinition definition) {
    triggerSuccessors(dagInstanceId, dagId, completedJobKey, definition, true);
  }

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
      if (areAllPredecessorsSuccessful(dagInstanceId, edge.to(), definition)) {
        dispatchNode(dagInstanceId, dagId, successor, definition);
      }
    }
  }

  private boolean areAllPredecessorsSuccessful(
      String dagInstanceId, String jobKey, DagDefinition definition) {
    List<DagEdge> incoming = definition.incomingEdges(jobKey);
    if (incoming.isEmpty()) {
      return true;
    }
    for (DagEdge edge : incoming) {
      DagNode predDagNode = definition.findNode(edge.from());
      JobDagNodeInstanceVO predNode =
          dagNodeInstanceRepository.findByDagInstanceAndJob(dagInstanceId, predDagNode.jobId());
      if (predNode == null || !DagNodeStatus.SUCCESS.name().equals(predNode.getNodeStatus())) {
        return false;
      }
    }
    return true;
  }

  private void skipPendingNodes(String dagInstanceId) {
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    for (JobDagNodeInstanceVO node : nodes) {
      if (DagNodeStatus.PENDING.name().equals(node.getNodeStatus())) {
        dagNodeInstanceRepository.markSkipped(node.getId());
      }
    }
  }

  // ==================== DAG 实例终态处理 ====================

  private void finalizeInstance(String dagInstanceId) {
    List<JobDagNodeInstanceVO> nodes = dagNodeInstanceRepository.findByDagInstanceId(dagInstanceId);
    if (nodes.isEmpty()) {
      return;
    }
    int total = nodes.size();
    int success = 0, failed = 0, skipped = 0, pending = 0, running = 0;
    for (JobDagNodeInstanceVO node : nodes) {
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
    if (pending > 0 || running > 0) {
      return;
    }

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
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    long durationMs =
        instance != null && instance.getStartedAt() != null
            ? ChronoUnit.MILLIS.between(instance.getStartedAt(), now)
            : 0;

    dagInstanceRepository.markFinished(
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
      dagInstanceRepository.updateResultStats(instance.getDagId(), finalStatus == DagInstanceStatus.SUCCESS);
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
      JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
      if (instance == null) return;
      LocalDateTime now = LocalDateTime.now();
      long durationMs =
          instance.getStartedAt() != null
              ? ChronoUnit.MILLIS.between(instance.getStartedAt(), now)
              : 0;
      dagInstanceRepository.markFinished(
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
    JobDagNodeInstanceVO node =
        dagNodeInstanceRepository.findByDagInstanceAndJobKey(dagInstanceId, jobKey);
    if (node == null) return;
    LocalDateTime now = LocalDateTime.now();
    long durationMs =
        node.getStartedAt() != null ? ChronoUnit.MILLIS.between(node.getStartedAt(), now) : 0;
    dagNodeInstanceRepository.markFinished(
        node.getId(), DagNodeStatus.FAILED.name(), now, durationMs, null, errorMessage, null);
  }

  private void markNodeSkipped(String dagInstanceId, String jobKey) {
    JobDagNodeInstanceVO node =
        dagNodeInstanceRepository.findByDagInstanceAndJobKey(dagInstanceId, jobKey);
    if (node == null) return;
    dagNodeInstanceRepository.markSkipped(node.getId());
  }

  // ==================== P2-5: 跨节点上下文传递 ====================

  private void mergeNodeResultToContext(
      String dagInstanceId, String jobKey, String nodeResultJson) {
    try {
      Object parsed;
      try {
        parsed = YdszJson.parseObject(nodeResultJson);
      } catch (Exception parseEx) {
        parsed = nodeResultJson;
      }
      ObjectNode mergeFragment = new ObjectNode();
      mergeFragment.put(jobKey, parsed);
      String mergeJson = YdszJson.toJson(mergeFragment);

      dagInstanceRepository.mergeContextAtomic(dagInstanceId, mergeJson);
      log.debug("[DagInstance] 上下文原子合并: instanceId={} jobKey={}", dagInstanceId, jobKey);
    } catch (Exception e) {
      log.warn(
          "[DagInstance] 上下文合并异常, 不影响主流程: instanceId={} jobKey={} reason={}",
          dagInstanceId,
          jobKey,
          e.getMessage());
    }
  }

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
    }
    return new ObjectNode();
  }

  private boolean isLoopIterJobKey(String jobKey) {
    return jobKey != null && jobKey.contains("#loop");
  }

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
   * @param dagInstanceId DAG 实例 ID
   * @return 上下文 JSON 对象（不可变副本）；实例不存在或无上下文返回空对象
   */
  public Map<String, Object> getDagContext(String dagInstanceId) {
    JobDagInstanceVO instance = dagInstanceRepository.findById(dagInstanceId).orElse(null);
    if (instance == null) {
      return new LinkedHashMap<>();
    }
    ObjectNode parsed = parseContextJson(instance.getContextJson());
    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) parsed.asValue();
    return result;
  }
}
