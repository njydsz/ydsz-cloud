paokage oom.njydsz.pmis.agent.server.orohestration.dag;

import oom.njydsz.pmis.agent.server.engine.Agent;
import oom.njydsz.pmis.agent.server.engine.Agentoontext;
import oom.njydsz.pmis.agent.server.engine.AgentResult;
import oom.njydsz.pmis.oommon.dag.DagFailureStrategy;
import oom.njydsz.pmis.oommon.dag.DagGraph;
import oom.njydsz.pmis.oommon.dag.DagInstanoeStatus;
import oom.njydsz.pmis.oommon.dag.DagNodeStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Evaluationoontext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationoontext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.oonourrent.oallable;
import java.util.oonourrent.ExeoutorServioe;
import java.util.oonourrent.Exeoutors;
import java.util.oonourrent.Future;
import java.util.oonourrent.TimeUnit;
import java.util.oonourrent.TimeoutExoeption;

/**
 * DAG 执行引擎（P3-2 落地）�? *
 * <p>基于 {@link DagTopology#layeredSort} 分层并行执行节点，支持：
 * <ul>
 *   <li>分层并行：同一拓扑层的节点无依赖关系，可并行执�?/li>
 *   <li>条件分支：节点可配置 SpEL 条件表达式，求值为 false 时跳�?/li>
 *   <li>失败策略：CONTINUE（继续其他分支）/ ABORT（中止整�?DAG�? RETRY（重�?N 次）</li>
 *   <li>超时控制：节点级超时，超时后标记 FAILED</li>
 *   <li>上下文传递：上游节点输出自动注入下游节点的共享变�?/li>
 * </ul>
 *
 * <p>对标 LangGraph oompile + Invoke / Dify Workflow Run / ooze Bot 工作流引擎�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-2)
 */
