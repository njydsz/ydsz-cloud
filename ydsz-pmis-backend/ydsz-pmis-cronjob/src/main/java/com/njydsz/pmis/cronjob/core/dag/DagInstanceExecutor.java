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
            nodeInstance.setMaxRetries(0);
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

        // 更新节点状态
        dagNodeInstanceMapper.markFinished(nodeInstance.getId(),
                finalStatus.name(), now, durationMs, null,
                event.success() ? null : "任务执行失败", event.logId());

        log.info("[DagInstance] 节点完成: instanceId={} jobKey={} status={}",
                dagInstanceId, nodeInstance.getJobKey(), finalStatus);

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
            // 节点失败：根据 DAG 级失败策略处理
            FailStrategy dagStrategy = FailStrategy.parse(dag.getFailStrategy());
            if (dagStrategy == FailStrategy.FAIL_FAST) {
                // FAIL_FAST: 标记所有未完成的节点为 SKIPPED
                skipPendingNodes(dagInstanceId);
                log.info("[DagInstance] FAIL_FAST, 跳过未完成节点: instanceId={}", dagInstanceId);
            } else {
                // CONTINUE_ON_FAIL: 仍然触发后继（CONTINUE_ON_FAIL 策略的后继）
                triggerSuccessors(dagInstanceId, instance.getDagId(), nodeInstance.getJobKey(), definition);
            }
        }

        // 检查是否所有节点完成
        finalizeInstance(dagInstanceId);
    }

    /**
     * 触发指定节点的后继节点（仅当后继的所有前置都成功时才派发）。
     */
    private void triggerSuccessors(String dagInstanceId, String dagId, String completedJobKey,
                                    DagDefinition definition) {
        List<DagEdge> outgoing = definition.outgoingEdges(completedJobKey);
        for (DagEdge edge : outgoing) {
            DagNode successor = definition.findNode(edge.to());
            if (successor == null) {
                continue;
            }
            // 检查后继的所有前置是否都成功
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
}
