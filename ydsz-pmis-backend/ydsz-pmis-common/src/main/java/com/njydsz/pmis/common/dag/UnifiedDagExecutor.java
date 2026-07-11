package com.njydsz.pmis.common.dag;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 统一 DAG 执行引擎（P1-1 架构优化）。
 *
 * <p>基于 {@link DagGraph} 拓扑分析实现分层并行执行，支持：
 * <ul>
 *   <li>分层并行：同一拓扑层的节点无依赖关系，可并行执行</li>
 *   <li>条件分支：节点可配置 SpEL 条件表达式，求值为 false 时跳过</li>
 *   <li>失败策略：CONTINUE / ABORT / RETRY</li>
 *   <li>超时控制：节点级超时</li>
 *   <li>上下文传递：上游节点输出自动注入下游节点的共享变量</li>
 *   <li>SPI 扩展：通过 {@link DagNodeExecutor} 接口，各模块提供具体执行逻辑</li>
 * </ul>
 *
 * <p>各模块（agent / cronjob）通过注入 {@link DagNodeExecutor} 实现来接入统一引擎。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
public class UnifiedDagExecutor {

    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ExecutorService executor;

    /** 节点执行器 SPI（由各模块实现） */
    private final DagNodeExecutor nodeExecutor;

    /**
     * 默认构造器，使用 cached thread pool。
     *
     * @param nodeExecutor 节点执行器 SPI
     */
    public UnifiedDagExecutor(DagNodeExecutor nodeExecutor) {
        this(nodeExecutor, Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "unified-dag-worker");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * 注入式构造器。
     *
     * @param nodeExecutor 节点执行器 SPI
     * @param executor     线程池
     */
    public UnifiedDagExecutor(DagNodeExecutor nodeExecutor, ExecutorService executor) {
        this.nodeExecutor = nodeExecutor;
        this.executor = executor;
    }

    /**
     * 执行 DAG。
     *
     * @param dag          DAG 定义
     * @param globalInputs 全局输入参数
     * @return 执行结果
     */
    public UnifiedDagExecutionResult execute(UnifiedDagDefinition dag, Map<String, Object> globalInputs) {
        // 1. 校验 DAG 定义
        Map<String, List<String>> adj = buildAdjacency(dag);
        DagGraph.validate(adj, dag.getName());
        List<List<String>> layers = DagGraph.layeredSort(adj);

        // 2. 构造执行上下文
        String instanceId = "dag-" + UUID.randomUUID();
        Map<String, Object> sharedVariables = new HashMap<>();
        if (globalInputs != null) {
            sharedVariables.putAll(globalInputs);
        }
        if (dag.getInputs() != null) {
            sharedVariables.putAll(dag.getInputs());
        }

        Map<String, DagNodeStatus> nodeStatuses = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, Object> nodeOutputs = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, String> nodeErrors = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, Integer> retryCounts = new java.util.concurrent.ConcurrentHashMap<>();
        List<String> traces = java.util.Collections.synchronizedList(new ArrayList<>());

        traces.add(String.format("[DAG_STARTED] %s, layers=%d", dag.getName(), layers.size()));
        long startTime = System.currentTimeMillis();
        boolean aborted = false;
        String abortReason = null;

        // 3. 逐层执行
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            if (aborted) {
                for (String nodeName : layers.get(layerIdx)) {
                    nodeStatuses.put(nodeName, DagNodeStatus.SKIPPED);
                }
                continue;
            }

            List<String> layer = layers.get(layerIdx);
            traces.add(String.format("[LAYER_START] layer=%d nodes=%s", layerIdx, layer));

            Map<String, Future<NodeOutcome>> futures = new HashMap<>();
            for (String nodeName : layer) {
                UnifiedDagNode node = dag.findNode(nodeName);
                futures.put(nodeName, executor.submit(
                        new NodeRunner(node, dag, sharedVariables, nodeStatuses,
                                nodeOutputs, nodeErrors, retryCounts, traces)));
            }

            for (Map.Entry<String, Future<NodeOutcome>> entry : futures.entrySet()) {
                String nodeName = entry.getKey();
                try {
                    NodeOutcome outcome = entry.getValue().get();
                    if (outcome == NodeOutcome.ABORT) {
                        aborted = true;
                        abortReason = "Node " + nodeName + " failed (ABORT)";
                    }
                } catch (Exception e) {
                    log.error("[DAG:{}] Node {} Future error", dag.getName(), nodeName, e);
                    nodeStatuses.put(nodeName, DagNodeStatus.FAILED);
                    nodeErrors.put(nodeName, e.getMessage());
                    UnifiedDagNode node = dag.findNode(nodeName);
                    if (resolveFailureStrategy(node, dag) == DagFailureStrategy.ABORT) {
                        aborted = true;
                        abortReason = "Node " + nodeName + " exception";
                    }
                }
            }

            traces.add(String.format("[LAYER_END] layer=%d", layerIdx));
        }

        // 4. 汇总
        long totalCost = System.currentTimeMillis() - startTime;
        DagInstanceStatus finalStatus = resolveFinalStatus(nodeStatuses, aborted);
        traces.add(String.format("[DAG_FINISHED] %s, status=%s, cost=%dms", dag.getName(), finalStatus, totalCost));

        return UnifiedDagExecutionResult.builder()
                .instanceId(instanceId)
                .definitionId(dag.getId())
                .dagName(dag.getName())
                .status(finalStatus)
                .nodeStatuses(nodeStatuses)
                .nodeOutputs(nodeOutputs)
                .nodeErrors(nodeErrors)
                .nodeRetryCounts(retryCounts)
                .traces(List.copyOf(traces))
                .totalCostMs(totalCost)
                .totalNodes(dag.getNodes().size())
                .note(abortReason)
                .build();
    }

