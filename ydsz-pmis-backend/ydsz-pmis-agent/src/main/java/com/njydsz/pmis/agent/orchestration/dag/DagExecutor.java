package com.njydsz.pmis.agent.orchestration.dag;

import com.njydsz.pmis.agent.engine.Agent;
import com.njydsz.pmis.agent.engine.AgentContext;
import com.njydsz.pmis.agent.engine.AgentResult;
import com.njydsz.pmis.common.dag.DagFailureStrategy;
import com.njydsz.pmis.common.dag.DagGraph;
import com.njydsz.pmis.common.dag.DagInstanceStatus;
import com.njydsz.pmis.common.dag.DagNodeStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

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
 * DAG 执行引擎（P3-2 落地）。
 *
 * <p>基于 {@link DagTopology#layeredSort} 分层并行执行节点，支持：
 * <ul>
 *   <li>分层并行：同一拓扑层的节点无依赖关系，可并行执行</li>
 *   <li>条件分支：节点可配置 SpEL 条件表达式，求值为 false 时跳过</li>
 *   <li>失败策略：CONTINUE（继续其他分支）/ ABORT（中止整个 DAG）/ RETRY（重试 N 次）</li>
 *   <li>超时控制：节点级超时，超时后标记 FAILED</li>
 *   <li>上下文传递：上游节点输出自动注入下游节点的共享变量</li>
 * </ul>
 *
 * <p>对标 LangGraph Compile + Invoke / Dify Workflow Run / Coze Bot 工作流引擎。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-2)
 */
@Slf4j
public class DagExecutor {

    /** SpEL 表达式解析器（线程安全，可复用） */
    private final ExpressionParser spelParser = new SpelExpressionParser();

    /** 共享线程池（并行层执行） */
    private final ExecutorService executor;

    /**
     * 默认构造器，使用 cached thread pool。
     */
    public DagExecutor() {
        this(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "dag-executor-worker");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * 注入式构造器（便于测试 mock 线程池）。
     *
     * @param executor 线程池
     */
    public DagExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    /**
     * 执行 DAG。
     *
     * @param dag          DAG 定义
     * @param agents       参与执行的 Agent 表（agentType -> Agent）
     * @param globalInputs 全局输入参数
     * @param agentCtx     Agent 上下文模板（用于传递 traceId 等）
     * @return 执行结果
     */
    public DagExecutionResult execute(DagDefinition dag, Map<String, Agent> agents,
                                       Map<String, Object> globalInputs, AgentContext agentCtx) {
        // 1. 校验 DAG 定义（含环检测）
        Map<String, List<String>> adj = buildAdjacencyFromDag(dag);
        DagGraph.validate(adj, dag.getName());
        List<List<String>> layers = DagGraph.layeredSort(adj);

        // 2. 构造执行上下文
        String instanceId = "dag-" + UUID.randomUUID();
        DagExecutionContext ctx = new DagExecutionContext(instanceId, dag, globalInputs, agentCtx);
        ctx.addTrace(null, "DAG_STARTED", "DAG " + dag.getName() + " 开始执行, 共 " + layers.size() + " 层",
                layers.size());

        long startTime = System.currentTimeMillis();
        boolean aborted = false;
        String abortReason = null;

        // 3. 逐层执行
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            if (aborted) {
                // ABORT 后剩余层全部跳过
                List<String> layer = layers.get(layerIdx);
                for (String nodeName : layer) {
                    ctx.markSkipped(nodeName, "前置层中止");
                }
                continue;
            }

            List<String> layer = layers.get(layerIdx);
            ctx.addTrace(null, "LAYER_START", "第 " + layerIdx + " 层开始: " + layer, layerIdx);

            // 并行执行当前层
            Map<String, Future<NodeOutcome>> futures = new HashMap<>();
            for (String nodeName : layer) {
                DagNode node = dag.findNode(nodeName);
                futures.put(nodeName, executor.submit(new NodeRunner(node, agents, ctx, dag)));
            }

            // 等待当前层完成
            for (Map.Entry<String, Future<NodeOutcome>> entry : futures.entrySet()) {
                String nodeName = entry.getKey();
                try {
                    NodeOutcome outcome = entry.getValue().get();
                    if (outcome == NodeOutcome.ABORT) {
                        aborted = true;
                        abortReason = "节点 " + nodeName + " 失败且策略为 ABORT";
                    }
                } catch (Exception e) {
                    // Future.get 异常，理论上 NodeRunner 内部已处理
                    log.error("[DAG:{}] 节点 {} Future 异常", dag.getName(), nodeName, e);
                    ctx.markFailed(nodeName, e);
                    if (resolveFailureStrategy(dag.findNode(nodeName), dag) == DagFailureStrategy.ABORT) {
                        aborted = true;
                        abortReason = "节点 " + nodeName + " 执行异常";
                    }
                }
            }

            ctx.addTrace(null, "LAYER_END", "第 " + layerIdx + " 层完成", layerIdx);
        }

        // 4. 汇总结果
        long totalCost = System.currentTimeMillis() - startTime;
        DagInstanceStatus finalStatus = resolveFinalStatus(ctx, aborted);
        ctx.addTrace(null, "DAG_FINISHED",
                "DAG " + dag.getName() + " 执行完成, 状态=" + finalStatus + ", 耗时=" + totalCost + "ms",
                totalCost);

        return buildResult(ctx, dag, finalStatus, totalCost, abortReason);
    }

