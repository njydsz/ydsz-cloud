package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.agent.AgentDag;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.common.util.id.IdGenerator;

/**
 * DAG 编排执行器
 *
 * <p>根据 {@link AgentDag} 描述的有向无环图，并行执行无依赖的节点，
 * 串行执行有依赖的节点。每个节点是一次 LLM 调用。
 *
 * <p>支持：
 * <ul>
 *   <li>并行执行无依赖节点</li>
 *   <li>节点间数据传递（上游输出 → 下游输入）</li>
 *   <li>虚拟线程并行（Java 21+）</li>
 *   <li>失败快速终止</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DagOrchestrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagOrchestrationExecutor.class);

    private final LlmClient llmClient;
    private final AgentProperties properties;
    private final AgentFactory agentFactory;
    private final ExecutorService executor;

    /**
     * 构造 DAG 执行器（强制使用外部线程池）
     *
     * @param llmClient    LLM 客户端
     * @param properties   Agent 配置
     * @param agentFactory Agent 工厂
     * @param executor     外部线程池（由 common-thread 管理）
     */
    public DagOrchestrationExecutor(LlmClient llmClient, AgentProperties properties,
                                     AgentFactory agentFactory, ExecutorService executor) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.agentFactory = agentFactory;
        this.executor = executor;
    }

    /**
     * 执行 DAG 编排
     *
     * <p>使用 CompletableFuture 依赖图拓扑排序，并行执行无依赖节点，串行执行有依赖节点。
     * 总超时 5 分钟，任一节点失败则其下游节点自动跳过。
     *
     * @param dag       DAG 定义
     * @param userInput 用户原始输入
     * @return 各节点执行结果
     */
    public DagExecutionResult execute(AgentDag dag, String userInput) {
        String executionId = IdGenerator.nextIdStr();
        log.info("[DAG] 开始编排: id={}, name={}, nodes={}",
                executionId, dag.getName(), dag.getNodes().size());

        Map<String, String> nodeResults = new ConcurrentHashMap<>();
        Map<String, TokenUsage> nodeUsages = new ConcurrentHashMap<>();
        Set<String> completed = ConcurrentHashMap.newKeySet();
        Set<String> failed = ConcurrentHashMap.newKeySet();

        List<String> sortedNodeIds = topologicalSort(dag);
        Map<String, CompletableFuture<Void>> futureMap = new HashMap<>();

        for (String nodeId : sortedNodeIds) {
            AgentDag.Node node = dag.getNodes().get(nodeId);
            List<String> deps = dag.getEdges().getOrDefault(nodeId, List.of());

            CompletableFuture<Void> allDepsFuture = deps.stream()
                    .map(dep -> futureMap.getOrDefault(dep, CompletableFuture.completedFuture(null)))
                    .reduce(CompletableFuture.completedFuture(null),
                            (f1, f2) -> f1.thenCombine(f2, (v1, v2) -> null));

            CompletableFuture<Void> nodeFuture = allDepsFuture
                    .thenRunAsync(() -> executeNodeLogic(dag, node, userInput, nodeResults,
                            nodeUsages, completed, failed, executionId), executor);
            futureMap.put(nodeId, nodeFuture);
        }

        try {
            CompletableFuture.allOf(futureMap.values().toArray(new CompletableFuture[0]))
                    // 整图总超时 300s（5 分钟）：任一节点超时即视为编排失败，避免长尾任务长期占用线程
                    .orTimeout(300, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                log.error("[DAG] 编排超时: id={}", executionId);
            } else {
                log.error("[DAG] 编排异常: id={}, error={}", executionId, e.getMessage(), e);
            }
        }

        boolean hasFailed = !failed.isEmpty();
        log.info("[DAG] 编排完成: id={}, completed={}, failed={}",
                executionId, completed.size(), failed.size());

        return new DagExecutionResult(executionId, dag.getName(),
                new HashMap<>(nodeResults), new HashMap<>(nodeUsages),
                completed, failed, hasFailed);
    }

    /**
     * 执行单个节点的业务逻辑
     */
    private void executeNodeLogic(AgentDag dag, AgentDag.Node node, String userInput,
                                   Map<String, String> results,
                                   Map<String, TokenUsage> usages,
                                   Set<String> completed,
                                   Set<String> failed,
                                   String executionId) {
        List<AgentDag.Node> deps = dag.getDependencies(node.getId());
        for (AgentDag.Node dep : deps) {
            if (failed.contains(dep.getId())) {
                log.warn("[DAG] 依赖节点失败，跳过: node={}, dep={}", node.getId(), dep.getId());
                failed.add(node.getId());
                return;
            }
        }

        String nodeType = (String) node.getConfig().getOrDefault("nodeType", "AGENT");
        if ("CONDITION".equalsIgnoreCase(nodeType)) {
            executeConditionNode(node, results, completed, failed, executionId);
            return;
        }
        if ("LOOP".equalsIgnoreCase(nodeType)) {
            executeLoopNode(dag, node, userInput, results, usages, completed, failed, executionId);
            return;
        }

        String input = buildNodeInput(node, userInput, results);
        log.info("[DAG] 执行节点: id={}, type={}", node.getId(), node.getAgentType());

        try {
            ChatRequest request = ChatRequest.builder()
                    .model(properties.getLlm().getDefaultModel())
                    .messages(List.of(
                            ChatMessage.system(node.getPrompt().isBlank()
                                    ? "你是 YDSZ 智能助手。" : node.getPrompt()),
                            ChatMessage.user(input, null)))
                    .temperature(properties.getLlm().getTemperature())
                    .maxTokens(properties.getLlm().getMaxTokens())
                    .build();

            ChatResponse response = llmClient.chat(request);
            Response.put(node.getId(), response.getContent());
            if (response.getUsage() != null) {
                usages.put(node.getId(), response.getUsage());
            }
            completed.add(node.getId());
            log.info("[DAG] 节点完成: id={}", node.getId());
        } catch (Exception e) {
            log.error("[DAG] 节点执行失败: id={}, error={}", node.getId(), e.getMessage(), e);
            failed.add(node.getId());
        }
    }

    /**
     * 执行条件分支节点
     *
     * <p>config 中需提供：
     * <ul>
     *   <li>condition: 条件表达式（简单包含判断，如 "results['nodeId'].contains('yes')"）</li>
     *   <li>trueBranch: 条件为真时的跳转节点 ID</li>
     *   <li>falseBranch: 条件为假时的跳转节点 ID（可选）</li>
     * </ul>
     */
    private void executeConditionNode(AgentDag.Node node, Map<String, String> results,
                                      Set<String> completed, Set<String> failed,
                                      String executionId) {
        String condition = (String) node.getConfig().get("condition");
        String trueBranch = (String) node.getConfig().get("trueBranch");
        String falseBranch = (String) node.getConfig().get("falseBranch");

        log.info("[DAG] 执行条件节点: id={}, condition={}", node.getId(), condition);

        boolean conditionResult = evaluateCondition(condition, results);
        String branchNodeId = conditionResult ? trueBranch : falseBranch;

        Response.put(node.getId(), String.valueOf(conditionResult));
        completed.add(node.getId());

        if (branchNodeId != null) {
            Response.put("__BRANCH__" + node.getId(), branchNodeId);
            log.info("[DAG] 条件路由: node={}, result={}, branch={}",
                    node.getId(), conditionResult, branchNodeId);
        }
    }

    /**
     * 执行循环节点
     *
     * <p>config 中需提供：
     * <ul>
     *   <li>loopCondition: 循环继续条件表达式</li>
     *   <li>maxIterations: 最大迭代次数（默认 10）</li>
     *   <li>loopBody: 循环体节点 ID 列表（逗号分隔）</li>
     * </ul>
     */
    private void executeLoopNode(AgentDag dag, AgentDag.Node node, String userInput,
                                  Map<String, String> results,
                                  Map<String, TokenUsage> usages,
                                  Set<String> completed, Set<String> failed,
                                  String executionId) {
        String loopCondition = (String) node.getConfig().get("loopCondition");
        int maxIter = node.getConfig().containsKey("maxIterations")
                ? (Integer) node.getConfig().get("maxIterations") : 10; // 循环节点默认最多迭代 10 次，防止死循环耗尽资源
        String loopBodyStr = (String) node.getConfig().getOrDefault("loopBody", "");
        List<String> loopBodyNodes = loopBodyStr.isBlank()
                ? List.of() : List.of(loopBodyStr.split(","));

        log.info("[DAG] 执行循环节点: id={}, maxIterations={}", node.getId(), maxIter);

        int iteration = 0;
        while (iteration < maxIter) {
            if (!evaluateCondition(loopCondition, results)) {
                break;
            }
            log.info("[DAG] 循环迭代: node={}, iteration={}", node.getId(), iteration + 1);
            for (String bodyNodeId : loopBodyNodes) {
                AgentDag.Node bodyNode = dag.getNodes().get(bodyNodeId.trim());
                if (bodyNode != null && !failed.contains(bodyNodeId.trim())) {
                    executeNodeLogic(dag, bodyNode, userInput, results, usages,
                            completed, failed, executionId);
                }
            }
            iteration++;
        }

        Response.put(node.getId(), "loop_completed_" + iteration + "_iterations");
        completed.add(node.getId());
        log.info("[DAG] 循环完成: node={}, iterations={}", node.getId(), iteration);
    }

    /**
     * 简单条件求值（支持 contains/equals/startsWith 等字符串操作）
     */
    private boolean evaluateCondition(String condition, Map<String, String> results) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        try {
            String expr = condition.trim();
            if (expr.contains(".contains(")) {
                int idx = expr.indexOf(".contains(\"");
                String varPart = expr.substring(0, idx);
                String valuePart = expr.substring(idx + 11, expr.indexOf("\")"));
                String resolved = resolveVariable(varPart.trim(), results);
                return resolved.contains(valuePart);
            }
            if (expr.contains(".equals(")) {
                int idx = expr.indexOf(".equals(\"");
                String varPart = expr.substring(0, idx);
                String valuePart = expr.substring(idx + 9, expr.indexOf("\")"));
                String resolved = resolveVariable(varPart.trim(), results);
                return resolved.equals(valuePart);
            }
            if (expr.contains(".startsWith(")) {
                int idx = expr.indexOf(".startsWith(\"");
                String varPart = expr.substring(0, idx);
                String valuePart = expr.substring(idx + 13, expr.indexOf("\")"));
                String resolved = resolveVariable(varPart.trim(), results);
                return resolved.startsWith(valuePart);
            }
            return Boolean.parseBoolean(expr);
        } catch (Exception e) {
            log.warn("[DAG] 条件求值失败: condition={}, error={}", condition, e.getMessage());
            return false;
        }
    }

    private String resolveVariable(String varExpr, Map<String, String> results) {
        if (varExpr.startsWith("results['") || varExpr.startsWith("results[\"")) {
            String nodeId = varExpr.substring(9, varExpr.length() - 2);
            return Response.getOrDefault(nodeId, "");
        }
        return Response.getOrDefault(varExpr, varExpr);
    }

    /**
     * 拓扑排序 DAG 节点，确保依赖节点在前
     *
     * @throws IllegalArgumentException DAG 存在环
     */
    private List<String> topologicalSort(AgentDag dag) {
        List<String> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeId : dag.getNodes().keySet()) {
            topologicalVisit(dag, nodeId, visited, visiting, result);
        }
        return result;
    }

    private void topologicalVisit(AgentDag dag, String nodeId, Set<String> visited,
                                   Set<String> visiting, List<String> result) {
        if (visited.contains(nodeId)) {
            return;
        }
        if (visiting.contains(nodeId)) {
            throw new IllegalArgumentException("DAG 存在环: " + nodeId);
        }
        visiting.add(nodeId);
        List<String> deps = dag.getEdges().getOrDefault(nodeId, List.of());
        for (String dep : deps) {
            topologicalVisit(dag, dep, visited, visiting, result);
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        result.add(nodeId);
    }

    private String buildNodeInput(AgentDag.Node node, String userInput, Map<String, String> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户需求: ").append(userInput);
        if (node.getInputFrom() != null && !node.getInputFrom().isBlank()) {
            String[] sources = node.getInputFrom().split(",");
            for (String source : sources) {
                String trimmed = source.trim();
                String upstreamResult = Response.get(trimmed);
                if (upstreamResult != null) {
                    sb.append("\n\n来自节点 [").append(trimmed).append("] 的结果:\n").append(upstreamResult);
                }
            }
        }
        return sb.toString();
    }

    private boolean allDepsCompleted(AgentDag dag, String nodeId, Set<String> completed) {
        List<String> deps = dag.getEdges().getOrDefault(nodeId, List.of());
        return completed.containsAll(deps);
    }

    /**
     * 容器销毁钩子，本执行器<b>不负责</b>关闭线程池。
     *
     * <p>{@code executor} 由 common-thread 统一托管生命周期并被多个组件共享，
     * 在此处 shutdown 会误伤其他使用方，因此方法体刻意留空。
     * 保留该钩子是为了显式声明这一约定，防止后续维护者误加关闭逻辑。
     */
    @PreDestroy
    public void shutdown() {
        // P0-3: executor 由 common-thread 管理生命周期，无需手动关闭
    }

    /**
     * DAG 编排执行结果。
     *
     * @param executionId    本次执行的唯一 ID
     * @param dagName        执行的 DAG 名称
     * @param nodeResults    各节点的执行结果（节点名 → 输出内容）
     * @param nodeUsages     各节点的 Token 用量（节点名 → 用量）
     * @param completedNodes 成功完成的节点集合
     * @param failedNodes    执行失败的节点集合
     * @param hasFailure     是否存在失败节点（{@code true} 表示整体执行未完全成功）
     */
    public record DagExecutionResult(
            String executionId,
            String dagName,
            Map<String, String> nodeResults,
            Map<String, TokenUsage> nodeUsages,
            Set<String> completedNodes,
            Set<String> failedNodes,
            boolean hasFailure) {}
}