    /**
     * 节点执行任务。
     */
    private class NodeRunner implements Callable<NodeOutcome> {
        private final UnifiedDagNode node;
        private final UnifiedDagDefinition dag;
        private final Map<String, Object> sharedVariables;
        private final Map<String, DagNodeStatus> nodeStatuses;
        private final Map<String, Object> nodeOutputs;
        private final Map<String, String> nodeErrors;
        private final Map<String, Integer> retryCounts;
        private final List<String> traces;

        NodeRunner(UnifiedDagNode node, UnifiedDagDefinition dag,
                   Map<String, Object> sharedVariables,
                   Map<String, DagNodeStatus> nodeStatuses,
                   Map<String, Object> nodeOutputs,
                   Map<String, String> nodeErrors,
                   Map<String, Integer> retryCounts,
                   List<String> traces) {
            this.node = node;
            this.dag = dag;
            this.sharedVariables = sharedVariables;
            this.nodeStatuses = nodeStatuses;
            this.nodeOutputs = nodeOutputs;
            this.nodeErrors = nodeErrors;
            this.retryCounts = retryCounts;
            this.traces = traces;
        }

        @Override
        public NodeOutcome call() {
            // 1. 检查前置依赖
            if (hasFailedDependency()) {
                nodeStatuses.put(node.getName(), DagNodeStatus.SKIPPED);
                traces.add(String.format("[SKIPPED] %s (dependency failed)", node.getName()));
                return NodeOutcome.CONTINUE;
            }

            // 2. 检查条件
            if (node.getCondition() != null && !node.getCondition().isBlank()) {
                if (!evaluateCondition(node.getCondition(), sharedVariables)) {
                    nodeStatuses.put(node.getName(), DagNodeStatus.SKIPPED);
                    traces.add(String.format("[SKIPPED] %s (condition false: %s)", node.getName(), node.getCondition()));
                    return NodeOutcome.CONTINUE;
                }
            }

            // 3. 执行（支持重试）
            DagFailureStrategy strategy = resolveFailureStrategy(node, dag);
            int maxRetries = resolveMaxRetries(node, dag);
            int attempts = strategy == DagFailureStrategy.RETRY ? maxRetries + 1 : 1;

            for (int attempt = 1; attempt <= attempts; attempt++) {
                nodeStatuses.put(node.getName(), DagNodeStatus.RUNNING);
                traces.add(String.format("[STARTED] %s (attempt=%d)", node.getName(), attempt));

                try {
                    Object output = executeNode();
                    nodeOutputs.put(node.getName(), output);
                    sharedVariables.put(node.getName(), output);
                    nodeStatuses.put(node.getName(), DagNodeStatus.SUCCESS);
                    traces.add(String.format("[SUCCESS] %s", node.getName()));
                    return NodeOutcome.CONTINUE;
                } catch (Exception e) {
                    nodeErrors.put(node.getName(), e.getMessage());
                    if (attempt < attempts) {
                        retryCounts.merge(node.getName(), 1, Integer::sum);
                        traces.add(String.format("[RETRY] %s attempt=%d err=%s", node.getName(), attempt, e.getMessage()));
                    } else {
                        nodeStatuses.put(node.getName(), DagNodeStatus.FAILED);
                        traces.add(String.format("[FAILED] %s err=%s", node.getName(), e.getMessage()));
                        return strategy == DagFailureStrategy.CONTINUE ? NodeOutcome.CONTINUE : NodeOutcome.ABORT;
                    }
                }
            }
            return NodeOutcome.CONTINUE;
        }

