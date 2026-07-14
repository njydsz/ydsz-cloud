package com.njydsz.pmis.agent.server.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.agent.AgentDag;
import com.njydsz.pmis.agent.domain.gateway.LlmClient;
import com.njydsz.pmis.agent.domain.model.ChatMessage;
import com.njydsz.pmis.agent.domain.model.ChatRequest;
import com.njydsz.pmis.agent.domain.model.ChatResponse;
import com.njydsz.pmis.agent.domain.model.TokenUsage;
import com.njydsz.pmis.agent.server.config.AgentProperties;

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
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public class DagOrchestrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(DagOrchestrationExecutor.class);

    private final LlmClient llmClient;
    private final AgentProperties properties;
    private final AgentFactory agentFactory;
    private final ExecutorService executor;

    public DagOrchestrationExecutor(LlmClient llmClient, AgentProperties properties,
                                     AgentFactory agentFactory) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.agentFactory = agentFactory;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
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
        String executionId = UUID.randomUUID().toString();
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
                    .orTimeout(300, TimeUnit.SECONDS)
                    .join();
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("[DAG] 编排超时: id={}", executionId);
        } catch (Exception e) {
            log.error("[DAG] 编排异常: id={}, error={}", executionId, e.getMessage(), e);
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

        String input = buildNodeInput(node, userInput, results);
        log.info("[DAG] 执行节点: id={}, type={}", node.getId(), node.getAgentType());

        try {
            ChatRequest request = ChatRequest.builder()
                    .model(properties.getLlm().getDefaultModel())
                    .messages(List.of(
                            ChatMessage.system(node.getPrompt().isBlank()
                                    ? "你是 PMIS 智能助手。" : node.getPrompt()),
                            ChatMessage.user(input, null)))
                    .temperature(properties.getLlm().getTemperature())
                    .maxTokens(properties.getLlm().getMaxTokens())
                    .build();

            ChatResponse response = llmClient.chat(request);
            results.put(node.getId(), response.getContent());
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
                String upstreamResult = results.get(trimmed);
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

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    public record DagExecutionResult(
            String executionId,
            String dagName,
            Map<String, String> nodeResults,
            Map<String, TokenUsage> nodeUsages,
            Set<String> completedNodes,
            Set<String> failedNodes,
            boolean hasFailure) {}
}
