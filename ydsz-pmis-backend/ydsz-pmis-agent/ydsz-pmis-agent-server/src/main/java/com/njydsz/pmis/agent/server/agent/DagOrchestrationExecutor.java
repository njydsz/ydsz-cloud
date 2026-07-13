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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.agent.AgentDag;
import com.njydsz.pmis.agent.domain.agent.AgentExecutionRequest;
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

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AgentDag.Node node : dag.getRootNodes()) {
            futures.add(executeNode(dag, node, userInput, nodeResults, nodeUsages,
                    completed, failed, executionId));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        boolean hasFailed = !failed.isEmpty();
        log.info("[DAG] 编排完成: id={}, completed={}, failed={}",
                executionId, completed.size(), failed.size());

        return new DagExecutionResult(executionId, dag.getName(),
                new HashMap<>(nodeResults), new HashMap<>(nodeUsages),
                completed, failed, hasFailed);
    }

    private CompletableFuture<Void> executeNode(AgentDag dag, AgentDag.Node node,
                                                 String userInput,
                                                 Map<String, String> results,
                                                 Map<String, TokenUsage> usages,
                                                 Set<String> completed,
                                                 Set<String> failed,
                                                 String executionId) {
        return CompletableFuture.runAsync(() -> {
            List<AgentDag.Node> deps = dag.getDependencies(node.getId());
            for (AgentDag.Node dep : deps) {
                while (!completed.contains(dep.getId()) && !failed.contains(dep.getId())) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
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

                for (Map.Entry<String, List<String>> entry : dag.getEdges().entrySet()) {
                    if (entry.getValue().contains(node.getId()) && !completed.contains(entry.getKey())) {
                        AgentDag.Node next = dag.getNodes().get(entry.getKey());
                        if (next != null && allDepsCompleted(dag, next.getId(), completed)) {
                            executeNode(dag, next, userInput, results, usages,
                                    completed, failed, executionId);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[DAG] 节点执行失败: id={}, error={}", node.getId(), e.getMessage(), e);
                failed.add(node.getId());
            }
        }, executor);
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

    public void shutdown() {
        executor.shutdown();
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