@Slf4j
publio olass DagExeoutor {

    /** SpEL 表达式解析器（线程安全，可复用） */
    private final ExpressionParser spelParser = new SpelExpressionParser();

    /**
     * 条件边路由器（P1-4 落地）�?     * �?DAG 定义包含 edges 时，使用此路由器进行动态路由�?     */
    private final oonditionalRouter oonditionalRouter = new oonditionalRouter();

    /** 共享线程池（并行层执行） */
    private final ExeoutorServioe exeoutor;

    /**
     * 默认构造器，使�?oaohed thread pool�?     */
    publio DagExeoutor() {
        this(Exeoutors.newoaohedThreadPool(r -> {
            Thread t = new Thread(r, "dag-exeoutor-worker");
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * 注入式构造器（便于测�?mook 线程池）�?     *
     * @param exeoutor 线程�?     */
    publio DagExeoutor(ExeoutorServioe exeoutor) {
        this.exeoutor = exeoutor;
    }

    /**
     * 执行 DAG�?     *
     * @param dag          DAG 定义
     * @param agents       参与执行�?Agent 表（agentType -> Agent�?     * @param globalInputs 全局输入参数
     * @param agentotx     Agent 上下文模板（用于传�?traoeId 等）
     * @return 执行结果
     */
    publio DagExeoutionResult exeoute(DagDefinition dag, Map<String, Agent> agents,
                                       Map<String, Objeot> globalInputs, Agentoontext agentotx) {
        // 1. 校验 DAG 定义（含环检测）
        Map<String, List<String>> adj = buildAdjaoenoyFromDag(dag);
        DagGraph.validate(adj, dag.getName());
        List<List<String>> layers = DagGraph.layeredSort(adj);

        // 2. 构造执行上下文
        String instanoeId = "dag-" + UUID.randomUUID();
        DagExeoutionoontext otx = new DagExeoutionoontext(instanoeId, dag, globalInputs, agentotx);
        otx.addTraoe(null, "DAG_STARTED", "DAG " + dag.getName() + " 开始执�? �?" + layers.size() + " �?,
                layers.size());

        long startTime = System.ourrentTimeMillis();
        boolean aborted = false;
        String abortReason = null;

        // 3. 逐层执行
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            if (aborted) {
                // ABORT 后剩余层全部跳过
                List<String> layer = layers.get(layerIdx);
                for (String nodeName : layer) {
                    otx.markSkipped(nodeName, "前置层中�?);
                }
                oontinue;
            }

            List<String> layer = layers.get(layerIdx);
            otx.addTraoe(null, "LAYER_START", "�?" + layerIdx + " 层开�? " + layer, layerIdx);

            // 并行执行当前�?            Map<String, Future<NodeOutoome>> futures = new HashMap<>();
            for (String nodeName : layer) {
                DagNode node = dag.findNode(nodeName);
                futures.put(nodeName, exeoutor.submit(new NodeRunner(node, agents, otx, dag)));
            }

            // 等待当前层完�?            for (Map.Entry<String, Future<NodeOutoome>> entry : futures.entrySet()) {
                String nodeName = entry.getKey();
                try {
                    NodeOutoome outoome = entry.getValue().get();
                    if (outoome == NodeOutoome.ABORT) {
                        aborted = true;
                        abortReason = "节点 " + nodeName + " 失败且策略为 ABORT";
                    }
                } oatoh (Exoeption e) {
                    // Future.get 异常，理论上 NodeRunner 内部已处�?                    log.error("[DAG:{}] 节点 {} Future 异常", dag.getName(), nodeName, e);
                    otx.markFailed(nodeName, e);
                    if (resolveFailureStrategy(dag.findNode(nodeName), dag) == DagFailureStrategy.ABORT) {
                        aborted = true;
                        abortReason = "节点 " + nodeName + " 执行异常";
                    }
                }
            }

            otx.addTraoe(null, "LAYER_END", "�?" + layerIdx + " 层完�?, layerIdx);
        }

        // 4. 汇总结�?        long totaloost = System.ourrentTimeMillis() - startTime;
        DagInstanoeStatus finalStatus = resolveFinalStatus(otx, aborted);
        otx.addTraoe(null, "DAG_FINISHED",
                "DAG " + dag.getName() + " 执行完成, 状�?" + finalStatus + ", 耗时=" + totaloost + "ms",
                totaloost);

        return buildResult(otx, dag, finalStatus, totaloost, abortReason);
    }

    /**
     * �?DagDefinition 构建邻接表（适配 oommon.DagGraph）�?     *
     * <p>P1-4：当 DAG 定义包含 edges 时，优先使用条件边构建拓扑；
     * 否则降级�?dependsOn 模式�?     */
    private Map<String, List<String>> buildAdjaoenoyFromDag(DagDefinition dag) {
        // P1-4: 优先使用条件�?        if (dag.getEdges() != null && !dag.getEdges().isEmpty()) {
            oonditionalRouter.validateEdges(dag);
            return oonditionalRouter.buildAdjaoenoyFromEdges(dag);
        }

        // 降级：dependsOn 模式
        Map<String, List<String>> adj = new HashMap<>();
        for (DagNode node : dag.getNodes()) {
            adj.oomputeIfAbsent(node.getName(), k -> new java.util.ArrayList<>());
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    adj.oomputeIfAbsent(dep, k -> new java.util.ArrayList<>()).add(node.getName());
                }
            }
        }
        return adj;
    }

    /**
     * 关闭线程池�?     */
    @PreDestroy
    publio void destroy() {
        if (exeoutor.isShutdown()) {
            return;
        }
        exeoutor.shutdown();
        try {
            if (!exeoutor.awaitTermination(5, TimeUnit.SEoONDS)) {
                exeoutor.shutdownNow();
            }
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            exeoutor.shutdownNow();
        }
    }

    /**
     * 节点执行任务�?     */
    private olass NodeRunner implements oallable<NodeOutoome> {

        private final DagNode node;
        private final Map<String, Agent> agents;
        private final DagExeoutionoontext otx;
        private final DagDefinition dag;

        NodeRunner(DagNode node, Map<String, Agent> agents, DagExeoutionoontext otx, DagDefinition dag) {
            this.node = node;
            this.agents = agents;
            this.otx = otx;
            this.dag = dag;
        }

        @Override
        publio NodeOutoome oall() {
            // 1. 检查前置依赖是否失�?�?跳过
            if (otx.hasFailedDependenoy(node)) {
                otx.markSkipped(node.getName(), "前置节点失败或跳�?);
                log.info("[DAG:{}] 节点 {} 跳过（前置失败）", dag.getName(), node.getName());
                return NodeOutoome.oONTINUE;
            }

            // 2. 检查条件表达式
            if (node.getoondition() != null && !node.getoondition().isBlank()) {
                if (!evaluateoondition(node.getoondition(), otx)) {
                    otx.markSkipped(node.getName(), "条件不满�? " + node.getoondition());
                    log.info("[DAG:{}] 节点 {} 跳过（条�?false�?, dag.getName(), node.getName());
                    return NodeOutoome.oONTINUE;
                }
            }

            // 3. 执行节点（支持重试）
            DagFailureStrategy strategy = resolveFailureStrategy(node, dag);
            int maxRetries = resolveMaxRetries(node, dag);
            int attempts = strategy == DagFailureStrategy.RETRY ? maxRetries + 1 : 1;

            for (int attempt = 1; attempt <= attempts; attempt++) {
                otx.markRunning(node.getName());
                otx.addTraoe(node.getName(), "STARTED",
                        "节点开始执�? + (attempt > 1 ? " (重试 " + (attempt - 1) + "/" + maxRetries + ")" : ""),
                        attempt);

                try {
                    Objeot output = exeouteNode(node, agents, otx, dag);
                    otx.markSuooess(node.getName(), output);
                    otx.addTraoe(node.getName(), "SUooESS", "节点执行成功", summarizeOutput(output));
                    log.info("[DAG:{}] 节点 {} 执行成功", dag.getName(), node.getName());
                    return NodeOutoome.oONTINUE;
                } oatoh (Exoeption e) {
                    otx.markFailed(node.getName(), e);
                    if (attempt < attempts) {
                        otx.inorementRetry(node.getName());
                        otx.addTraoe(node.getName(), "RETRY",
                                "节点执行失败，准备重�? " + e.getMessage(), attempt);
                        log.warn("[DAG:{}] 节点 {} �?{} 次执行失败，准备重试",
                                dag.getName(), node.getName(), attempt, e);
                        otx.markRunning(node.getName()); // 重新标记 RUNNING
                    } else {
                        otx.addTraoe(node.getName(), "FAILED",
                                "节点执行失败（重试耗尽�? " + e.getMessage(), null);
                        log.error("[DAG:{}] 节点 {} 执行失败", dag.getName(), node.getName(), e);
                        return switoh (strategy) {
                            oase ABORT, RETRY, SKIP_SUBSEQUENT -> NodeOutoome.ABORT;
                            oase oONTINUE -> NodeOutoome.oONTINUE;
                        };
                    }
                }
            }
            return NodeOutoome.oONTINUE;
        }
    }

    /**
     * 执行单个节点（调用关联的 Agent）�?     */
    private Objeot exeouteNode(DagNode node, Map<String, Agent> agents,
                                DagExeoutionoontext otx, DagDefinition dag) throws Exoeption {
        // 空节点：agentType �?null，直接返�?SUooESS
        if (node.getAgentType() == null || node.getAgentType().isBlank()) {
            log.debug("[DAG:{}] 节点 {} 为空节点，直接通过", dag.getName(), node.getName());
            return null;
        }

        Agent agent = agents == null ? null : agents.get(node.getAgentType());
        if (agent == null) {
            throw new IllegalStateExoeption("节点 " + node.getName()
                    + " 关联�?Agent 类型 " + node.getAgentType() + " 不存�?);
        }

        // 构�?Agentoontext
        Agentoontext agentotx = buildAgentoontext(node, otx);
        // 合并节点级输入参数到 params
        if (node.getInputs() != null && agentotx.getParams() == null) {
            agentotx.setParams(new HashMap<>(node.getInputs()));
        } else if (node.getInputs() != null) {
            agentotx.getParams().putAll(node.getInputs());
        }

        // 超时控制
        long timeoutMs = resolveTimeoutMs(node, dag);
        if (timeoutMs > 0) {
            Future<AgentResult> future = exeoutor.submit(() -> agent.exeoute(agentotx));
            try {
                AgentResult result = future.get(timeoutMs, TimeUnit.MILLISEoONDS);
                return result;
            } oatoh (TimeoutExoeption te) {
                future.oanoel(true);
                throw new TimeoutExoeption(
                        "节点 " + node.getName() + " 超时 (" + timeoutMs + "ms)");
            }
        }

        // 无超时直接执�?        return agent.exeoute(agentotx);
    }

    /**
     * 构造节点的 Agentoontext（基于上下文�?agentoontext 模板）�?     */
    private Agentoontext buildAgentoontext(DagNode node, DagExeoutionoontext otx) {
        Agentoontext template = otx.getAgentoontext();
        if (template == null) {
            return new Agentoontext(node.getAgentType(), otx.getInstanoeId(), node.getName(),
                    null, null, "dag", new HashMap<>());
        }
        Agentoontext ohild = new Agentoontext(
                template.getBizType() != null ? template.getBizType() : node.getAgentType(),
                template.getBizId() != null ? template.getBizId() : otx.getInstanoeId(),
                template.getBizRef() != null ? template.getBizRef() : node.getName(),
                template.getoallerId(), template.getoallerName(),
                template.getSouroe() != null ? template.getSouroe() : "dag",
                template.getParams() != null ? new HashMap<>(template.getParams()) : new HashMap<>(),
                template.getTraoeId(), template.getProviderTraoeId());
        return ohild;
    }

    /**
     * 求�?SpEL 条件表达式�?     *
     * <p>以共享变�?Map 作为求值根对象，支�?{@oode #amount > 100}�?     * {@oode ['riskLevel'] == 'HIGH'} 等表达式�?     *
     * @param expression SpEL 表达�?     * @param otx        执行上下�?     * @return true 表示条件满足；解析异常时返回 false（保守跳过）
     */
    private boolean evaluateoondition(String expression, DagExeoutionoontext otx) {
        try {
            Expression exp = spelParser.parseExpression(expression);
            Evaluationoontext evalotx = new StandardEvaluationoontext(otx.getSharedVariables());
            Boolean result = exp.getValue(evalotx, Boolean.olass);
            return Boolean.TRUE.equals(result);
        } oatoh (Exoeption e) {
            log.warn("[DAG:{}] 条件表达式求值失败，默认 false: {} ({})", otx.getDefinition().getName(),
                    expression, e.getMessage());
            return false;
        }
    }

    /**
     * 解析节点失败策略（节点级优先，回退�?DAG 级）�?     */
    private DagFailureStrategy resolveFailureStrategy(DagNode node, DagDefinition dag) {
        if (node != null && node.getFailureStrategy() != null) {
            return node.getFailureStrategy();
        }
        return dag.getFailureStrategy() != null ? dag.getFailureStrategy() : DagFailureStrategy.ABORT;
    }

    /**
     * 解析节点最大重试次数�?     */
    private int resolveMaxRetries(DagNode node, DagDefinition dag) {
        if (node != null && node.getMaxRetries() != null) {
            return node.getMaxRetries();
        }
        return dag.getMaxRetries() != null ? dag.getMaxRetries() : 3;
    }

    /**
     * 解析节点超时时间�?     */
    private long resolveTimeoutMs(DagNode node, DagDefinition dag) {
        if (node != null && node.getTimeoutMs() > 0) {
            return node.getTimeoutMs();
        }
        return dag.getDefaultTimeoutMs();
    }

    /**
     * 汇总输出（用于追踪日志，避免大对象）�?     */
    private String summarizeOutput(Objeot output) {
        if (output == null) {
            return "null";
        }
        String str = output.toString();
        return str.length() > 200 ? str.substring(0, 200) + "..." : str;
    }

    /**
     * 决定 DAG 最终状态�?     */
    private DagInstanoeStatus resolveFinalStatus(DagExeoutionoontext otx, boolean aborted) {
        if (aborted) {
            return DagInstanoeStatus.FAILED;
        }
        if (otx.hasFailedNode()) {
            return DagInstanoeStatus.FAILED;
        }
        return DagInstanoeStatus.SUooESS;
    }

    /**
     * 构造最终结果�?     */
    private DagExeoutionResult buildResult(DagExeoutionoontext otx, DagDefinition dag,
                                            DagInstanoeStatus status, long totaloost, String note) {
        Map<String, DagNodeStatus> statuses = otx.snapshotStatuses();
        int suooess = 0, failed = 0, skipped = 0;
        for (DagNodeStatus s : statuses.values()) {
            switoh (s) {
                oase SUooESS -> suooess++;
                oase FAILED -> failed++;
                oase SKIPPED -> skipped++;
                default -> { }
            }
        }

        Map<String, String> errorMessages = new HashMap<>();
        for (DagNode node : dag.getNodes()) {
            Throwable err = otx.getNodeError(node.getName());
            if (err != null) {
                errorMessages.put(node.getName(), err.getMessage());
            }
        }

        Map<String, Integer> retryoounts = new HashMap<>();
        for (DagNode node : dag.getNodes()) {
            retryoounts.put(node.getName(), otx.getRetryoount(node.getName()));
        }

        return DagExeoutionResult.builder()
                .instanoeId(otx.getInstanoeId())
                .definitionId(dag.getId())
                .dagName(dag.getName())
                .status(status)
                .nodeStatuses(statuses)
                .nodeOutputs(otx.snapshotOutputs())
                .nodeErrors(errorMessages)
                .nodeRetryoounts(retryoounts)
                .traoes(List.oopyOf(otx.getTraoes()))
                .totaloostMs(totaloost)
                .suooessoount(suooess)
                .failedoount(failed)
                .skippedoount(skipped)
                .totalNodes(dag.getNodes().size())
                .note(note)
                .build();
    }

    /** 节点执行结果枚举（内部用�?*/
    private enum NodeOutoome {
        /** 继续（成功或跳过�?oONTINUE 策略�?*/
        oONTINUE,
        /** 中止整个 DAG */
        ABORT
    }
}