        private boolean hasFailedDependency() {
            if (node.getDependsOn() == null) return false;
            for (String dep : node.getDependsOn()) {
                DagNodeStatus status = nodeStatuses.get(dep);
                if (status == DagNodeStatus.FAILED || status == DagNodeStatus.SKIPPED) {
                    return true;
                }
            }
            return false;
        }

        private Object executeNode() throws Exception {
            if (node.getNodeType() == null || node.getNodeType().isBlank()) {
                return null;
            }
            long timeoutMs = resolveTimeoutMs(node, dag);
            if (timeoutMs > 0) {
                Future<Object> future = executor.submit(() ->
                        nodeExecutor.execute(node.getName(), node.getNodeType(), node.getInputs(), sharedVariables));
                try {
                    return future.get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    future.cancel(true);
                    throw new TimeoutException("Node " + node.getName() + " timeout (" + timeoutMs + "ms)");
                }
            }
            return nodeExecutor.execute(node.getName(), node.getNodeType(), node.getInputs(), sharedVariables);
        }
    }

    private boolean evaluateCondition(String expression, Map<String, Object> variables) {
        try {
            Expression exp = spelParser.parseExpression(expression);
            EvaluationContext evalCtx = new StandardEvaluationContext(variables);
            Boolean result = exp.getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Condition eval failed, default false: {} ({})", expression, e.getMessage());
            return false;
        }
    }

    private DagFailureStrategy resolveFailureStrategy(UnifiedDagNode node, UnifiedDagDefinition dag) {
        if (node != null && node.getFailureStrategy() != null) {
            return node.getFailureStrategy();
        }
        return dag.getFailureStrategy() != null ? dag.getFailureStrategy() : DagFailureStrategy.ABORT;
    }

    private int resolveMaxRetries(UnifiedDagNode node, UnifiedDagDefinition dag) {
        if (node != null && node.getMaxRetries() != null) {
            return node.getMaxRetries();
        }
        return dag.getMaxRetries() != null ? dag.getMaxRetries() : 3;
    }

    private long resolveTimeoutMs(UnifiedDagNode node, UnifiedDagDefinition dag) {
        if (node != null && node.getTimeoutMs() > 0) {
            return node.getTimeoutMs();
        }
        return dag.getDefaultTimeoutMs();
    }

    private DagInstanceStatus resolveFinalStatus(Map<String, DagNodeStatus> statuses, boolean aborted) {
        if (aborted || statuses.values().stream().anyMatch(s -> s == DagNodeStatus.FAILED)) {
            return DagInstanceStatus.FAILED;
        }
        return DagInstanceStatus.SUCCESS;
    }

    private Map<String, List<String>> buildAdjacency(UnifiedDagDefinition dag) {
        Map<String, List<String>> adj = new HashMap<>();
        for (UnifiedDagNode node : dag.getNodes()) {
            adj.computeIfAbsent(node.getName(), k -> new ArrayList<>());
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    adj.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getName());
                }
            }
        }
        return adj;
    }

    @PreDestroy
    public void destroy() {
        if (executor.isShutdown()) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private enum NodeOutcome {
        CONTINUE,
        ABORT
    }
}