    /**
     * 从 DagDefinition 构建邻接表（适配 common.DagGraph）。
     */
    private Map<String, List<String>> buildAdjacencyFromDag(DagDefinition dag) {
        Map<String, List<String>> adj = new HashMap<>();
        for (DagNode node : dag.getNodes()) {
            adj.computeIfAbsent(node.getName(), k -> new java.util.ArrayList<>());
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    adj.computeIfAbsent(dep, k -> new java.util.ArrayList<>()).add(node.getName());
                }
            }
        }
        return adj;
    }

    /**
     * 关闭线程池。
     */
    @PreDestroy
    public void destroy() {
        if (executor.isShutdown()) {
            return;
        }
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

    /**
     * 节点执行任务。
     */
    private class NodeRunner implements Callable<NodeOutcome> {

        private final DagNode node;
        private final Map<String, Agent> agents;
        private final DagExecutionContext ctx;
        private final DagDefinition dag;

        NodeRunner(DagNode node, Map<String, Agent> agents, DagExecutionContext ctx, DagDefinition dag) {
            this.node = node;
            this.agents = agents;
            this.ctx = ctx;
            this.dag = dag;
        }

        @Override
        public NodeOutcome call() {
            // 1. 检查前置依赖是否失败 → 跳过
            if (ctx.hasFailedDependency(node)) {
                ctx.markSkipped(node.getName(), "前置节点失败或跳过");
                log.info("[DAG:{}] 节点 {} 跳过（前置失败）", dag.getName(), node.getName());
                return NodeOutcome.CONTINUE;
            }

            // 2. 检查条件表达式
            if (node.getCondition() != null && !node.getCondition().isBlank()) {
                if (!evaluateCondition(node.getCondition(), ctx)) {
                    ctx.markSkipped(node.getName(), "条件不满足: " + node.getCondition());
                    log.info("[DAG:{}] 节点 {} 跳过（条件 false）", dag.getName(), node.getName());
                    return NodeOutcome.CONTINUE;
                }
            }

            // 3. 执行节点（支持重试）
            DagFailureStrategy strategy = resolveFailureStrategy(node, dag);
            int maxRetries = resolveMaxRetries(node, dag);
            int attempts = strategy == DagFailureStrategy.RETRY ? maxRetries + 1 : 1;

            for (int attempt = 1; attempt <= attempts; attempt++) {
                ctx.markRunning(node.getName());
                ctx.addTrace(node.getName(), "STARTED",
                        "节点开始执行" + (attempt > 1 ? " (重试 " + (attempt - 1) + "/" + maxRetries + ")" : ""),
                        attempt);

                try {
                    Object output = executeNode(node, agents, ctx, dag);
                    ctx.markSuccess(node.getName(), output);
                    ctx.addTrace(node.getName(), "SUCCESS", "节点执行成功", summarizeOutput(output));
                    log.info("[DAG:{}] 节点 {} 执行成功", dag.getName(), node.getName());
                    return NodeOutcome.CONTINUE;
                } catch (Exception e) {
                    ctx.markFailed(node.getName(), e);
                    if (attempt < attempts) {
                        ctx.incrementRetry(node.getName());
                        ctx.addTrace(node.getName(), "RETRY",
                                "节点执行失败，准备重试: " + e.getMessage(), attempt);
                        log.warn("[DAG:{}] 节点 {} 第 {} 次执行失败，准备重试",
                                dag.getName(), node.getName(), attempt, e);
                        ctx.markRunning(node.getName()); // 重新标记 RUNNING
                    } else {
                        ctx.addTrace(node.getName(), "FAILED",
                                "节点执行失败（重试耗尽）: " + e.getMessage(), null);
                        log.error("[DAG:{}] 节点 {} 执行失败", dag.getName(), node.getName(), e);
                        return switch (strategy) {
                            case ABORT, RETRY, SKIP_SUBSEQUENT -> NodeOutcome.ABORT;
                            case CONTINUE -> NodeOutcome.CONTINUE;
                        };
                    }
                }
            }
            return NodeOutcome.CONTINUE;
        }
    }

    /**
     * 执行单个节点（调用关联的 Agent）。
     */
    private Object executeNode(DagNode node, Map<String, Agent> agents,
                                DagExecutionContext ctx, DagDefinition dag) throws Exception {
        // 空节点：agentType 为 null，直接返回 SUCCESS
        if (node.getAgentType() == null || node.getAgentType().isBlank()) {
            log.debug("[DAG:{}] 节点 {} 为空节点，直接通过", dag.getName(), node.getName());
            return null;
        }

        Agent agent = agents == null ? null : agents.get(node.getAgentType());
        if (agent == null) {
            throw new IllegalStateException("节点 " + node.getName()
                    + " 关联的 Agent 类型 " + node.getAgentType() + " 不存在");
        }

        // 构造 AgentContext
        AgentContext agentCtx = buildAgentContext(node, ctx);
        // 合并节点级输入参数到 params
        if (node.getInputs() != null && agentCtx.getParams() == null) {
            agentCtx.setParams(new HashMap<>(node.getInputs()));
        } else if (node.getInputs() != null) {
            agentCtx.getParams().putAll(node.getInputs());
        }

        // 超时控制
        long timeoutMs = resolveTimeoutMs(node, dag);
        if (timeoutMs > 0) {
            Future<AgentResult> future = executor.submit(() -> agent.execute(agentCtx));
            try {
                AgentResult result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                return result;
            } catch (TimeoutException te) {
                future.cancel(true);
                throw new TimeoutException(
                        "节点 " + node.getName() + " 超时 (" + timeoutMs + "ms)");
            }
        }

        // 无超时直接执行
        return agent.execute(agentCtx);
    }

    /**
     * 构造节点的 AgentContext（基于上下文的 agentContext 模板）。
     */
    private AgentContext buildAgentContext(DagNode node, DagExecutionContext ctx) {
        AgentContext template = ctx.getAgentContext();
        if (template == null) {
            return new AgentContext(node.getAgentType(), ctx.getInstanceId(), node.getName(),
                    null, null, "dag", new HashMap<>());
        }
        AgentContext child = new AgentContext(
                template.getBizType() != null ? template.getBizType() : node.getAgentType(),
                template.getBizId() != null ? template.getBizId() : ctx.getInstanceId(),
                template.getBizRef() != null ? template.getBizRef() : node.getName(),
                template.getCallerId(), template.getCallerName(),
                template.getSource() != null ? template.getSource() : "dag",
                template.getParams() != null ? new HashMap<>(template.getParams()) : new HashMap<>(),
                template.getTraceId(), template.getProviderTraceId());
        return child;
    }

    /**
     * 求值 SpEL 条件表达式。
     *
     * <p>以共享变量 Map 作为求值根对象，支持 {@code #amount > 100}、
     * {@code ['riskLevel'] == 'HIGH'} 等表达式。
     *
     * @param expression SpEL 表达式
     * @param ctx        执行上下文
     * @return true 表示条件满足；解析异常时返回 false（保守跳过）
     */
    private boolean evaluateCondition(String expression, DagExecutionContext ctx) {
        try {
            Expression exp = spelParser.parseExpression(expression);
            EvaluationContext evalCtx = new StandardEvaluationContext(ctx.getSharedVariables());
            Boolean result = exp.getValue(evalCtx, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("[DAG:{}] 条件表达式求值失败，默认 false: {} ({})", ctx.getDefinition().getName(),
                    expression, e.getMessage());
            return false;
        }
    }

    /**
     * 解析节点失败策略（节点级优先，回退到 DAG 级）。
     */
    private DagFailureStrategy resolveFailureStrategy(DagNode node, DagDefinition dag) {
        if (node != null && node.getFailureStrategy() != null) {
            return node.getFailureStrategy();
        }
        return dag.getFailureStrategy() != null ? dag.getFailureStrategy() : DagFailureStrategy.ABORT;
    }

    /**
     * 解析节点最大重试次数。
     */
    private int resolveMaxRetries(DagNode node, DagDefinition dag) {
        if (node != null && node.getMaxRetries() != null) {
            return node.getMaxRetries();
        }
        return dag.getMaxRetries() != null ? dag.getMaxRetries() : 3;
    }

    /**
     * 解析节点超时时间。
     */
    private long resolveTimeoutMs(DagNode node, DagDefinition dag) {
        if (node != null && node.getTimeoutMs() > 0) {
            return node.getTimeoutMs();
        }
        return dag.getDefaultTimeoutMs();
    }

    /**
     * 汇总输出（用于追踪日志，避免大对象）。
     */
    private String summarizeOutput(Object output) {
        if (output == null) {
            return "null";
        }
        String str = output.toString();
        return str.length() > 200 ? str.substring(0, 200) + "..." : str;
    }

    /**
     * 决定 DAG 最终状态。
     */
    private DagInstanceStatus resolveFinalStatus(DagExecutionContext ctx, boolean aborted) {
        if (aborted) {
            return DagInstanceStatus.FAILED;
        }
        if (ctx.hasFailedNode()) {
            return DagInstanceStatus.FAILED;
        }
        return DagInstanceStatus.SUCCESS;
    }

    /**
     * 构造最终结果。
     */
    private DagExecutionResult buildResult(DagExecutionContext ctx, DagDefinition dag,
                                            DagInstanceStatus status, long totalCost, String note) {
        Map<String, DagNodeStatus> statuses = ctx.snapshotStatuses();
        int success = 0, failed = 0, skipped = 0;
        for (DagNodeStatus s : statuses.values()) {
            switch (s) {
                case SUCCESS -> success++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
                default -> { }
            }
        }

        Map<String, String> errorMessages = new HashMap<>();
        for (DagNode node : dag.getNodes()) {
            Throwable err = ctx.getNodeError(node.getName());
            if (err != null) {
                errorMessages.put(node.getName(), err.getMessage());
            }
        }

        Map<String, Integer> retryCounts = new HashMap<>();
        for (DagNode node : dag.getNodes()) {
            retryCounts.put(node.getName(), ctx.getRetryCount(node.getName()));
        }

        return DagExecutionResult.builder()
                .instanceId(ctx.getInstanceId())
                .definitionId(dag.getId())
                .dagName(dag.getName())
                .status(status)
                .nodeStatuses(statuses)
                .nodeOutputs(ctx.snapshotOutputs())
                .nodeErrors(errorMessages)
                .nodeRetryCounts(retryCounts)
                .traces(List.copyOf(ctx.getTraces()))
                .totalCostMs(totalCost)
                .successCount(success)
                .failedCount(failed)
                .skippedCount(skipped)
                .totalNodes(dag.getNodes().size())
                .note(note)
                .build();
    }

    /** 节点执行结果枚举（内部用） */
    private enum NodeOutcome {
        /** 继续（成功或跳过或 CONTINUE 策略） */
        CONTINUE,
        /** 中止整个 DAG */
        ABORT
    }
}
