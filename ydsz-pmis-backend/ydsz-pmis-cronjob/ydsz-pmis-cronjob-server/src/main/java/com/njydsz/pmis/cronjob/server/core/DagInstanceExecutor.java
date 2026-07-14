package com.njydsz.pmis.cronjob.server.core.dag;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.njydsz.pmis.common.json.YdszJson;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.dag.DagInstanceStatus;
import com.njydsz.pmis.common.core.dag.DagNodeStatus;
import com.njydsz.pmis.common.core.dag.SpELConditionEvaluator;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.domain.entity.job.JobDO;
import com.njydsz.pmis.cronjob.domain.entity.log.JobLogDO;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagMapper;
import com.njydsz.pmis.cronjob.infra.mapper.dag.JobDagNodeInstanceMapper;
import com.njydsz.pmis.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.pmis.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.pmis.cronjob.server.core.TaskCompletedEvent;
import com.njydsz.pmis.cronjob.server.core.dispatch.TaskDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
/** P1-8: SpEL 条件表达式引擎 */
private final SpELConditionEvaluator spELConditionEvaluator;

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
            // P2-1: 控制节点（CONDITION/LOOP/PARALLEL_GATEWAY）jobId 可能为 null，
            // 使用 jobKey 作为 jobId 的兜底值，确保后续 selectByDagInstanceAndJob 查询能命中
            String effectiveJobId = (node.jobId() != null) ? node.jobId() : node.jobKey();
            nodeInstance.setJobId(effectiveJobId);
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
     *
     * <p>P2-1 增强：根据 {@link DagNode#nodeType()} 分发到不同的处理逻辑：
     * <ul>
     *   <li>TASK：现有逻辑（调用 handler 执行）</li>
     *   <li>CONDITION：评估 conditionExpression 决定走哪条边</li>
     *   <li>LOOP：重复执行下游节点 loopCount 次</li>
     *   <li>PARALLEL_GATEWAY：并行执行所有下游分支</li>
     * </ul>
     */
    private void dispatchNode(String dagInstanceId, String dagId, DagNode node, DagDefinition definition) {
        DagNode.NodeType nodeType = node.resolveNodeType();
        switch (nodeType) {
            case CONDITION -> dispatchConditionNode(dagInstanceId, dagId, node, definition);
            case LOOP -> dispatchLoopNode(dagInstanceId, dagId, node, definition);
            case PARALLEL_GATEWAY -> dispatchParallelGatewayNode(dagInstanceId, dagId, node, definition);
            default -> dispatchTaskNode(dagInstanceId, dagId, node, definition);
        }
    }

    /**
     * 派发 TASK 类型节点（现有逻辑：调用 handler 执行）。
     */
    private void dispatchTaskNode(String dagInstanceId, String dagId, DagNode node, DagDefinition definition) {
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

    // ==================== P2-1: 条件分支 / 循环 / 并行网关 ====================

    /**
     * P2-1: 派发 CONDITION 条件分支节点。
     *
     * <p>评估 conditionExpression 表达式（如 {@code ${nodeA.result=='success'}}），
     * 根据评估结果决定是否触发后继：
     * <ul>
     *   <li>true：标记节点 SUCCESS，触发后继节点（走对应边）</li>
     *   <li>false：标记节点 SKIPPED，不触发后继（跳过边）</li>
     * </ul>
     */
    private void dispatchConditionNode(String dagInstanceId, String dagId,
                                        DagNode node, DagDefinition definition) {
        JobDagNodeInstanceDO nodeInstance = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                dagInstanceId, effectiveJobId(node));
        if (nodeInstance == null) {
            log.warn("[DagInstance] CONDITION 节点实例不存在: instanceId={} jobKey={}",
                    dagInstanceId, node.jobKey());
            return;
        }

        // 标记 RUNNING
        dagNodeInstanceMapper.markRunning(nodeInstance.getId(), LocalDateTime.now());

        // 构建评估上下文：从 DAG 实例 contextJson 获取上游节点结果
        Map<String, Object> context = buildConditionContext(dagInstanceId);

        // 评估条件表达式
        boolean conditionResult = evaluateCondition(node.conditionExpression(), context);
        log.info("[DagInstance] CONDITION 节点评估: instanceId={} jobKey={} expr={} result={}",
                dagInstanceId, node.jobKey(), node.conditionExpression(), conditionResult);

        LocalDateTime now = LocalDateTime.now();
        if (conditionResult) {
            // 条件为 true: 标记 SUCCESS, 触发后继
            dagNodeInstanceMapper.markFinished(nodeInstance.getId(),
                    DagNodeStatus.SUCCESS.name(), now, 0, null, null, null);
            triggerSuccessors(dagInstanceId, dagId, node.jobKey(), definition);
        } else {
            // 条件为 false: 标记 SKIPPED, 不触发后继
            dagNodeInstanceMapper.markSkipped(nodeInstance.getId());
        }

        // 检查是否所有节点完成（CONDITION 节点本身是控制节点，立即终态）
        finalizeInstance(dagInstanceId);
    }

    /**
     * P2-1: 派发 LOOP 循环节点。
     *
     * <p>LOOP 节点作为控制节点，标记 SUCCESS 后将下游节点作为循环体，
     * 重复派发 loopCount 次。每次迭代创建新的节点实例（jobKey 加迭代后缀），
     * 避免与原始节点实例状态冲突。
     */
    private void dispatchLoopNode(String dagInstanceId, String dagId,
                                   DagNode node, DagDefinition definition) {
        JobDagNodeInstanceDO loopInstance = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                dagInstanceId, effectiveJobId(node));
        if (loopInstance == null) {
            log.warn("[DagInstance] LOOP 节点实例不存在: instanceId={} jobKey={}",
                    dagInstanceId, node.jobKey());
            return;
        }

        // 标记 LOOP 控制节点 RUNNING → SUCCESS
        LocalDateTime now = LocalDateTime.now();
        dagNodeInstanceMapper.markRunning(loopInstance.getId(), now);
        dagNodeInstanceMapper.markFinished(loopInstance.getId(),
                DagNodeStatus.SUCCESS.name(), now, 0, null, null, null);

        // 获取循环体（下游节点）
        List<DagEdge> outgoing = definition.outgoingEdges(node.jobKey());
        int loopCount = (node.loopCount() != null && node.loopCount() > 0) ? node.loopCount() : 1;
        log.info("[DagInstance] LOOP 节点循环执行: instanceId={} jobKey={} loopCount={}",
                dagInstanceId, node.jobKey(), loopCount);

        // 重复派发循环体 loopCount 次
        for (int i = 0; i < loopCount; i++) {
            for (DagEdge edge : outgoing) {
                DagNode bodyNode = definition.findNode(edge.to());
                if (bodyNode == null) {
                    continue;
                }
                // 为每次迭代创建新的节点实例（jobKey 加迭代后缀以区分）
                JobDagNodeInstanceDO iterInstance = new JobDagNodeInstanceDO();
                iterInstance.setDagInstanceId(dagInstanceId);
                iterInstance.setDagId(dagId);
                iterInstance.setJobId(bodyNode.jobId());
                iterInstance.setJobKey(bodyNode.jobKey() + "#loop" + i);
                iterInstance.setNodeStatus(DagNodeStatus.PENDING.name());
                iterInstance.setRetryCount(0);
                iterInstance.setMaxRetries(resolveNodeMaxRetries(bodyNode.jobId()));
                iterInstance.setTenantId(loopInstance.getTenantId());
                dagNodeInstanceMapper.insert(iterInstance);

                // 直接派发循环体任务（使用新创建的迭代实例，避免与原始实例状态冲突）
                dispatchTaskNodeWithInstance(dagInstanceId, bodyNode, iterInstance);
            }
        }
    }

    /**
     * 使用指定的节点实例派发 TASK 节点（供 LOOP 迭代复用）。
     *
     * <p>与 {@link #dispatchTaskNode} 的区别：本方法跳过实例查找，
     * 直接使用传入的实例进行派发，支持 LOOP 场景下每次迭代使用独立实例。
     */
    private void dispatchTaskNodeWithInstance(String dagInstanceId, DagNode node,
                                               JobDagNodeInstanceDO instance) {
        JobDO job = jobMapper.selectById(node.jobId());
        if (job == null) {
            log.warn("[DagInstance] 循环体任务不存在, 标记 FAILED: instanceId={} jobKey={}",
                    dagInstanceId, node.jobKey());
            dagNodeInstanceMapper.markFinished(instance.getId(),
                    DagNodeStatus.FAILED.name(), LocalDateTime.now(), 0, null, "任务不存在", null);
            return;
        }
        if (!"NORMAL".equals(job.getStatus())) {
            log.info("[DagInstance] 循环体任务非 NORMAL 状态, 标记 SKIPPED: instanceId={} jobKey={} status={}",
                    dagInstanceId, node.jobKey(), job.getStatus());
            dagNodeInstanceMapper.markSkipped(instance.getId());
            return;
        }

        // 标记迭代实例 RUNNING
        dagNodeInstanceMapper.markRunning(instance.getId(), LocalDateTime.now());

        // 派发任务（triggerType=DEPENDENT, 抢锁）
        String logId = taskDispatcher.dispatch(job, null, "DEPENDENT");
        log.info("[DagInstance] 循环体节点派发: instanceId={} jobKey={} iterLogId={}",
                dagInstanceId, instance.getJobKey(), logId);

        if (logId != null) {
            JobDagNodeInstanceDO update = new JobDagNodeInstanceDO();
            update.setId(instance.getId());
            update.setLogId(logId);
            dagNodeInstanceMapper.updateById(update);
        }
    }

    /**
     * P2-1: 派发 PARALLEL_GATEWAY 并行网关节点。
     *
     * <p>PARALLEL_GATEWAY 节点作为控制节点，标记 SUCCESS 后使用
     * {@link CompletableFuture} 并行执行所有出边对应的子图。
     * 所有分支并行派发，不等待完成（各分支通过事件驱动自行推进）。
     */
    private void dispatchParallelGatewayNode(String dagInstanceId, String dagId,
                                              DagNode node, DagDefinition definition) {
        JobDagNodeInstanceDO gatewayInstance = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                dagInstanceId, effectiveJobId(node));
        if (gatewayInstance == null) {
            log.warn("[DagInstance] PARALLEL_GATEWAY 节点实例不存在: instanceId={} jobKey={}",
                    dagInstanceId, node.jobKey());
            return;
        }

        // 标记并行网关控制节点 RUNNING → SUCCESS
        LocalDateTime now = LocalDateTime.now();
        dagNodeInstanceMapper.markRunning(gatewayInstance.getId(), now);
        dagNodeInstanceMapper.markFinished(gatewayInstance.getId(),
                DagNodeStatus.SUCCESS.name(), now, 0, null, null, null);

        // 获取所有出边对应的子节点
        List<DagEdge> outgoing = definition.outgoingEdges(node.jobKey());
        int branches = node.parallelBranches() != null && node.parallelBranches() > 0
                ? node.parallelBranches() : outgoing.size();
        log.info("[DagInstance] PARALLEL_GATEWAY 并行派发: instanceId={} jobKey={} branches={}",
                dagInstanceId, node.jobKey(), branches);

        // 使用 CompletableFuture 并行派发所有下游分支
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (DagEdge edge : outgoing) {
            DagNode branchNode = definition.findNode(edge.to());
            if (branchNode == null) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    dispatchNode(dagInstanceId, dagId, branchNode, definition);
                } catch (Exception e) {
                    log.error("[DagInstance] 并行分支派发异常: instanceId={} jobKey={} reason={}",
                            dagInstanceId, branchNode.jobKey(), e.getMessage(), e);
                }
            }));
        }
        // 等待所有分支派发完成（派发本身是非阻塞的，这里只是确保所有分支都已提交）
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    /**
     * P2-1: 构建条件评估上下文。
     *
     * <p>从 DAG 实例级 contextJson 中提取所有 jobKey → 节点结果 的映射，
     * 同时补充每个节点的 status（从节点实例表读取）。
     *
     * @param dagInstanceId DAG 实例 ID
     * @return 上下文 Map，key 为 jobKey，value 为节点结果对象（含 result/status 字段）
     */
    private Map<String, Object> buildConditionContext(String dagInstanceId) {
        Map<String, Object> context = new HashMap<>();
        // 1. 从 contextJson 获取节点结果
        Map<String, Object> dagContext = getDagContext(dagInstanceId);
        for (String key : dagContext.keySet()) {
            context.put(key, dagContext.get(key));
        }
        // 2. 补充节点状态（status 字段）
        List<JobDagNodeInstanceDO> nodes = dagNodeInstanceMapper.selectByDagInstanceId(dagInstanceId);
        for (JobDagNodeInstanceDO node : nodes) {
            if (node.getJobKey() == null) {
                continue;
            }
            // 若 contextJson 中已有该 jobKey 的结果，补充 status；否则添加完整对象
            Object existing = context.get(node.getJobKey());
            if (existing instanceof JSONObject jo) {
                jo.put("status", node.getNodeStatus());
            } else {
                Map<String, Object> jo = new JSONObject();
                jo.put("status", node.getNodeStatus());
                jo.put("result", node.getResultJson());
                context.put(node.getJobKey(), jo);
            }
        }
        return context;
    }

    /**
     * P2-1: 评估条件表达式。
     *
     * <p>支持的表达式格式：{@code ${nodeId.field=='value'}} 或 {@code ${nodeId.field=='value'}}
     * <ul>
     *   <li>支持 == 和 != 操作符</li>
     *   <li>field 支持 result / status</li>
     *   <li>value 用单引号或双引号包裹</li>
     *   <li>从上下文中获取上游节点的 result/status 进行比较</li>
     * </ul>
     *
     * <p>示例：
     * <ul>
     *   <li>{@code ${nodeA.result=='success'}} — 判断 nodeA 的结果是否为 success</li>
     *   <li>{@code ${nodeA.status!='FAILED'}} — 判断 nodeA 的状态是否非 FAILED</li>
     * </ul>
     *
     * @param expression 条件表达式
     * @param context    上下文（key=jobKey, value=节点结果对象）
     * @return 评估结果；表达式为空或解析失败时返回 false
     */
    public boolean evaluateCondition(String expression, Map<String, Object> context) {
        // P1-8: 优先使用 SpEL 引擎评估条件表达式
        return spELConditionEvaluator.evaluate(expression, context);
    }

    /**
     * P2-1: 获取节点的有效 Job ID。
     *
     * <p>控制节点（CONDITION/LOOP/PARALLEL_GATEWAY）的 jobId 可能为 null，
     * 此时使用 jobKey 作为兜底（doExecute 创建实例时已用此规则）。
     *
     * @param node DAG 节点
     * @return 有效 Job ID（永不为 null）
     */
    private String effectiveJobId(DagNode node) {
        return (node.jobId() != null) ? node.jobId() : node.jobKey();
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
     *
     * <p>P0-4 修复：使用 {@link JobDagNodeInstanceMapper#selectAllByDagInstanceAndJob}
     * 返回 ALL 匹配实例（含 LOOP iter 实例），避免 LOOP 场景下同一 jobId 的多个实例
     * 仅返回一条导致部分 iter 完成事件丢失。
     */
    private List<JobDagNodeInstanceDO> findRunningNodesByJobId(String jobId) {
        List<JobDagInstanceDO> runningInstances = dagInstanceMapper.selectByStatus(
                DagInstanceStatus.RUNNING.name());
        if (runningInstances.isEmpty()) {
            return List.of();
        }
        return runningInstances.stream()
                .flatMap(inst -> dagNodeInstanceMapper.selectAllByDagInstanceAndJob(inst.getId(), jobId).stream())
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
        int updated = dagNodeInstanceMapper.markFinished(nodeInstance.getId(),
                finalStatus.name(), now, durationMs, nodeResultJson,
                event.success() ? null : "任务执行失败", event.logId());

        // P0-4: markFinished 返回 0 表示节点状态非 RUNNING（CAS 失败）。
        // 典型场景：LOOP 原始 body 节点处于 PENDING（doExecute 创建但从未 dispatch），
        // 被 findRunningNodesByJobId 选中后进入本方法。此时跳过后续处理，避免误触发后继。
        if (updated == 0) {
            log.debug("[DagInstance] 节点状态非 RUNNING, CAS 失败跳过完成处理: instanceId={} jobKey={} currentStatus={}",
                    dagInstanceId, nodeInstance.getJobKey(), nodeInstance.getNodeStatus());
            return;
        }

        log.info("[DagInstance] 节点完成: instanceId={} jobKey={} status={}",
                dagInstanceId, nodeInstance.getJobKey(), finalStatus);

        // P2-5: 节点成功时，将结果合并到 DAG 实例级上下文
        if (event.success() && nodeResultJson != null) {
            mergeNodeResultToContext(dagInstanceId, nodeInstance.getJobKey(), nodeResultJson);
        }

        // P0-4: LOOP iter 实例完成后，不直接触发后继（iter jobKey 带 #loop 后缀，DAG 定义中无对应边）。
        // 而是等所有 iter 完成后，标记原始 body 节点并触发其后继（使用原始 jobKey 查 DAG 定义边）。
        if (isLoopIterJobKey(nodeInstance.getJobKey())) {
            handleLoopIterCompletion(nodeInstance, finalStatus);
            finalizeInstance(dagInstanceId);
            return;
        }

        // 加载 DAG 实例和定义
        JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
        if (instance == null || !DagInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            // P1-4: 实例非 RUNNING 状态（如 PAUSED/CANCELED），不触发后继
            log.info("[DagInstance] 实例非 RUNNING 状态, 不触发后继: instanceId={} status={}",
                    dagInstanceId, instance == null ? "null" : instance.getStatus());
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
     * P0-4: LOOP iter 实例完成处理。
     *
     * <p>当 LOOP 循环体的某个 iter 实例完成时，不直接触发后继（因为 iter 实例的 jobKey
     * 带 {@code #loop<i>} 后缀，DAG 定义中无对应边）。而是检查同一 body 节点的所有 iter
     * 实例是否全部完成：
     * <ul>
     *   <li>全部完成且全部成功 → 标记原始 body 节点 SUCCESS，触发其后继（使用原始 jobKey）</li>
     *   <li>全部完成但有失败 → 标记原始 body 节点 FAILED，按 DAG 级失败策略处理</li>
     *   <li>仍有未完成 iter → 不做处理，等待最后一个 iter 完成时再聚合</li>
     * </ul>
     *
     * <p>并发安全：使用 {@code markRunning} 的 CAS 语义（PENDING → RUNNING）确保
     * 多个 iter 同时完成时，原始 body 节点只被一个线程标记。
     *
     * @param iterInstance   完成的 iter 实例（jobKey 含 {@code #loop} 后缀）
     * @param iterFinalStatus iter 实例的终态（SUCCESS/FAILED）
     */
    private void handleLoopIterCompletion(JobDagNodeInstanceDO iterInstance, DagNodeStatus iterFinalStatus) {
        String dagInstanceId = iterInstance.getDagInstanceId();
        String originalJobKey = stripLoopSuffix(iterInstance.getJobKey());

        // 查询同一 jobId 的所有实例（原始 body + 所有 iter）
        List<JobDagNodeInstanceDO> allInstances = dagNodeInstanceMapper.selectAllByDagInstanceAndJob(
                dagInstanceId, iterInstance.getJobId());

        // 分离原始 body 节点实例和 iter 实例
        JobDagNodeInstanceDO originalBody = null;
        List<JobDagNodeInstanceDO> iterInstances = new ArrayList<>();
        for (JobDagNodeInstanceDO inst : allInstances) {
            if (isLoopIterJobKey(inst.getJobKey())) {
                iterInstances.add(inst);
            } else {
                originalBody = inst;
            }
        }

        if (originalBody == null) {
            log.warn("[DagInstance] LOOP 原始 body 节点实例不存在: instanceId={} jobKey={}",
                    dagInstanceId, originalJobKey);
            return;
        }

        // 检查是否所有 iter 实例都已终态（非 PENDING/RUNNING）
        long pendingCount = iterInstances.stream()
                .filter(inst -> DagNodeStatus.PENDING.name().equals(inst.getNodeStatus())
                        || DagNodeStatus.RUNNING.name().equals(inst.getNodeStatus()))
                .count();
        if (pendingCount > 0) {
            log.debug("[DagInstance] LOOP iter 未全部完成, 等待: instanceId={} jobKey={} pending={} total={}",
                    dagInstanceId, originalJobKey, pendingCount, iterInstances.size());
            return;
        }

        // 所有 iter 完成，聚合状态
        boolean allSuccess = iterInstances.stream()
                .allMatch(inst -> DagNodeStatus.SUCCESS.name().equals(inst.getNodeStatus()));

        // CAS 标记原始 body 节点（PENDING → RUNNING → SUCCESS/FAILED）
        // 使用 markRunning 的 CAS 语义（PENDING → RUNNING）确保只被一个线程标记
        LocalDateTime now = LocalDateTime.now();
        int runningUpdated = dagNodeInstanceMapper.markRunning(originalBody.getId(), now);
        if (runningUpdated == 0) {
            log.debug("[DagInstance] LOOP body 节点已被其他线程处理, 跳过: instanceId={} jobKey={}",
                    dagInstanceId, originalJobKey);
            return;
        }

        DagNodeStatus bodyFinalStatus = allSuccess ? DagNodeStatus.SUCCESS : DagNodeStatus.FAILED;
        String errorMessage = allSuccess ? null : "LOOP 循环体存在失败迭代";
        dagNodeInstanceMapper.markFinished(originalBody.getId(),
                bodyFinalStatus.name(), now, 0, null, errorMessage, null);

        log.info("[DagInstance] LOOP body 全部 iter 完成: instanceId={} jobKey={} iterCount={} bodyStatus={}",
                dagInstanceId, originalJobKey, iterInstances.size(), bodyFinalStatus);

        // 加载 DAG 实例和定义，触发后继或处理失败
        JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
        if (instance == null || !DagInstanceStatus.RUNNING.name().equals(instance.getStatus())) {
            return;
        }
        JobDagDO dag = dagMapper.selectById(instance.getDagId());
        if (dag == null) {
            return;
        }
        DagDefinition definition = dagDefinitionCodec.fromJson(dag.getDagDefinition());

        if (allSuccess) {
            // 触发原始 body 节点的后继（使用原始 jobKey 查 DAG 定义中的边）
            triggerSuccessors(dagInstanceId, instance.getDagId(), originalJobKey, definition);
        } else {
            // 按 DAG 级失败策略处理（对原始 body 节点）
            FailStrategy dagStrategy = FailStrategy.parse(dag.getFailStrategy());
            handleNodeFailure(dagInstanceId, instance.getDagId(), originalBody, definition, dagStrategy);
        }
    }

    /**
     * P0-4: 判断 jobKey 是否为 LOOP iter 实例（带 {@code #loop<i>} 后缀）。
     */
    private boolean isLoopIterJobKey(String jobKey) {
        return jobKey != null && jobKey.contains("#loop");
    }

    /**
     * P0-4: 去除 LOOP iter 后缀，得到原始 jobKey。
     */
    private String stripLoopSuffix(String jobKey) {
        if (jobKey == null) {
            return null;
        }
        int idx = jobKey.indexOf("#loop");
        return idx > 0 ? jobKey.substring(0, idx) : jobKey;
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
     * <p>P0-3 修复：当节点实例的 jobKey 带 LOOP iter 后缀（{@code #loop<i>}）时，
     * {@link DagDefinition#findNode} 无法匹配（DAG 定义中只有原始 jobKey）。
     * 修复方案：先尝试原始 jobKey 查找；若失败则去除 {@code #loop} 后缀后重试。
     * 这样即使 LOOP iter 实例意外进入重试路径（理论上 P0-4 修复后不会发生），
     * 也能正确找到 DagNode 并重新派发，避免 markRetry 成功但节点卡死在 PENDING。
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
        // P0-3: 优先使用原始 jobKey 查找 DagNode；LOOP iter 后缀场景下去缀后重试
        DagNode node = definition.findNode(refreshed.getJobKey());
        if (node == null && isLoopIterJobKey(refreshed.getJobKey())) {
            String strippedKey = stripLoopSuffix(refreshed.getJobKey());
            node = definition.findNode(strippedKey);
            log.debug("[DagInstance] RETRY 使用去缀 jobKey 查找 DagNode: original={} stripped={}",
                    refreshed.getJobKey(), strippedKey);
        }
        if (node == null) {
            // markRetry 已成功但 findNode 失败，回滚节点状态避免卡死 PENDING
            log.warn("[DagInstance] RETRY findNode 失败, 节点状态异常: instanceId={} jobKey={}",
                    refreshed.getDagInstanceId(), refreshed.getJobKey());
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
        // P2-1: 控制节点 jobId 可能为 null，使用 jobKey 兜底
        String lookupId = node.jobId() != null ? node.jobId() : node.jobKey();
        JobDagNodeInstanceDO nodeInstance = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                dagInstanceId, lookupId);
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
     *
     * <p>P2-1: 支持控制节点（CONDITION/LOOP/PARALLEL_GATEWAY）jobId 为 null 的场景，
     * 使用 jobKey 作为查询兜底。
     */
    private boolean areAllPredecessorsSuccessful(String dagInstanceId, String jobKey,
                                                  DagDefinition definition) {
        List<DagEdge> incoming = definition.incomingEdges(jobKey);
        if (incoming.isEmpty()) {
            return true; // 无前置，可直接执行
        }
        for (DagEdge edge : incoming) {
            DagNode predDagNode = definition.findNode(edge.from());
            // P2-1: 控制节点 jobId 可能为 null，使用 jobKey 兜底
            String lookupId = predDagNode.jobId() != null ? predDagNode.jobId() : predDagNode.jobKey();
            JobDagNodeInstanceDO predNode = dagNodeInstanceMapper.selectByDagInstanceAndJob(
                    dagInstanceId, lookupId);
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
     * <p>P0-1 并发安全修复：使用 PostgreSQL {@code jsonb ||} 操作符在 DB 层面原子合并，
     * 消除 read-modify-write 竞态。并行网关（PARALLEL_GATEWAY）多分支同时写 contextJson
     * 时不再丢失数据。
     *
     * <p>合并策略：构造 {@code {"jobKey": nodeResult}} 片段，通过
     * {@link JobDagInstanceMapper#mergeContextAtomic} 原子写入。
     * 相同 jobKey 的后写覆盖先写（重试场景），不同 jobKey 各自保留。
     *
     * @param dagInstanceId DAG 实例 ID
     * @param jobKey        节点 jobKey（作为 contextJson 的 key）
     * @param nodeResultJson 节点执行结果 JSON
     */
    private void mergeNodeResultToContext(String dagInstanceId, String jobKey, String nodeResultJson) {
        try {
            // 构造待合并的 JSON 片段: {"jobKey": <nodeResult>}
            Object parsed;
            try {
                parsed = JSON.parse(nodeResultJson);
            } catch (Exception parseEx) {
                parsed = nodeResultJson;
            }
            Map<String, Object> mergeFragment = new JSONObject();
            mergeFragment.put(jobKey, parsed);
            String mergeJson = YdszJson.toJson(mergeFragment);

            // 使用 PostgreSQL jsonb || 原子合并，消除 read-modify-write 竞态
            dagInstanceMapper.mergeContextAtomic(dagInstanceId, mergeJson);
            log.debug("[DagInstance] 上下文原子合并: instanceId={} jobKey={}",
                    dagInstanceId, jobKey);
        } catch (Exception e) {
            log.warn("[DagInstance] 上下文合并异常, 不影响主流程: instanceId={} jobKey={} reason={}",
                    dagInstanceId, jobKey, e.getMessage());
        }
    }

    /**
     * 解析 contextJson，空值或异常时返回空 JSONObject。
     */
    private Map<String, Object> parseContextJson(String contextJson) {
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
     * Map<String, Object> context = dagInstanceExecutor.getDagContext(dagInstanceId);
     * Object upstreamResult = context.get("upstreamJobKey");
     * }</pre>
     *
     * @param dagInstanceId DAG 实例 ID
     * @return 上下文 JSON 对象（不可变副本）；实例不存在或无上下文返回空对象
     */
    public Map<String, Object> getDagContext(String dagInstanceId) {
        JobDagInstanceDO instance = dagInstanceMapper.selectById(dagInstanceId);
        if (instance == null) {
            return new JSONObject();
        }
        return parseContextJson(instance.getContextJson());
    }
}
