package com.njydsz.pmis.agent.orchestration.dag;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAG 执行上下文（P3-2 落地）。
 *
 * <p>一次 {@link DagDefinition} 执行产生一个 {@code DagExecutionContext}，
 * 负责在节点间共享数据、记录节点状态与执行追踪。
 *
 * <p>线程安全：节点输出与状态使用 {@link ConcurrentHashMap}，支持并行层执行。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
public class DagExecutionContext {

    /** DAG 实例 ID */
    @Getter
    private final String instanceId;

    /** DAG 定义 */
    @Getter
    private final DagDefinition definition;

    /** 节点输出（节点名 -> 输出对象） */
    private final Map<String, Object> nodeOutputs = new ConcurrentHashMap<>();

    /** 节点状态（节点名 -> 状态） */
    private final Map<String, DagNodeStatus> nodeStatuses = new ConcurrentHashMap<>();

    /** 节点错误（节点名 -> 异常） */
    private final Map<String, Throwable> nodeErrors = new ConcurrentHashMap<>();

    /** 节点开始时间（节点名 -> 开始时间） */
    private final Map<String, LocalDateTime> nodeStartTimes = new ConcurrentHashMap<>();

    /** 节点结束时间（节点名 -> 结束时间） */
    private final Map<String, LocalDateTime> nodeEndTimes = new ConcurrentHashMap<>();

    /** 节点重试次数（节点名 -> 已重试次数） */
    private final Map<String, Integer> nodeRetryCounts = new ConcurrentHashMap<>();

    /** 全局输入参数 */
    @Getter
    private final Map<String, Object> globalInputs;

    /** 共享变量（用于条件表达式求值） */
    @Getter
    private final Map<String, Object> sharedVariables = new ConcurrentHashMap<>();

    /** 执行追踪日志 */
    @Getter
    private final List<DagExecutionTrace> traces = Collections.synchronizedList(new ArrayList<>());

    /** Agent 上下文（用于传递 traceId 等） */
    @Getter
    private final AgentContext agentContext;

    /**
     * 构造执行上下文。
     *
     * @param instanceId   DAG 实例 ID
     * @param definition    DAG 定义
     * @param globalInputs  全局输入参数
     * @param agentContext  Agent 上下文（可空）
     */
    public DagExecutionContext(String instanceId, DagDefinition definition,
                                Map<String, Object> globalInputs, AgentContext agentContext) {
        this.instanceId = instanceId;
        this.definition = definition;
        this.globalInputs = globalInputs == null ? new HashMap<>() : new HashMap<>(globalInputs);
        this.agentContext = agentContext;
        // 初始化所有节点状态为 PENDING
        if (definition.getNodes() != null) {
            for (DagNode node : definition.getNodes()) {
                nodeStatuses.put(node.getName(), DagNodeStatus.PENDING);
                nodeRetryCounts.put(node.getName(), 0);
            }
        }
        // 全局输入合并到共享变量
        this.sharedVariables.putAll(this.globalInputs);
    }

    /**
     * 记录节点开始执行。
     *
     * @param nodeName 节点名
     */
    public void markRunning(String nodeName) {
        nodeStatuses.put(nodeName, DagNodeStatus.RUNNING);
        nodeStartTimes.put(nodeName, LocalDateTime.now());
    }

    /**
     * 记录节点执行成功。
     *
     * @param nodeName 节点名
     * @param output   节点输出
     */
    public void markSuccess(String nodeName, Object output) {
        nodeStatuses.put(nodeName, DagNodeStatus.SUCCESS);
        nodeEndTimes.put(nodeName, LocalDateTime.now());
        if (output != null) {
            nodeOutputs.put(nodeName, output);
            // 输出合并到共享变量，供下游节点条件判断使用
            sharedVariables.put(nodeName, output);
        }
    }

    /**
     * 记录节点执行失败。
     *
     * @param nodeName 节点名
     * @param error    异常
     */
    public void markFailed(String nodeName, Throwable error) {
        nodeStatuses.put(nodeName, DagNodeStatus.FAILED);
        nodeEndTimes.put(nodeName, LocalDateTime.now());
        if (error != null) {
            nodeErrors.put(nodeName, error);
        }
    }

