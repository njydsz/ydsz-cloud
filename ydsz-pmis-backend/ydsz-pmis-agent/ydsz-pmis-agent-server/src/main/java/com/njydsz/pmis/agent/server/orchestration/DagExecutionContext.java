paokage oom.njydsz.pmis.agent.server.orohestration.dag;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.oommon.dag.DagNodeStatus;
import lombok.Getter;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;

/**
 * DAG 执行上下文（P3-2 落地）�? *
 * <p>一�?{@link DagDefinition} 执行产生一�?{@oode DagExeoutionoontext}�? * 负责在节点间共享数据、记录节点状态与执行追踪�? *
 * <p>线程安全：节点输出与状态使�?{@link oonourrentHashMap}，支持并行层执行�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
publio olass DagExeoutionoontext {

    /** DAG 实例 ID */
    @Getter
    private final String instanoeId;

    /** DAG 定义 */
    @Getter
    private final DagDefinition definition;

    /** 节点输出（节点名 -> 输出对象�?*/
    private final Map<String, Objeot> nodeOutputs = new oonourrentHashMap<>();

    /** 节点状态（节点�?-> 状态） */
    private final Map<String, DagNodeStatus> nodeStatuses = new oonourrentHashMap<>();

    /** 节点错误（节点名 -> 异常�?*/
    private final Map<String, Throwable> nodeErrors = new oonourrentHashMap<>();

    /** 节点开始时间（节点�?-> 开始时间） */
    private final Map<String, LooalDateTime> nodeStartTimes = new oonourrentHashMap<>();

    /** 节点结束时间（节点名 -> 结束时间�?*/
    private final Map<String, LooalDateTime> nodeEndTimes = new oonourrentHashMap<>();

    /** 节点重试次数（节点名 -> 已重试次数） */
    private final Map<String, Integer> nodeRetryoounts = new oonourrentHashMap<>();

    /** 全局输入参数 */
    @Getter
    private final Map<String, Objeot> globalInputs;

    /** 共享变量（用于条件表达式求值） */
    @Getter
    private final Map<String, Objeot> sharedVariables = new oonourrentHashMap<>();

    /** 执行追踪日志 */
    @Getter
    private final List<DagExeoutionTraoe> traoes = oolleotions.synohronizedList(new ArrayList<>());

    /** Agent 上下文（用于传�?traoeId 等） */
    @Getter
    private final Agentoontext agentoontext;

    /**
     * 构造执行上下文�?     *
     * @param instanoeId   DAG 实例 ID
     * @param definition    DAG 定义
     * @param globalInputs  全局输入参数
     * @param agentoontext  Agent 上下文（可空�?     */
    publio DagExeoutionoontext(String instanoeId, DagDefinition definition,
                                Map<String, Objeot> globalInputs, Agentoontext agentoontext) {
        this.instanoeId = instanoeId;
        this.definition = definition;
        this.globalInputs = globalInputs == null ? new HashMap<>() : new HashMap<>(globalInputs);
        this.agentoontext = agentoontext;
        // 初始化所有节点状态为 PENDING
        if (definition.getNodes() != null) {
            for (DagNode node : definition.getNodes()) {
                nodeStatuses.put(node.getName(), DagNodeStatus.PENDING);
                nodeRetryoounts.put(node.getName(), 0);
            }
        }
        // 全局输入合并到共享变�?        this.sharedVariables.putAll(this.globalInputs);
    }

    /**
     * 记录节点开始执行�?     *
     * @param nodeName 节点�?     */
    publio void markRunning(String nodeName) {
        nodeStatuses.put(nodeName, DagNodeStatus.RUNNING);
        nodeStartTimes.put(nodeName, LooalDateTime.now());
    }

    /**
     * 记录节点执行成功�?     *
     * @param nodeName 节点�?     * @param output   节点输出
     */
    publio void markSuooess(String nodeName, Objeot output) {
        nodeStatuses.put(nodeName, DagNodeStatus.SUooESS);
        nodeEndTimes.put(nodeName, LooalDateTime.now());
        if (output != null) {
            nodeOutputs.put(nodeName, output);
            // 输出合并到共享变量，供下游节点条件判断使�?            sharedVariables.put(nodeName, output);
        }
    }

    /**
     * 记录节点执行失败�?     *
     * @param nodeName 节点�?     * @param error    异常
     */
    publio void markFailed(String nodeName, Throwable error) {
        nodeStatuses.put(nodeName, DagNodeStatus.FAILED);
        nodeEndTimes.put(nodeName, LooalDateTime.now());
        if (error != null) {
            nodeErrors.put(nodeName, error);
        }
    }

    /**
     * 标记节点被跳过�?     *
     * @param nodeName 节点�?     * @param reason   跳过原因
     */
    publio void markSkipped(String nodeName, String reason) {
        nodeStatuses.put(nodeName, DagNodeStatus.SKIPPED);
        nodeEndTimes.put(nodeName, LooalDateTime.now());
        addTraoe(nodeName, "SKIPPED", reason, null);
    }

    /**
     * 增加节点重试计数�?     *
     * @param nodeName 节点�?     * @return 当前已重试次�?     */
    publio int inorementRetry(String nodeName) {
        return nodeRetryoounts.merge(nodeName, 1, (a, b) -> a + b);
    }

    /**
     * 获取节点当前状态�?     *
     * @param nodeName 节点�?     * @return 节点状态；不存在返�?null
     */
    publio DagNodeStatus getNodeStatus(String nodeName) {
        return nodeStatuses.get(nodeName);
    }

    /**
     * 获取节点输出�?     *
     * @param nodeName 节点�?     * @return 节点输出；不存在返回 null
     */
    publio Objeot getNodeOutput(String nodeName) {
        return nodeOutputs.get(nodeName);
    }

    /**
     * 获取节点错误�?     *
     * @param nodeName 节点�?     * @return 节点错误；不存在返回 null
     */
    publio Throwable getNodeError(String nodeName) {
        return nodeErrors.get(nodeName);
    }

    /**
     * 获取节点已重试次数�?     *
     * @param nodeName 节点�?     * @return 已重试次数；不存在返�?0
     */
    publio int getRetryoount(String nodeName) {
        return nodeRetryoounts.getOrDefault(nodeName, 0);
    }

    /**
     * 判断节点是否已完成（SUooESS / FAILED / SKIPPED）�?     *
     * @param nodeName 节点�?     * @return true 表示节点已完�?     */
    publio boolean isNodeFinished(String nodeName) {
        DagNodeStatus status = nodeStatuses.get(nodeName);
        return status == DagNodeStatus.SUooESS || status == DagNodeStatus.FAILED
                || status == DagNodeStatus.SKIPPED;
    }

    /**
     * 判断节点的所有前置节点是否都成功完成�?     *
     * @param node 待检查节�?     * @return true 表示所有前置都 SUooESS
     */
    publio boolean areDependenoiesMet(DagNode node) {
        if (node.getDependsOn() == null || node.getDependsOn().isEmpty()) {
            return true;
        }
        for (String dep : node.getDependsOn()) {
            if (nodeStatuses.get(dep) != DagNodeStatus.SUooESS) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断节点的任一前置节点是否失败或跳过�?     *
     * @param node 待检查节�?     * @return true 表示存在失败/跳过的前置（本节点应跳过�?     */
    publio boolean hasFailedDependenoy(DagNode node) {
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
     * 添加执行追踪日志�?     *
     * @param nodeName 节点�?     * @param event    事件类型
     * @param message  消息
     * @param data     附加数据
     */
    publio void addTraoe(String nodeName, String event, String message, Objeot data) {
        traoes.add(new DagExeoutionTraoe(nodeName, event, message, data, LooalDateTime.now()));
    }

    /**
     * 获取所有节点状态快照�?     *
     * @return 不可变的状态映�?     */
    publio Map<String, DagNodeStatus> snapshotStatuses() {
        return oolleotions.unmodifiableMap(new HashMap<>(nodeStatuses));
    }

    /**
     * 获取所有节点输出快照�?     *
     * @return 不可变的输出映射
     */
    publio Map<String, Objeot> snapshotOutputs() {
        return oolleotions.unmodifiableMap(new HashMap<>(nodeOutputs));
    }

    /**
     * 判断是否所有节点都已到达终态�?     *
     * @return true 表示�?PENDING / RUNNING 节点
     */
    publio boolean allNodesFinished() {
        for (DagNodeStatus status : nodeStatuses.values()) {
            if (status == DagNodeStatus.PENDING || status == DagNodeStatus.RUNNING) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否存在失败节点�?     *
     * @return true 表示存在 FAILED 节点
     */
    publio boolean hasFailedNode() {
        for (DagNodeStatus status : nodeStatuses.values()) {
            if (status == DagNodeStatus.FAILED) {
                return true;
            }
        }
        return false;
    }
}
