package com.njydsz.agent.server.agent;

import java.time.LocalDateTime;
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
import java.util.function.Consumer;

import com.njydsz.common.util.id.IdGenerator;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentDag;
import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.agent.DagCheckpoint;
import com.njydsz.agent.domain.agent.DagProgressEvent;
import com.njydsz.agent.domain.gateway.DagCheckpointStore;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
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
 * <p><b>AgentExecutor 适配（P1 修复）</b>：本类实现 {@link AgentExecutor} 接口，作为 DAG 类型 Agent
 * 纳入 {@link AgentFactory} 路由体系。通过 {@link AgentExecutionRequest#getVariables()} 中的 {@code dsl}
 * 字段传入 YAML 定义，userInput 作为编排输入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DagOrchestrationExecutor implements AgentExecutor {

  /** variables 中携带 DAG YAML 定义时使用的键 */
  private static final String VARIABLE_DSL_KEY = "dsl";

  private final LlmClient llmClient;
  private final AgentProperties properties;
  private final AgentFactory agentFactory;
  private final DagDslParser dagDslParser;
  private final ExecutorService executor;

  /** 检查点存储（可选依赖，Redis 不可用时降级） */
  private final DagCheckpointStore checkpointStore;

  /**
   * 构造 DAG 执行器（强制使用外部线程池）
   *
   * @param llmClient LLM 客户端
   * @param properties Agent 配置
   * @param agentFactory Agent 工厂
   * @param dagDslParser YAML DSL 解析器（AgentExecutor 适配入口使用）
   * @param executor 外部线程池（由 common-thread 管理）
   * @param checkpointStore 检查点存储（可为 null，null 时禁用断点续跑）
   */
  public DagOrchestrationExecutor(
      LlmClient llmClient,
      AgentProperties properties,
      AgentFactory agentFactory,
      DagDslParser dagDslParser,
      ExecutorService executor,
      DagCheckpointStore checkpointStore) {
    this.llmClient = llmClient;
    this.properties = properties;
    this.agentFactory = agentFactory;
    this.dagDslParser = dagDslParser;
    this.executor = executor;
    this.checkpointStore = checkpointStore;
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
    return execute(dag, userInput, null);
  }

  /**
   * 执行 DAG 编排（支持从检查点续跑）。
   *
   * <p>当 {@code resumeExecutionId} 非空时尝试加载已存在的检查点，跳过已成功的节点， 仅执行失败及未执行的节点；若检查点不存在则退化为全新执行。
   *
   * @param dag DAG 定义
   * @param userInput 用户原始输入
   * @param resumeExecutionId 续跑的执行 ID（null 表示全新执行）
   * @return 各节点执行结果
   */
  public DagExecutionResult execute(AgentDag dag, String userInput, String resumeExecutionId) {
    String executionId = resumeExecutionId != null ? resumeExecutionId : IdGenerator.nextIdStr();
    log.info(
        "[DAG] 开始编排: id={}, name={}, nodes={}, resume={}",
        executionId, dag.getName(), dag.getNodes().size(), resumeExecutionId != null);

    Map<String, String> nodeResults = new ConcurrentHashMap<>();
    Map<String, TokenUsage> nodeUsages = new ConcurrentHashMap<>();
    Set<String> completed = ConcurrentHashMap.newKeySet();
    Set<String> failed = ConcurrentHashMap.newKeySet();

    // P2-#8: 尝试加载检查点恢复中间状态
    if (resumeExecutionId != null) {
      loadCheckpoint(resumeExecutionId, nodeResults, completed, failed);
    }

    List<String> sortedNodeIds = topologicalSort(dag);
    Map<String, CompletableFuture<Void>> futureMap = new HashMap<>();

    // LOOP 循环体节点由 LOOP 节点在循环内调度，不参与主图调度，避免双重执行
    Set<String> loopBodyNodeIds = collectLoopBodyNodeIds(dag);

    for (String nodeId : sortedNodeIds) {
      // P2-#8: 检查点中已成功的节点跳过
      if (completed.contains(nodeId)) {
        log.info("[DAG] 跳过已完成节点: id={}", nodeId);
        // 为该节点创建一个已完成的占位 future，保证下游依赖正常
        futureMap.put(nodeId, CompletableFuture.completedFuture(null));
        continue;
      }
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
        log.error("[DAG] 编排超时: id={}", executionId);
      } else {
        log.error("[DAG] 编排异常: id={}, error={}", executionId, e.getMessage(), e);
      }
    }

    boolean hasFailed = !failed.isEmpty();
    log.info(
        "[DAG] 编排完成: id={}, completed={}, failed={}", executionId, completed.size(), failed.size());

    // P2-#8: 保存检查点（支持后续续跑）
    saveCheckpoint(executionId, dag.getName(), userInput, nodeResults, completed, failed);

    return new DagExecutionResult(
        executionId,
        dag.getName(),
        new HashMap<>(nodeResults),
        new HashMap<>(nodeUsages),
        completed,
        failed,
        hasFailed);
  }

  /**
   * 从检查点加载已完成的节点状态。
   *
   * <p>加载成功时恢复 nodeResults / completed / failed 三个集合，使续跑跳过已成功的节点。
   *
   * @param executionId 执行 ID
   * @param nodeResults 节点结果映射（输出参数）
   * @param completed 已完成节点集合（输出参数）
   * @param failed 失败节点集合（输出参数）
   */
  private void loadCheckpoint(
      String executionId,
      Map<String, String> nodeResults,
      Set<String> completed,
      Set<String> failed) {
    if (checkpointStore == null) {
      return;
    }
    try {
      checkpointStore
          .load(executionId)
          .ifPresent(
              cp -> {
                nodeResults.putAll(cp.getNodeResults());
                completed.addAll(cp.getCompletedNodes());
                failed.addAll(cp.getFailedNodes());
                log.info(
                    "[DAG] 加载检查点: executionId={}, completed={}, failed={}",
                    executionId,
                    cp.getCompletedNodes().size(),
                    cp.getFailedNodes().size());
              });
    } catch (Exception e) {
      log.warn("[DAG] 加载检查点失败，退化为全新执行: executionId={}, err={}", executionId, e.getMessage());
    }
  }

  /**
   * 保存当前执行状态的检查点快照。
   *
   * @param executionId 执行 ID
   * @param dagName DAG 名称
   * @param userInput 用户输入
   * @param nodeResults 节点结果映射
   * @param completed 已完成节点集合
   * @param failed 失败节点集合
   */
  private void saveCheckpoint(
      String executionId,
      String dagName,
      String userInput,
      Map<String, String> nodeResults,
      Set<String> completed,
      Set<String> failed) {
    if (checkpointStore == null) {
      return;
    }
    try {
      DagCheckpoint checkpoint =
          new DagCheckpoint(
              executionId,
              dagName,
              null,
              userInput,
              new HashMap<>(nodeResults),
              new HashSet<>(completed),
              new HashSet<>(failed),
              LocalDateTime.now());
      checkpointStore.save(checkpoint);
      log.debug("[DAG] 保存检查点: executionId={}, completed={}, failed={}", executionId, completed.size(), failed.size());
    } catch (Exception e) {
      log.warn("[DAG] 保存检查点失败: executionId={}, err={}", executionId, e.getMessage());
    }
  }

  /** 默认节点超时（秒），当节点未配置 timeoutSeconds 时使用 */
  private static final int DEFAULT_NODE_TIMEOUT_SECONDS = 60;

  /** 节点子 Agent 默认最大迭代次数（ReAct/Plan 循环兜底熔断） */
  private static final int DEFAULT_NODE_MAX_ITERATIONS = 10;

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
      log.info("[DAG] 节点被条件分支排除，跳过: node={}", node.getId());
      return;
    }

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
    // 获取节点级超时配置（优先节点 config，其次全局默认）
    int nodeTimeoutSeconds = getNodeTimeoutSeconds(node);
    log.info(
        "[DAG] 执行节点: id={}, type={}, timeout={}s",
        node.getId(),
        node.getAgentType(),
        nodeTimeoutSeconds);

    try {
      // P1 修复：按节点 agentType 路由到子 Agent 执行器（原实现忽略 agentType，统一走 LLM 直连，
      // 导致节点无法承载 ReAct/PlanExecute 等子 Agent 能力）
      AgentDefinition nodeAgentDef = buildNodeAgentDefinition(node);
      AgentExecutionRequest nodeRequest =
          AgentExecutionRequest.builder()
              .userInput(input)
              .conversationId(executionId)
              .systemPrompt(
                  node.getPrompt().isBlank()
                      ? properties.getDefaultSystemPrompt()
                      : node.getPrompt())
              .maxIterations(getNodeMaxIterations(node))
              .build();

      // 节点级超时：使用 CompletableFuture.orTimeout 为单个节点设置超时
      ChatResponse response =
          CompletableFuture.supplyAsync(
                  () -> agentFactory.getExecutor(nodeAgentDef).execute(nodeRequest), executor)
              .orTimeout(nodeTimeoutSeconds, TimeUnit.SECONDS)
              .join();

      results.put(node.getId(), response.getContent());
      if (response.getUsage() != null) {
        usages.put(node.getId(), response.getUsage());
      }
      completed.add(node.getId());
      log.info("[DAG] 节点完成: id={}", node.getId());
    } catch (CompletionException e) {
      if (e.getCause() instanceof TimeoutException) {
        log.error("[DAG] 节点超时: id={}, timeout={}s", node.getId(), nodeTimeoutSeconds);
        results.put(node.getId(), "[超时] 节点执行超过 " + nodeTimeoutSeconds + " 秒");
      } else {
        log.error("[DAG] 节点执行失败: id={}, error={}", node.getId(), e.getMessage(), e);
      }
      failed.add(node.getId());
    } catch (Exception e) {
      log.error("[DAG] 节点执行失败: id={}, error={}", node.getId(), e.getMessage(), e);
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
   * 构建节点对应的 {@link AgentDefinition}，供 AgentFactory 路由到子 Agent 执行器。
   *
   * @param node DAG 节点
   * @return Agent 定义（类型由节点 {@code agentType} 决定，未知类型回退 CHAT）
   */
  private AgentDefinition buildNodeAgentDefinition(AgentDag.Node node) {
    return new AgentDefinition(
        IdGenerator.nextIdStr(),
        node.getId(),
        node.getId(),
        resolveNodeAgentType(node.getAgentType()),
        node.getPrompt(),
        List.of(),
        properties.getLlm().getTemperature(),
        properties.getLlm().getMaxTokens(),
        getNodeMaxIterations(node),
        null);
  }

  /**
   * 解析节点 agentType 为 {@link AgentDefinition.Type}。
   *
   * <p>未知类型回退为 CHAT（单轮对话），保证节点必定可执行。
   *
   * @param agentType 节点声明的 agentType
   * @return Agent 类型枚举
   */
  private AgentDefinition.Type resolveNodeAgentType(String agentType) {
    if (agentType == null || agentType.isBlank()) {
      return AgentDefinition.Type.CHAT;
    }
    try {
      return AgentDefinition.Type.valueOf(agentType.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("[DAG] 未知节点 agentType: {}，回退为 CHAT", agentType);
      return AgentDefinition.Type.CHAT;
    }
  }

  /**
   * 获取节点 ReAct/Plan 循环最大迭代次数。
   *
   * <p>优先从节点 {@code config.maxIterations} 读取，未配置时使用默认 10 轮。
   *
   * @param node DAG 节点
   * @return 最大迭代次数
   */
  private int getNodeMaxIterations(AgentDag.Node node) {
    Object iterations = node.getConfig().get("maxIterations");
    if (iterations instanceof Number num) {
      int value = num.intValue();
      return value > 0 ? value : DEFAULT_NODE_MAX_ITERATIONS;
    }
    return DEFAULT_NODE_MAX_ITERATIONS;
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

    log.info("[DAG] 执行条件节点: id={}, condition={}", node.getId(), condition);

    boolean conditionResult = evaluateCondition(condition, results);
    String branchNodeId = conditionResult ? trueBranch : falseBranch;

    results.put(node.getId(), String.valueOf(conditionResult));
    completed.add(node.getId());

    if (branchNodeId != null) {
      results.put("__BRANCH__" + node.getId(), branchNodeId);
      log.info(
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
          executeNodeLogic(
              dag, bodyNode, userInput, results, usages, completed, failed, executionId);
        }
      }
      iteration++;
    }

    results.put(node.getId(), "loop_completed_" + iteration + "_iterations");
    completed.add(node.getId());
    log.info("[DAG] 循环完成: node={}, iterations={}", node.getId(), iteration);
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

  // -----------------------------------------------------------------------
  // AgentExecutor 适配（P1 修复：DAG 纳入 AgentFactory 路由体系）
  // -----------------------------------------------------------------------

  /**
   * 以 {@link AgentExecutor} 身份执行 DAG。
   *
   * <p>DAG 定义（YAML DSL）通过 {@link AgentExecutionRequest#getVariables()} 的 {@code dsl} 键传入；
   * 用户输入作为编排的根输入。执行结果按节点拼装为最终回复。
   *
   * @param request 执行请求（variables.dsl 必填）
   * @return 编排结果汇总响应
   * @throws IllegalArgumentException 当 variables 中缺少 dsl 定义时抛出
   */
  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    Object dslObj = request.getVariables().get(VARIABLE_DSL_KEY);
    if (!(dslObj instanceof String dsl) || dsl.isBlank()) {
      throw new IllegalArgumentException(
          "DAG 类型 Agent 执行时缺少 dsl 定义（request.variables.dsl）");
    }
    // P2-#8: 支持通过 variables.resumeExecutionId 触发续跑
    String resumeExecutionId = (String) request.getVariables().get("resumeExecutionId");
    AgentDag dag = dagDslParser.parse(dsl);
    DagExecutionResult result = execute(dag, request.getUserInput(), resumeExecutionId);
    return buildSummaryResponse(result);
  }

  /**
   * 流式执行 DAG：复用同步编排，完成后一次性推送结果（DAG 为图执行，无逐 token 流式语义）。
   *
   * @param request 执行请求
   * @param chunkConsumer 流式片段消费者
   */
  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    executeStream(request, chunkConsumer, null);
  }

  /**
   * 流式执行 DAG（带节点级进度推送）。
   *
   * <p>在编排执行过程中，按节点生命周期推送进度事件：DAG_STARTED → NODE_STARTED → NODE_COMPLETED / NODE_FAILED → DAG_COMPLETED。
   * {@code progressConsumer} 为 null 时退化为普通流式（仅推送最终结果）。
   *
   * @param request 执行请求
   * @param chunkConsumer 流式片段消费者
   * @param progressConsumer DAG 节点进度事件消费者（可为 null）
   */
  @Override
  public void executeStream(
      AgentExecutionRequest request,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<DagProgressEvent> progressConsumer) {
    Object dslObj = request.getVariables().get(VARIABLE_DSL_KEY);
    if (!(dslObj instanceof String dsl) || dsl.isBlank()) {
      throw new IllegalArgumentException(
          "DAG 类型 Agent 执行时缺少 dsl 定义（request.variables.dsl）");
    }
    String resumeExecutionId = (String) request.getVariables().get("resumeExecutionId");
    AgentDag dag = dagDslParser.parse(dsl);
    String executionId =
        resumeExecutionId != null ? resumeExecutionId : IdGenerator.nextIdStr();

    // P2-#14: 推送编排启动事件
    if (progressConsumer != null) {
      progressConsumer.accept(DagProgressEvent.dagStarted(executionId, dag.getNodes().size()));
    }

    // P2-#14: 创建带进度回调的消费者，在节点完成时推送进度
    Consumer<DagProgressEvent> progressTracker = createProgressTracker(progressConsumer, executionId, dag);
    DagExecutionResult result = executeWithProgress(dag, request.getUserInput(), resumeExecutionId, progressTracker);

    // 推送最终结果
    ChatResponse response = buildSummaryResponse(result);
    chunkConsumer.accept(ChatChunk.content(response.getId(), response.getModel(), response.getContent()));
    chunkConsumer.accept(ChatChunk.finish(response.getId(), response.getModel(), "stop", response.getUsage()));

    // P2-#14: 推送编排完成事件
    if (progressConsumer != null) {
      progressConsumer.accept(DagProgressEvent.dagCompleted(executionId, dag.getNodes().size()));
    }
  }

  /**
   * 创建进度追踪回调包装器。
   *
   * <p>内部维护已完成计数，每次回调时附加进度信息（completedCount / totalCount）。
   *
   * @param delegate 实际进度消费者
   * @param executionId 执行 ID
   * @param dag DAG 定义（用于获取总节点数）
   * @return 包装后的进度消费者
   */
  private Consumer<DagProgressEvent> createProgressTracker(
      Consumer<DagProgressEvent> delegate, String executionId, AgentDag dag) {
    if (delegate == null) {
      return null;
    }
    int total = dag.getNodes().size();
    return event -> {
      // 包装原始事件，附加当前进度计数
      DagProgressEvent enriched =
          new DagProgressEvent(
              event.getEventType(),
              event.getNodeId(),
              event.getNodeType(),
              event.getCompletedCount(),
              total,
              event.getError(),
              event.getTimestamp());
      delegate.accept(enriched);
    };
  }

  /**
   * 带进度追踪的 DAG 编排执行。
   *
   * <p>在每个节点开始/完成/失败时调用 progressCallback，同时保持检查点保存逻辑不变。
   *
   * @param dag DAG 定义
   * @param userInput 用户原始输入
   * @param resumeExecutionId 续跑的执行 ID（null 表示全新执行）
   * @param progressCallback 进度回调（可为 null）
   * @return 各节点执行结果
   */
  private DagExecutionResult executeWithProgress(
      AgentDag dag,
      String userInput,
      String resumeExecutionId,
      Consumer<DagProgressEvent> progressCallback) {
    String executionId = resumeExecutionId != null ? resumeExecutionId : IdGenerator.nextIdStr();
    log.info(
        "[DAG] 开始编排: id={}, name={}, nodes={}, resume={}",
        executionId, dag.getName(), dag.getNodes().size(), resumeExecutionId != null);

    Map<String, String> nodeResults = new ConcurrentHashMap<>();
    Map<String, TokenUsage> nodeUsages = new ConcurrentHashMap<>();
    Set<String> completed = ConcurrentHashMap.newKeySet();
    Set<String> failed = ConcurrentHashMap.newKeySet();

    if (resumeExecutionId != null) {
      loadCheckpoint(resumeExecutionId, nodeResults, completed, failed);
    }

    List<String> sortedNodeIds = topologicalSort(dag);
    Map<String, CompletableFuture<Void>> futureMap = new HashMap<>();
    Set<String> loopBodyNodeIds = collectLoopBodyNodeIds(dag);

    // P2-#14: 用于计算当前完成进度的原子计数器
    int[] completedCounter = {completed.size()};

    for (String nodeId : sortedNodeIds) {
      if (completed.contains(nodeId)) {
        futureMap.put(nodeId, CompletableFuture.completedFuture(null));
        continue;
      }
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
              () -> {
                // P2-#14: 推送节点启动事件
                if (progressCallback != null) {
                  progressCallback.accept(
                      DagProgressEvent.nodeStarted(
                          nodeId, node.getAgentType(), completedCounter[0], dag.getNodes().size()));
                }
                executeNodeLogic(
                    dag, node, userInput, nodeResults, nodeUsages, completed, failed, executionId);
                // P2-#14: 根据执行结果推送完成/失败事件
                if (progressCallback != null) {
                  synchronized (completedCounter) {
                    completedCounter[0] = completed.size();
                    if (failed.contains(nodeId)) {
                      progressCallback.accept(
                          DagProgressEvent.nodeFailed(
                              nodeId,
                              node.getAgentType(),
                              completedCounter[0],
                              dag.getNodes().size(),
                              nodeResults.getOrDefault(nodeId, "节点执行失败")));
                    } else {
                      progressCallback.accept(
                          DagProgressEvent.nodeCompleted(
                              nodeId,
                              node.getAgentType(),
                              completedCounter[0],
                              dag.getNodes().size()));
                    }
                  }
                }
              },
              executor);
      futureMap.put(nodeId, nodeFuture);
    }

    try {
      CompletableFuture.allOf(futureMap.values().toArray(new CompletableFuture[0]))
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
    log.info(
        "[DAG] 编排完成: id={}, completed={}, failed={}", executionId, completed.size(), failed.size());

    saveCheckpoint(executionId, dag.getName(), userInput, nodeResults, completed, failed);

    return new DagExecutionResult(
        executionId,
        dag.getName(),
        new HashMap<>(nodeResults),
        new HashMap<>(nodeUsages),
        completed,
        failed,
        hasFailed);
  }

  /**
   * 汇总 DAG 执行结果到最终回复。
   *
   * <p>失败节点存在时以错误文案提示；否则按节点顺序拼接各节点输出。
   *
   * @param result DAG 执行结果
   * @return 汇总后的 {@link ChatResponse}
   */
  private ChatResponse buildSummaryResponse(DagExecutionResult result) {
    String content;
    if (result.hasFailure()) {
      content = "DAG 编排存在失败节点: " + String.join(", ", result.failedNodes());
    } else {
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, String> entry : result.nodeResults().entrySet()) {
        sb.append("## ").append(entry.getKey()).append("\n").append(entry.getValue()).append("\n\n");
      }
      content = sb.toString().trim();
    }
    TokenUsage totalUsage =
        result.nodeUsages().values().stream()
            .reduce(TokenUsage.zero(), TokenUsage::add);
    return new ChatResponse(
        result.executionId(),
        properties.getLlm().getDefaultModel(),
        ChatMessage.assistant(content, null, totalUsage),
        totalUsage,
        result.hasFailure() ? "failure" : "stop",
        List.of());
  }

  /** {@inheritDoc} */
  @Override
  public String getType() {
    return "dag";
  }

  /** {@inheritDoc} */
  @Override
  public boolean supports(String type) {
    return "dag".equalsIgnoreCase(type);
  }
}