    /**
     * 标记节点被跳过。
     *
     * @param nodeName 节点名
     * @param reason   跳过原因
     */
    public void markSkipped(String nodeName, String reason) {
        nodeStatuses.put(nodeName, DagNodeStatus.SKIPPED);
        nodeEndTimes.put(nodeName, LocalDateTime.now());
        addTrace(nodeName, "SKIPPED", reason, null);
    }

    /**
     * 增加节点重试计数。
     *
     * @param nodeName 节点名
     * @return 当前已重试次数
     */
    public int incrementRetry(String nodeName) {
        return nodeRetryCounts.merge(nodeName, 1, Integer::sum);
    }

    /**
     * 获取节点当前状态。
     *
     * @param nodeName 节点名
     * @return 节点状态；不存在返回 null
     */
    public DagNodeStatus getNodeStatus(String nodeName) {
        return nodeStatuses.get(nodeName);
    }

    /**
     * 获取节点输出。
     *
     * @param nodeName 节点名
     * @return 节点输出；不存在返回 null
     */
    public Object getNodeOutput(String nodeName) {
        return nodeOutputs.get(nodeName);
    }

    /**
     * 获取节点错误。
     *
     * @param nodeName 节点名
     * @return 节点错误；不存在返回 null
     */
    public Throwable getNodeError(String nodeName) {
        return nodeErrors.get(nodeName);
    }

    /**
     * 获取节点已重试次数。
     *
     * @param nodeName 节点名
     * @return 已重试次数；不存在返回 0
     */
    public int getRetryCount(String nodeName) {
        return nodeRetryCounts.getOrDefault(nodeName, 0);
    }

    /**
     * 判断节点是否已完成（SUCCESS / FAILED / SKIPPED）。
     *
     * @param nodeName 节点名
     * @return true 表示节点已完成
     */
    public boolean isNodeFinished(String nodeName) {
        DagNodeStatus status = nodeStatuses.get(nodeName);
        return status == DagNodeStatus.SUCCESS || status == DagNodeStatus.FAILED
                || status == DagNodeStatus.SKIPPED;
    }

    /**
     * 判断节点的所有前置节点是否都成功完成。
     *
     * @param node 待检查节点
     * @return true 表示所有前置都 SUCCESS
     */
    public boolean areDependenciesMet(DagNode node) {
        if (node.getDependsOn() == null || node.getDependsOn().isEmpty()) {
            return true;
        }
        for (String dep : node.getDependsOn()) {
            if (nodeStatuses.get(dep) != DagNodeStatus.SUCCESS) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断节点的任一前置节点是否失败或跳过。
     *
     * @param node 待检查节点
     * @return true 表示存在失败/跳过的前置（本节点应跳过）
     */
    public boolean hasFailedDependency(DagNode node) {
        if (node.getDependsOn() == null || node.getDependsOn().isEmpty()) {
            return false;
        }
        for (String dep : node.getDependsOn()) {
            DagNodeStatus status = nodeStatuses.get(dep);
            if (status == DagNodeStatus.FAILED || status == DagNodeStatus.SKIPPED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 添加执行追踪日志。
     *
     * @param nodeName 节点名
     * @param event    事件类型
     * @param message  消息
     * @param data     附加数据
     */
    public void addTrace(String nodeName, String event, String message, Object data) {
        traces.add(new DagExecutionTrace(nodeName, event, message, data, LocalDateTime.now()));
    }

    /**
     * 获取所有节点状态快照。
     *
     * @return 不可变的状态映射
     */
    public Map<String, DagNodeStatus> snapshotStatuses() {
        return Collections.unmodifiableMap(new HashMap<>(nodeStatuses));
    }

    /**
     * 获取所有节点输出快照。
     *
     * @return 不可变的输出映射
     */
    public Map<String, Object> snapshotOutputs() {
        return Collections.unmodifiableMap(new HashMap<>(nodeOutputs));
    }

    /**
     * 判断是否所有节点都已到达终态。
     *
     * @return true 表示无 PENDING / RUNNING 节点
     */
    public boolean allNodesFinished() {
        for (DagNodeStatus status : nodeStatuses.values()) {
            if (status == DagNodeStatus.PENDING || status == DagNodeStatus.RUNNING) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否存在失败节点。
     *
     * @return true 表示存在 FAILED 节点
     */
    public boolean hasFailedNode() {
        for (DagNodeStatus status : nodeStatuses.values()) {
            if (status == DagNodeStatus.FAILED) {
                return true;
            }
        }
        return false;
    }
}
