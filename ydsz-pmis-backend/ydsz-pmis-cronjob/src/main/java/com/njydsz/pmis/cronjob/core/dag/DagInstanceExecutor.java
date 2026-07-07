package com.njydsz.pmis.cronjob.core.dag;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher;
import com.njydsz.pmis.cronjob.entity.JobDagDO;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagNodeInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * DAG 实例执行器（P2 DAG 增强）。
 *
 * <p>负责基于 DAG 定义（{@link DagDefinition}）执行 DAG 实例：
 * <ol>
 *   <li>{@link #execute(String)}：创建节点实例，派发起始节点（无入边）</li>
 *   <li>{@link #onTaskCompleted(TaskCompletedEvent)}：监听任务完成事件，
 *       通过查询节点实例表判断是否为 DAG 节点，更新节点状态并触发后继</li>
 *   <li>所有节点完成后，更新 DAG 实例终态（SUCCESS/FAILED/PARTIAL_SUCCESS）</li>
 * </ol>
 *
 * <h3>与 {@link DagExecutor} 的关系</h3>
 * <p>两者均监听 {@link TaskCompletedEvent}：
 * <ul>
 *   <li>{@code DagInstanceExecutor}：通过查询节点实例表判断是否为 DAG 节点，
 *       匹配则处理，不匹配则跳过（不影响 DagExecutor）</li>
 *   <li>{@code DagExecutor}：基于 {@code pmis_job_relation} 表触发后继，
 *       与 DAG 实例执行正交（建议 DAG 模式下不混用 JobRelation）</li>
 * </ul>
 *
 * <h3>跨节点上下文传递（P2-5）</h3>
 * <p>节点执行结果写入 DAG 实例级上下文（{@code contextJson}），
 * 后继节点可通过 {@link JobDagInstanceMapper#updateContext} 读取。
 *
 * @author ydsz-pmis-team
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
     * <ol>
     *   <li>加载 DAG 实例与定义</li>
     *   <li>标记实例为 RUNNING</li>
     *   <li>为每个节点创建节点实例（PENDING）</li>
     *   <li>派发所有起始节点（无入边）</li>
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
     * 监听任务完成事件，更新 DAG 节点状态并触发后继。
     *
     * <p>通过查询节点实例表判断是否为 DAG 节点：
     * <ul>
     *   <li>匹配 PENDING/RUNNING 状态的节点实例 → 是 DAG 节点，处理</li>
     *   <li>无匹配 → 非 DAG 节点，跳过（不影响 DagExecutor）</li>
     * </ul>
     */
    @Async
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        try {
            handleNodeCompletion(event);
        } catch (Exception e) {
            log.error("[DagInstance] 节点完成处理异常: jobId={} reason={}",
                    event.jobId(), e.getMessage(), e);
        }
    }

    // ==================== 核心执行逻辑 ====================

    private void doExecute(String dagInstanceId) {
        JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
        if (instance == null) {
            log.warn("[DagInstance] 实例不存在: instanceId={}", dagInstanceId);
            return;
        }
        JobDagDO dag = dagMapper.selectById(instance.getDagId());
        if (dag == null) {
            markInstanceFailed(dagInstanceId, "DAG 定义不存在");
            return;
        }
        DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());
        log.info("[DagInstance] 开始执行: instanceId={} dagKey={} nodes={} edges={}",
                dagInstanceId, dag.getDagKey(), definition.nodeCount(), definition.edges().size());

        // 标记 RUNNING
        int updated = dagInstanceMapper.markRunning(dagInstanceId, LocalDateTime.now());
        if (updated == 0) {
            log.warn("[DagInstance] 实例非 PENDING 状态, 跳过执行: instanceId={}", dagInstanceId);
            return;
        }

        // 创建节点实例
        List<DagNode> nodes = definition.nodes();
        for (DagNode node : nodes) {
            JobDagNodeInstanceDO nodeInstance = new JobDagNodeInstanceDO();
            nodeInstance.setDagInstanceId(dagInstanceId);
            nodeInstance.setDagId(instance.getDagId());
            nodeInstance.setJobId(node.jobId());
            nodeInstance.setJobKey(node.jobKey());
            nodeInstance.setNodeStatus(DagNodeStatus.PENDING.name());
            nodeInstance.setRetryCount(0);
            // P2-6: 从 JobDO 读取 maxRetries，支持 RETRY 失败策略
            nodeInstance.setMaxRetries(resolveNodeMaxRetries(node.jobId()));
            nodeInstance.setTenantId(instance.getTenantId());
            dagNodeInstanceMapper.insert(nodeInstance);
        }

        // 更新总节点数
        JobDagInstanceDO update = new JobDagInstanceDO();
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
     */
    private void dispatchNode(String dagInstanceId, String dagId, DagNode node, DagDefinition definition) {
        JobDO job = jobMapper.selectById(node.jobId());
        if (job == null) {
            log.warn("[DagInstance] 节点任务不存在, 标记 FAILED: instanceId={} jobKey={}",
                    dagInstanceId, node.jobKey());
            markNodeFailed(dagInstanceId, node.jobKey(), "任务不存在");
            return;
        }
        if (!"NORMAL".equals(job.getStatus())) {
            log.info("[DagInstance] 节点任务非 NORMAL 状态, 标记 SKIPPED: instanceId={} jobKey={} status={}",
                    dagInstanceId, node.jobKey(), job.getStatus());
            markNodeSkipped(dagInstanceId, node.jobKey());
            return;
        }

        // 标记节点 RUNNING
        JobDagNodeInstanceDO nodeInstance = dagNodeInstanceMapper.selectByDagInstanceAndJob(dagInstanceId, node.jobId());
        if (nodeInstance == null) {
            log.warn("[DagInstance] 节点实例不存在: instanceId={} jobId={}", dagInstanceId, node.jobId());
            return;
        }
        dagNodeInstanceMapper.markRunning(nodeInstance.getId(), LocalDateTime.now());

        // 派发任务（triggerType=DEPENDENT, 抢锁）
        String logId = taskDispatcher.dispatch(job, null, "DEPENDENT");
        log.info("[DagInstance] 节点派发: instanceId={} jobKey={} logId={}",
                dagInstanceId, node.jobKey(), logId);

        // 如果 dispatch 同步返回 logId 且任务已执行完成（MANUAL 触发同步执行），
        // 节点状态可能已经通过事件更新，这里不重复处理
        if (logId != null) {
            // 更新节点实例的 logId
            JobDagNodeInstanceDO update = new JobDagNodeInstanceDO();
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
        List<JobDagNodeInstanceDO> candidates = findRunningNodesByJobId(event.jobId());
        if (candidates.isEmpty()) {
            return; // 非 DAG 节点，跳过
        }

        for (JobDagNodeInstanceDO nodeInstance : candidates) {
            processNodeCompletion(nodeInstance, event);
        }
    }

    /**
     * 查询 PENDING/RUNNING 状态的节点实例。
     */
    private List<JobDagNodeInstanceDO> findRunningNodesByJobId(String jobId) {
        // 通过 BaseMapper 的 selectList + LambdaQueryWrapper 查询
        // 但为了简化，直接遍历所有 RUNNING DAG 实例的节点
        // 优化：添加专门的 Mapper 方法
        List<JobDagInstanceDO> runningInstances = dagInstanceMapper.selectByStatus(
                DagInstanceStatus.RUNNING.name());
        if (runningInstances.isEmpty()) {
            return List.of();
        }
        return runningInstances.stream()
                .map(inst -> dagNodeInstanceMapper.selectByDagInstanceAndJob(inst.getId(), jobId))
                .filter(ni -> ni != null && (DagNodeStatus.PENDING.name().equals(ni.getNodeStatus())
                        || DagNodeStatus.RUNNING.name().equals(ni.getNodeStatus())))
                .toList();
    }

    private void processNodeCompletion(JobDagNodeInstanceDO nodeInstance, TaskCompletedEvent event) {
        String dagInstanceId = nodeInstance.getDagInstanceId();
        DagNodeStatus finalStatus = event.success() ? DagNodeStatus.SUCCESS : DagNodeStatus.FAILED;
        LocalDateTime now = LocalDateTime.now();
        long durationMs = nodeInstance.getStartedAt() != null
                ? ChronoUnit.MILLIS.between(nodeInstance.getStartedAt(), now) : 0;

        // P2-5: 通过 logId 查询 JobLog 获取节点执行结果
        String nodeResultJson = null;
        if (event.success() && event.logId() != null) {
            try {
                JobLogDO jobLog = jobLogMapper.selectById(event.logId());
                if (jobLog != null) {
                    nodeResultJson = jobLog.getResultJson();
                }
            } catch (Exception e) {
                log.warn("[DagInstance] 查询节点执行结果异常, 忽略上下文合并: logId={} reason={}",
                        event.logId(), e.getMessage());
            }
        }

        // 更新节点状态（含 resultJson）
        dagNodeInstanceMapper.markFinished(nodeInstance.getId(),
                finalStatus.name(), now, durationMs, nodeResultJson,
                event.success() ? null : "任务执行失败", event.logId());

        log.info("[DagInstance] 节点完成: instanceId={} jobKey={} status={}",
                dagInstanceId, nodeInstance.getJobKey(), finalStatus);

        // P2-5: 节点成功时，将结果合并到 DAG 实例级上下文
        if (event.success() && nodeResultJson != null) {
            mergeNodeResultToContext(dagInstanceId, nodeInstance.getJobKey(), nodeResultJson);
        }

        // 加载 DAG 实例和定义
        JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
        if (instance == null || !DagInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            return;
        }
        JobDagDO dag = dagMapper.selectById(instance.getDagId());
        if (dag == null) {
            return;
        }
        DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());

        if (event.success()) {
            // 节点成功：触发后继
            triggerSuccessors(dagInstanceId, instance.getDagId(), nodeInstance.getJobKey(), definition);
        } else {
            // 节点失败：根据 DAG 级失败策略处理（P2-6 增强）
            FailStrategy dagStrategy = FailStrategy.parse(dag.getFailStrategy());
            handleNodeFailure(dagInstanceId, instance.getDagId(), nodeInstance, definition, dagStrategy);
        }

        // 检查是否所有节点完成
        finalizeInstance(dagInstanceId);
    }

    /**
     * P2-6: 节点失败时的策略处理。
     *
     * <p>支持四种 DAG 级失败策略：
     * <ul>
     *   <li>{@link FailStrategy#RETRY}：若 retryCount &lt; maxRetries，重置为 PENDING 并重新派发；
     *       否则按 {@link FailStrategy#FAIL_FAST} 处理</li>
     *   <li>{@link FailStrategy#FAIL_FAST}：标记所有未完成节点为 SKIPPED</li>
     *   <li>{@link FailStrategy#SKIP_SUBSEQUENT}：仅跳过失败节点的直接后继，其他分支继续</li>
     *   <li>{@link FailStrategy#CONTINUE_ON_FAIL}：仍触发后继（通知/清理类）</li>
     * </ul>
     */
    private void handleNodeFailure(String dagInstanceId, String dagId,
                                    JobDagNodeInstanceDO nodeInstance, DagDefinition definition,
                                    FailStrategy dagStrategy) {
        String jobKey = nodeInstance.getJobKey();
        // P2-6: RETRY 策略优先处理
        if (dagStrategy == FailStrategy.RETRY) {
            if (tryRetryNode(nodeInstance, definition)) {
                log.info("[DagInstance] RETRY 重试节点: instanceId={} jobKey={} retry={}/{}",
                        dagInstanceId, jobKey, nodeInstance.getRetryCount() + 1, nodeInstance.getMaxRetries());
                return; // 重试中，不触发后继也不 finalize
            }
            // 重试次数用尽，降级为 FAIL_FAST
            log.info("[DagInstance] RETRY 重试次数用尽, 按 FAIL_FAST 处理: instanceId={} jobKey={}",
                    dagInstanceId, jobKey);
            skipPendingNodes(dagInstanceId);
            return;
        }

        if (dagStrategy == FailStrategy.FAIL_FAST) {
            skipPendingNodes(dagInstanceId);
            log.info("[DagInstance] FAIL_FAST, 跳过未完成节点: instanceId={}", dagInstanceId);
        } else if (dagStrategy == FailStrategy.SKIP_SUBSEQUENT) {
            // P2-6: 仅跳过失败节点的直接后继（递归跳过后继的后继）
            skipSubsequentNodes(dagInstanceId, jobKey, definition);
            log.info("[DagInstance] SKIP_SUBSEQUENT, 跳过失败节点后继: instanceId={} jobKey={}",
                    dagInstanceId, jobKey);
        } else {
            // CONTINUE_ON_FAIL: 仍然触发后继（仅 CONTINUE_ON_FAIL 边级策略的边触发）
            triggerSuccessors(dagInstanceId, dagId, jobKey, definition, false);
        }
    }

    /**
     * P2-6: 尝试重试节点。
     *
     * @return true 表示重试已触发；false 表示重试次数用尽
     */
    private boolean tryRetryNode(JobDagNodeInstanceDO nodeInstance, DagDefinition definition) {
        int updated = dagNodeInstanceMapper.markRetry(nodeInstance.getId());
        if (updated == 0) {
            return false; // 重试次数用尽或状态非 FAILED
        }
        // 重新查询节点实例获取最新状态（retryCount 已递增）
        JobDagNodeInstanceDO refreshed = dagNodeInstanceMapper.selectById(nodeInstance.getId());
        if (refreshed == null) {
            return false;
        }
        // 重新派发该节点
        DagNode node = definition.findNode(refreshed.getJobKey());
        if (node == null) {
            return false;
        }
        dispatchNode(refreshed.getDagInstanceId(), refreshed.getDagId(), node, definition);
        return true;
    }

    /**
     * P2-6: 跳过失败节点的所有直接后继（递归跳过后继的后继）。
     *
     * <p>与 {@link #skipPendingNodes} 的区别：本方法只跳过失败节点的后继链路，
     * 不影响其他分支的 PENDING 节点。
     */
    private void skipSubsequentNodes(String dagInstanceId, String failedJobKey, DagDefinition definition) {
        // 使用 DagParser 的后代查询，递归跳过所有后继
        List<DagEdge> outgoing = definition.outgoingEdges(failedJobKey);
        for (DagEdge edge : outgoing) {
            skipNodeAndSubsequent(dagInstanceId, edge.to(), definition);
        }
    }

    /**
     * 递归跳过指定节点及其后继（仅 PENDING 状态才跳过）。
     */
    private void skipNodeAndSubsequent(String dagInstanceId, String jobKey, DagDefinition definition) {
        // 通过 jobKey 查找节点，再查节点实例
        DagNode node = definition.findNode(jobKey);
        if (node == null) {
            return;
        }
        JobDagNodeInstanceDO nodeInstance = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                dagInstanceId, node.jobId());
        if (nodeInstance != null && DagNodeStatus.PENDING.name().equals(nodeInstance.getNodeStatus())) {
            dagNodeInstanceMapper.markSkipped(nodeInstance.getId());
            log.debug("[DagInstance] SKIP_SUBSEQUENT 跳过节点: instanceId={} jobKey={}",
                    dagInstanceId, jobKey);
        }
        // 递归跳过后继
        for (DagEdge edge : definition.outgoingEdges(jobKey)) {
            skipNodeAndSubsequent(dagInstanceId, edge.to(), definition);
        }
    }

    /**
     * P2-6: 从 JobDO 读取 maxRetries（节点级重试上限）。
     *
     * @return JobDO.maxRetries；任务不存在或为 null 返回 0
     */
    private int resolveNodeMaxRetries(String jobId) {
        try {
            JobDO job = jobMapper.selectById(jobId);
            if (job != null && job.getMaxRetries() != null) {
                return job.getMaxRetries();
            }
        } catch (Exception e) {
            log.warn("[DagInstance] 读取 maxRetries 异常, 默认 0: jobId={} reason={}",
                    jobId, e.getMessage());
        }
        return 0;
    }

    /**
     * 触发指定节点的后继节点（仅当后继的所有前置都成功时才派发）。
     *
     * <p>P2-6: 支持边级失败策略。当前置节点成功时，所有边都触发；
     * 当前置节点失败时（CONTINUE_ON_FAIL 场景），仅边级策略为 CONTINUE_ON_FAIL 的边才触发。
     */
    private void triggerSuccessors(String dagInstanceId, String dagId, String completedJobKey,
                                    DagDefinition definition) {
        triggerSuccessors(dagInstanceId, dagId, completedJobKey, definition, true);
    }

    /**
     * 触发指定节点的后继节点（带前置成功标志，支持边级策略）。
     *
     * @param predecessorSuccess 前置节点是否成功；false 时仅 CONTINUE_ON_FAIL 边触发
     */
    private void triggerSuccessors(String dagInstanceId, String dagId, String completedJobKey,
                                    DagDefinition definition, boolean predecessorSuccess) {
        List<DagEdge> outgoing = definition.outgoingEdges(completedJobKey);
        for (DagEdge edge : outgoing) {
            DagNode successor = definition.findNode(edge.to());
            if (successor == null) {
                continue;
            }
            // P2-6: 边级失败策略判断
            if (!predecessorSuccess) {
                FailStrategy edgeStrategy = edge.resolveFailStrategy();
                if (!edgeStrategy.shouldTriggerOnFailure()) {
                    log.debug("[DagInstance] 边级策略不触发后继: instanceId={} edge={}→{} strategy={}",
                            dagInstanceId, edge.from(), edge.to(), edgeStrategy);
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
     */
    private boolean areAllPredecessorsSuccessful(String dagInstanceId, String jobKey,
                                                  DagDefinition definition) {
        List<DagEdge> incoming = definition.incomingEdges(jobKey);
        if (incoming.isEmpty()) {
            return true; // 无前置，可直接执行
        }
        for (DagEdge edge : incoming) {
            JobDagNodeInstanceDO predNode = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                    dagInstanceId, definition.findNode(edge.from()).jobId());
            if (predNode == null || !DagNodeStatus.SUCCESS.name().equals(predNode.getNodeStatus())) {
                return false; // 前置未成功完成
            }
        }
        return true;
    }

    /**
     * 将所有 PENDING 状态的节点标记为 SKIPPED。
     */
    private void skipPendingNodes(String dagInstanceId) {
        List<JobDagNodeInstanceDO> nodes = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
        for (JobDagNodeInstanceDO node : nodes) {
            if (DagNodeStatus.PENDING.name().equals(node.getNodeStatus())) {
                dagNodeInstanceMapper.markSkipped(node.getId());
            }
        }
    }

    // ==================== DAG 实例终态处理 ====================

    /**
     * 检查 DAG 实例是否所有节点都已完成，如是则更新终态。
     */
    private void finalizeInstance(String dagInstanceId) {
        List<JobDagNodeInstanceDO> nodes = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
        if (nodes.isEmpty()) {
            return;
        }
        int total = nodes.size();
        int success = 0, failed = 0, skipped = 0, pending = 0, running = 0;
        for (JobDagNodeInstanceDO node : nodes) {
            DagNodeStatus st = DagNodeStatus.parse(node.getNodeStatus());
            if (st == null) continue;
            switch (st) {
                case SUCCESS -> success++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
                case PENDING -> pending++;
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
        JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
        long durationMs = instance != null && instance.getStartedAt() != null
                ? ChronoUnit.MILLIS.between(instance.getStartedAt(), now) : 0;

        dagInstanceMapper.markFinished(dagInstanceId, finalStatus.name(), now, durationMs,
                errorMessage, total, success, failed, skipped);

        // 更新 DAG 定义的统计计数
        if (instance != null) {
            dagMapper.updateResultStats(instance.getDagId(),
                    finalStatus == DagInstanceStatus.SUCCESS);
        }
        log.info("[DagInstance] 执行完成: instanceId={} status={} total={} success={} failed={} skipped={} durationMs={}",
                dagInstanceId, finalStatus, total, success, failed, skipped, durationMs);
    }

    private void markInstanceFailed(String dagInstanceId, String errorMessage) {
        try {
            JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
            if (instance == null) return;
            LocalDateTime now = LocalDateTime.now();
            long durationMs = instance.getStartedAt() != null
                    ? ChronoUnit.MILLIS.between(instance.getStartedAt(), now) : 0;
            dagInstanceMapper.markFinished(dagInstanceId, DagInstanceStatus.FAILED.name(),
                    now, durationMs, errorMessage, 0, 0, 0, 0);
        } catch (Exception e) {
            log.error("[DagInstance] 标记实例 FAILED 异常: instanceId={}", dagInstanceId, e);
        }
    }

    private void markNodeFailed(String dagInstanceId, String jobKey, String errorMessage) {
        JobDagNodeInstanceDO node = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                dagInstanceId, jobKey);
        if (node == null) return;
        LocalDateTime now = LocalDateTime.now();
        long durationMs = node.getStartedAt() != null
                ? ChronoUnit.MILLIS.between(node.getStartedAt(), now) : 0;
        dagNodeInstanceMapper.markFinished(node.getId(), DagNodeStatus.FAILED.name(),
                now, durationMs, null, errorMessage, null);
    }

    private void markNodeSkipped(String dagInstanceId, String jobKey) {
        JobDagNodeInstanceDO node = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                dagInstanceId, jobKey);
        if (node == null) return;
        dagNodeInstanceMapper.markSkipped(node.getId());
    }

    // ==================== P2-5: 跨节点上下文传递 ====================

    /**
     * 将节点执行结果合并到 DAG 实例级上下文（contextJson）。
     *
     * <p>合并策略：以 jobKey 为 key，节点结果为 value，写入 contextJson 对象。
     * 同一 jobKey 的多次执行（重试）会覆盖旧值。
     *
     * <p>线程安全：DAG 实例级 contextJson 更新采用 read-modify-write，
     * 由于 DAG 节点完成事件是异步串行处理的（@Async 单线程默认），
     * 且同一 DAG 实例的节点不会并行完成（拓扑顺序），冲突概率低。
     * 如需强一致，可后续改为 PostgreSQL jsonb 合并或 Redis HSET。
     *
     * @param dagInstanceId DAG 实例 ID
     * @param jobKey        节点 jobKey（作为 contextJson 的 key）
     * @param nodeResultJson 节点执行结果 JSON
     */
    private void mergeNodeResultToContext(String dagInstanceId, String jobKey, String nodeResultJson) {
        try {
            JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
            if (instance == null) {
                return;
            }
            JSONObject context = parseContextJson(instance.getContextJson());
            // 尝试将 nodeResultJson 解析为对象/数组，保留原始类型；解析失败则作为字符串存储
            Object parsed;
            try {
                parsed = JSON.parse(nodeResultJson);
            } catch (Exception parseEx) {
                parsed = nodeResultJson;
            }
            context.put(jobKey, parsed);
            dagInstanceMapper.updateContext(dagInstanceId, JSON.toJSONString(context));
            log.debug("[DagInstance] 上下文合并: instanceId={} jobKey={} keys={}",
                    dagInstanceId, jobKey, context.size());
        } catch (Exception e) {
            log.warn("[DagInstance] 上下文合并异常, 不影响主流程: instanceId={} jobKey={} reason={}",
                    dagInstanceId, jobKey, e.getMessage());
        }
    }

    /**
     * 解析 contextJson，空值或异常时返回空 JSONObject。
     */
    private JSONObject parseContextJson(String contextJson) {
        if (contextJson == null || contextJson.isBlank()) {
            return new JSONObject();
        }
        try {
            Object parsed = JSON.parse(contextJson);
            if (parsed instanceof JSONObject jo) {
                return jo;
            }
        } catch (Exception ignored) {
            // contextJson 非法时返回空对象，避免覆盖
        }
        return new JSONObject();
    }

    /**
     * P2-5: 获取 DAG 实例级上下文（供业务侧查询跨节点传递的参数）。
     *
     * <p>业务侧可在节点执行时调用本方法获取上游节点的执行结果：
     * <pre>{@code
     * JSONObject context = dagInstanceExecutor.getDagContext(dagInstanceId);
     * Object upstreamResult = context.get("upstreamJobKey");
     * }</pre>
     *
     * @param dagInstanceId DAG 实例 ID
     * @return 上下文 JSON 对象（不可变副本）；实例不存在或无上下文返回空对象
     */
    public JSONObject getDagContext(String dagInstanceId) {
        JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
        if (instance == null) {
            return new JSONObject();
        }
        return parseContextJson(instance.getContextJson());
    }
}
