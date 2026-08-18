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
 * DAG 编排执行器（Node + Edge + State 图引擎）
 *
 * <p>P2-1 重构：统一的图执行引擎，替代原 DAG + Router 两种执行器。 基于 Node（执行单元）+ Edge（依赖边）+ State（节点结果状态）模式，支持：
 *
 * <ul>
 *   <li>并行执行无依赖节点
 *   <li>串行执行有依赖节点（Edge 定义依赖关系）
 *   <li>条件分支（CONDITION 节点，Edge 由条件结果选择）
 *   <li>循环迭代（LOOP 节点，Edge 回指形成环）
 *   <li>节点间数据传递（State：上游输出 → 下游输入）
 * </ul>
 *
 * <p>相比 RouterAgentExecutor（LLM 意图路由），本引擎通过 YAML DSL 显式定义执行图， 行为确定、可观测、可回放，无额外 LLM 调用成本。
 *
 * <p>支持：
 *
 * <ul>
 *   <li>并行执行无依赖节点
 *   <li>节点间数据传递（上游输出 → 下游输入）
 *   <li>虚拟线程并行（Java 21+）
 *   <li>失败快速终止
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DagOrchestrationExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(DagOrchestrationExecutor.class);

  private final LlmClient llmClient;
  private final AgentProperties properties;
  private final AgentFactory agentFactory;
  private final ExecutorService executor;

  /**
   * 构造 DAG 执行器（强制使用外部线程池）
   *
   * @param llmClient LLM 客户端
   * @param properties Agent 配置
   * @param agentFactory Agent 工厂
   * @param executor 外部线程池（由 common-thread 管理）
   */
  public DagOrchestrationExecutor(
      LlmClient llmClient,
      AgentProperties properties,
      AgentFactory agentFactory,
      ExecutorService executor) {
    this.llmClient = llmClient;
    this.properties = properties;
    this.agentFactory = agentFactory;
    this.executor = executor;
  }

  /**
   * 执行 DAG 编排
   *
   * <p>使用 CompletableFuture 依赖图拓扑排序，并行执行无依赖节点，串行执行有依赖节点。 总超时 5 分钟，任一节点失败则其下游节点自动跳过。
   *
   * @param dag DAG 定义
   * @param userInput 用户原始输入
   * @return 各节点执行结果
   */
  public DagExecutionResult execute(AgentDag dag, String userInput) {
    String executionId = IdGenerator.nextIdStr();
    LOG.info(
        "[DAG] 开始编排: id={}, name={}, nodes={}", executionId, dag.getName(), dag.getNodes().size());

    Map<String, String> nodeResults = new ConcurrentHashMap<>();
    Map<String, TokenUsage> nodeUsages = new ConcurrentHashMap<>();
    Set<String> completed = ConcurrentHashMap.newKeySet();
    Set<String> failed = ConcurrentHashMap.newKeySet();

    List<String> sortedNodeIds = topologicalSort(dag);
    Map<String, CompletableFuture<Void>> futureMap = new HashMap<>();

    // LOOP 循环体节点由 LOOP 节点在循环内调度，不参与主图调度，避免双重执行
    Set<String> loopBodyNodeIds = collectLoopBodyNodeIds(dag);

    for (String nodeId : sortedNodeIds) {
      // 循环体节点跳过主图调度（在 LOOP 节点内由 executeLoopNode 驱动执行）
      if (loopBodyNodeIds.contains(nodeId)) {
        continue;
      }
      AgentDag.Node node = dag.getNodes().get(nodeId);
      List<String> deps = dag.getEdges().getOrDefault(nodeId, List.of());

      CompletableFuture<Void> allDepsFuture =
          deps.stream()
              .map(dep -> futureMap.getOrDefault(dep, CompletableFuture.completedFuture(null)))
              .reduce(
                  CompletableFuture.completedFuture(null),
                  (f1, f2) -> f1.thenCombine(f2, (v1, v2) -> null));

      CompletableFuture<Void> nodeFuture =
          allDepsFuture.thenRunAsync(
              () ->
                  executeNodeLogic(
                      dag,
                      node,
                      userInput,
                      nodeResults,
                      nodeUsages,
                      completed,
                      failed,
                      executionId),
              executor);
      futureMap.put(nodeId, nodeFuture);
    }

    try {
      CompletableFuture.allOf(futureMap.values().toArray(new CompletableFuture[0]))
          // 整图总超时 300s（5 分钟）：任一节点超时即视为编排失败，避免长尾任务长期占用线程
          .orTimeout(300, TimeUnit.SECONDS)
          .join();
    } catch (CompletionException e) {
      if (e.getCause() instanceof TimeoutException) {
        LOG.error("[DAG] 编排超时: id={}", executionId);
      } else {
        LOG.error("[DAG] 编排异常: id={}, error={}", executionId, e.getMessage(), e);
      }
    }

    boolean hasFailed = !failed.isEmpty();
    LOG.info(
        "[DAG] 编排完成: id={}, completed={}, failed={}", executionId, completed.size(), failed.size());

    return new DagExecutionResult(
        executionId,
        dag.getName(),
        new HashMap<>(nodeResults),
        new HashMap<>(nodeUsages),
        completed,
        failed,
        hasFailed);
  }

  /** 默认节点超时（秒），当节点未配置 timeoutSeconds 时使用 */
  private static final int DEFAULT_NODE_TIMEOUT_SECONDS = 60;

  /** 执行单个节点的业务逻辑 */
  private void executeNodeLogic(
      AgentDag dag,
      AgentDag.Node node,
      String userInput,
      Map<String, String> results,
      Map<String, TokenUsage> usages,
      Set<String> completed,
      Set<String> failed,
      String executionId) {
    // 条件分支：若本节点属于某 CONDITION 节点未选中的分支，则跳过执行
    if (isBranchSkipped(dag, node, results)) {
      LOG.info("[DAG] 节点被条件分支排除，跳过: node={}", node.getId());
      return;
    }

    List<AgentDag.Node> deps = dag.getDependencies(node.getId());
    for (AgentDag.Node dep : deps) {
      if (failed.contains(dep.getId())) {
        LOG.warn("[DAG] 依赖节点失败，跳过: node={}, dep={}", node.getId(), dep.getId());
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
    // 获取节点级超时配置（优先节点 config，其次全局默认）
    int nodeTimeoutSeconds = getNodeTimeoutSeconds(node);
    LOG.info(
        "[DAG] 执行节点: id={}, type={}, timeout={}s",
        node.getId(),
        node.getAgentType(),
        nodeTimeoutSeconds);

    try {
      ChatRequest request =
          ChatRequest.builder()
              .model(properties.getLlm().getDefaultModel())
              .messages(
                  List.of(
                      ChatMessage.system(
                          node.getPrompt().isBlank() ? "你是 YDSZ 智能助手。" : node.getPrompt()),
                      ChatMessage.user(input, null)))
              .temperature(properties.getLlm().getTemperature())
              .maxTokens(properties.getLlm().getMaxTokens())
              .build();

      // 节点级超时：使用 CompletableFuture.orTimeout 为单个节点设置超时
      ChatResponse response =
          CompletableFuture.supplyAsync(() -> llmClient.chat(request), executor)
              .orTimeout(nodeTimeoutSeconds, TimeUnit.SECONDS)
              .join();

      results.put(node.getId(), response.getContent());
      if (response.getUsage() != null) {
        usages.put(node.getId(), response.getUsage());
      }
      completed.add(node.getId());
      LOG.info("[DAG] 节点完成: id={}", node.getId());
    } catch (CompletionException e) {
      if (e.getCause() instanceof TimeoutException) {
        LOG.error("[DAG] 节点超时: id={}, timeout={}s", node.getId(), nodeTimeoutSeconds);
        results.put(node.getId(), "[超时] 节点执行超过 " + nodeTimeoutSeconds + " 秒");
      } else {
        LOG.error("[DAG] 节点执行失败: id={}, error={}", node.getId(), e.getMessage(), e);
      }
      failed.add(node.getId());
    } catch (Exception e) {
      LOG.error("[DAG] 节点执行失败: id={}, error={}", node.getId(), e.getMessage(), e);
      failed.add(node.getId());
    }
  }

  /**
   * 收集所有 LOOP 节点声明的循环体节点 ID。
   *
   * <p>这些节点由 LOOP 节点在循环体内调度执行，不应再参与主图调度， 否则会被主图与循环体各执行一次（双重执行 + 结果竞态）。
   *
   * @param dag DAG 定义
   * @return 循环体节点 ID 集合
   */
  private static Set<String> collectLoopBodyNodeIds(AgentDag dag) {
    Set<String> loopBodyNodeIds = new HashSet<>();
    for (AgentDag.Node node : dag.getNodes().values()) {
      String nodeType = (String) node.getConfig().getOrDefault("nodeType", "AGENT");
      if ("LOOP".equalsIgnoreCase(nodeType)) {
        String loopBody = (String) node.getConfig().getOrDefault("loopBody", "");
        if (!loopBody.isBlank()) {
          for (String bodyId : loopBody.split(",")) {
            loopBodyNodeIds.add(bodyId.trim());
          }
        }
      }
    }
    return loopBodyNodeIds;
  }

  /**
   * 判断节点是否被条件分支排除。
   *
   * <p>遍历所有 CONDITION 节点：若本节点是该 CONDITION 的 trueBranch/falseBranch 之一， 但 CONDITION
   * 实际选中的分支不是本节点，则本节点应被跳过执行。
   *
   * <p>时序保证：拓扑序约束下 CONDITION 节点先于其分支节点执行（分支节点应通过 dependsOn 声明依赖），
   * 因此读取 {@code __BRANCH__<condId>} 时通常已就绪；若尚未就绪则保守不跳过。
   *
   * @param dag DAG 定义
   * @param node 待判断节点
   * @param results 节点执行结果映射
   * @return {@code true} 表示该节点属于未选中的分支，应跳过
   */
  private static boolean isBranchSkipped(AgentDag dag, AgentDag.Node node, Map<String, String> results) {
    for (AgentDag.Node candidate : dag.getNodes().values()) {
      String nodeType = (String) candidate.getConfig().getOrDefault("nodeType", "AGENT");
      if (!"CONDITION".equalsIgnoreCase(nodeType)) {
        continue;
      }
      String trueBranch = (String) candidate.getConfig().get("trueBranch");
      String falseBranch = (String) candidate.getConfig().get("falseBranch");
      if (trueBranch == null && falseBranch == null) {
        continue;
      }
      // 仅当本节点是某 CONDITION 的分支目标时才需要判断
      boolean isBranchTarget = node.getId().equals(trueBranch) || node.getId().equals(falseBranch);
      if (!isBranchTarget) {
        continue;
      }
      String chosen = results.get("__BRANCH__" + candidate.getId());
      if (chosen == null) {
        // CONDITION 尚未执行（正常情况下拓扑序已保证其先完成），保守不跳过
        continue;
      }
      if (!node.getId().equals(chosen)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 获取节点超时秒数。
   *
   * <p>优先从节点 {@code config.timeoutSeconds} 读取整数配置， 未配置时使用全局默认值 {@value
   * DEFAULT_NODE_TIMEOUT_SECONDS}s。
   *
   * @param node DAG 节点
   * @return 超时秒数
   */
  private int getNodeTimeoutSeconds(AgentDag.Node node) {
    Object timeoutObj = node.getConfig().get("timeoutSeconds");
    if (timeoutObj instanceof Number num) {
      int timeout = num.intValue();
      return timeout > 0 ? timeout : DEFAULT_NODE_TIMEOUT_SECONDS;
    }
    return DEFAULT_NODE_TIMEOUT_SECONDS;
  }

  /**
   * 执行条件分支节点
   *
   * <p>config 中需提供：
   *
   * <ul>
   *   <li>condition: 条件表达式（简单包含判断，如 "results['nodeId'].contains('yes')"）
   *   <li>trueBranch: 条件为真时的跳转节点 ID
   *   <li>falseBranch: 条件为假时的跳转节点 ID（可选）
   * </ul>
   */
  private void executeConditionNode(
      AgentDag.Node node,
      Map<String, String> results,
      Set<String> completed,
      Set<String> failed,
      String executionId) {
    String condition = (String) node.getConfig().get("condition");
    String trueBranch = (String) node.getConfig().get("trueBranch");
    String falseBranch = (String) node.getConfig().get("falseBranch");

    LOG.info("[DAG] 执行条件节点: id={}, condition={}", node.getId(), condition);

    boolean conditionResult = evaluateCondition(condition, results);
    String branchNodeId = conditionResult ? trueBranch : falseBranch;

    results.put(node.getId(), String.valueOf(conditionResult));
    completed.add(node.getId());

    if (branchNodeId != null) {
      results.put("__BRANCH__" + node.getId(), branchNodeId);
      LOG.info(
          "[DAG] 条件路由: node={}, result={}, branch={}", node.getId(), conditionResult, branchNodeId);
    }
  }

  /**
   * 执行循环节点
   *
   * <p>config 中需提供：
   *
   * <ul>
   *   <li>loopCondition: 循环继续条件表达式
   *   <li>maxIterations: 最大迭代次数（默认 10）
   *   <li>loopBody: 循环体节点 ID 列表（逗号分隔）
   * </ul>
   */
  private void executeLoopNode(
      AgentDag dag,
      AgentDag.Node node,
      String userInput,
      Map<String, String> results,
      Map<String, TokenUsage> usages,
      Set<String> completed,
      Set<String> failed,
      String executionId) {
    String loopCondition = (String) node.getConfig().get("loopCondition");
    int maxIter =
        node.getConfig().containsKey("maxIterations")
            ? (Integer) node.getConfig().get("maxIterations")
            : 10; // 循环节点默认最多迭代 10 次，防止死循环耗尽资源
    String loopBodyStr = (String) node.getConfig().getOrDefault("loopBody", "");
    List<String> loopBodyNodes =
        loopBodyStr.isBlank() ? List.of() : List.of(loopBodyStr.split(","));

    LOG.info("[DAG] 执行循环节点: id={}, maxIterations={}", node.getId(), maxIter);

    int iteration = 0;
    while (iteration < maxIter) {
      if (!evaluateCondition(loopCondition, results)) {
        break;
      }
      LOG.info("[DAG] 循环迭代: node={}, iteration={}", node.getId(), iteration + 1);
      for (String bodyNodeId : loopBodyNodes) {
        AgentDag.Node bodyNode = dag.getNodes().get(bodyNodeId.trim());
        if (bodyNode != null && !failed.contains(bodyNodeId.trim())) {
          executeNodeLogic(
              dag, bodyNode, userInput, results, usages, completed, failed, executionId);
        }
      }
      iteration++;
    }

    results.put(node.getId(), "loop_completed_" + iteration + "_iterations");
    completed.add(node.getId());
    LOG.info("[DAG] 循环完成: node={}, iterations={}", node.getId(), iteration);
  }

  /**
   * 条件表达式求值。
   *
   * <p>委托给 {@link DagConditionEvaluator}，支持：
   *
   * <ul>
   *   <li>变量引用：results['nodeId']
   *   <li>字符串方法：.contains() / .equals() / .startsWith() / .endsWith() / .isEmpty() / .isNotEmpty()
   *   <li>逻辑运算：&amp;&amp; / || / !
   *   <li>比较运算：== / !=
   * </ul>
   *
   * @param condition 条件表达式
   * @param results 节点执行结果映射
   * @return 求值结果
   */
  private boolean evaluateCondition(String condition, Map<String, String> results) {
    return DagConditionEvaluator.evaluate(condition, results);
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

  private void topologicalVisit(
      AgentDag dag, String nodeId, Set<String> visited, Set<String> visiting, List<String> result) {
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

  /**
   * DAG 编排执行结果。
   *
   * @param executionId 本次执行的唯一 ID
   * @param dagName 执行的 DAG 名称
   * @param nodeResults 各节点的执行结果（节点名 → 输出内容）
   * @param nodeUsages 各节点的 Token 用量（节点名 → 用量）
   * @param completedNodes 成功完成的节点集合
   * @param failedNodes 执行失败的节点集合
   * @param hasFailure 是否存在失败节点（{@code true} 表示整体执行未完全成功）
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
