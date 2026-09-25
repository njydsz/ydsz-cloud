package com.njydsz.agent.server.agent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutionContext;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.middleware.MiddlewareChain;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.chat.StreamingPiiMasker;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.common.util.id.IdGenerator;
import com.njydsz.common.util.message.MessageUtils;

/**
 * Supervisor 多 Agent 协作执行器
 *
 * <p>实现 Supervisor 模式：一个"主管 Agent"负责任务分解、子 Agent 调度和结果汇总。
 *
 * <h3>工作流程</h3>
 *
 * <ol>
 *   <li>接收用户输入
 *   <li>通过 LLM 分析任务，生成执行计划（含子任务列表）
 *   <li>分发子任务到合适的 Worker Agent 执行
 *   <li>汇总各 Worker 结果，生成最终回答
 * </ol>
 *
 * <h3>适用场景</h3>
 *
 * <p>适合需要多种能力协作的复杂任务，例如： "帮我分析项目进度，查询相关文档，然后生成一份报告" 可分解为 REACT（查项目）+ RAG（查文档）→ CHAT（生成报告）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class SupervisorAgentExecutor extends AbstractAgentExecutor {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;


  /** 任务分解 Prompt 模板（默认值，可被数据库模板覆盖） */
  private static final String DEFAULT_PLAN_PROMPT_TEMPLATE =
      """
            你是 YDSZ 智能助手的任务规划器。请分析用户任务，将其分解为可执行的子任务。

            可用的 Worker Agent 类型：
            - CHAT: 简单问答、文本生成、总结
            - REACT: 需要调用工具完成任务（查询、操作）
            - RAG: 需要检索知识库回答问题
            - PLAN_EXECUTE: 复杂多步任务

            用户任务: {task}

            请以 JSON 格式输出执行计划（不要 markdown 代码块）：
            {"tasks": [{"id": 1, "type": "REACT", "description": "任务描述", "depends_on": []}]}

            - id: 任务序号
            - type: Worker 类型（CHAT/REACT/RAG/PLAN_EXECUTE）
            - description: 子任务的具体描述
            - depends_on: 依赖的前置任务 id 列表（无依赖为空数组）

            最多分解为 3 个子任务。
            """;

  /** 规划阶段系统 Prompt（默认值，可被数据库模板覆盖） */
  private static final String DEFAULT_PLAN_SYSTEM_PROMPT = "你是任务规划器，只输出 JSON 格式的执行计划。";

  /** 汇总阶段温度 */
  private static final double SYNTHESIZE_TEMPERATURE = 0.5;

  /** 规划阶段最大输出 Token 数 */
  private static final int PLAN_MAX_TOKENS = 500;

  /** Markdown 代码块围栏字符长度（```） */
  private static final int MD_FENCE_LENGTH = 3;

  /** Markdown 代码块语言标记 "json" 的长度 */
  private static final int JSON_LANG_TAG_LENGTH = 4;

  /** 子代理片段来源标识前缀（完整形式 {@code supervisor/{子任务号}}） */
  private static final String SUB_SOURCE_PREFIX = "supervisor/";

  /** 子 Agent 执行超时时间（秒） */
  private static final long SUB_TASK_TIMEOUT_SECONDS = 120;

  /** Agent 工厂 */
  private final AgentFactory agentFactory;

  public SupervisorAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService,
      PromptTemplateProvider promptTemplateProvider,
      AgentFactory agentFactory,
      MiddlewareChain middlewareChain,
      I18nMessages i18nMessages) {
    super(
        llmClient,
        memory,
        properties,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider,
        middlewareChain,
        i18nMessages);
    this.agentFactory = agentFactory;
  }

  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "SUPERVISOR");
    log.info("[Supervisor] 开始执行: convId={}, traceId={}", convId, traceId);

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      return buildRejectedResponse("您的输入被安全护栏拒绝");
    }

    // 1. 任务分解
    List<SubTask> subTasks = planTasks(userInput, traceId);
    traceRecorder.recordStep(
        traceId, "PLAN", "Task plan created", userInput, subTasks.size() + " subtasks", 0);

    // 2. 按依赖层级并行执行子任务（每层内部并行，层间串行保证依赖顺序）
    ParallelSubTaskResult parallelResult =
        executeSubTasksWithIsolation(convId, request, subTasks, traceId);

    // 3. 汇总结果
    String finalAnswer = synthesizeResults(userInput, parallelResult.results(), convId);
    String output = guardrailService.applyOutputGuardrails(finalAnswer);
    traceRecorder.endTrace(traceId, "SUCCESS");
    log.info(
        "[Supervisor] 执行完成: convId={}, subTasks={}, tokens={}",
        convId,
        subTasks.size(),
        parallelResult.totalUsage().getTotalTokens());

    return new ChatResponse(
        IdGenerator.nextIdStr(),
        properties.getLlm().getDefaultModel(),
        ChatMessage.assistant(output, convId, parallelResult.totalUsage()),
        parallelResult.totalUsage(),
        "stop",
        List.of());
  }

  /**
   * 按依赖层级并行执行子任务，每层内部通过 {@link CompletableFuture} 并行，层间串行保证依赖顺序。
   *
   * <p><b>上下文隔离策略</b>：
   *
   * <ul>
   *   <li>每个子任务通过 {@link #buildSubAgentConversationId} 获得独立的对话 ID，
   *       与主 Agent 记忆完全隔离（基于 {@link AgentExecutionContext#copyForSubTask} 的命名约定）
   *   <li>子 Agent 的中间 LLM 调用结果不会污染主 Agent 上下文
   *   <li>主 Agent 仅获取每个子任务的最终结果（最后一条 assistant 消息内容）
   * </ul>
   *
   * <p><b>容错策略</b>：单个子任务失败不影响其他子任务，失败时返回错误描述文本继续汇总。
   *
   * @param convId 主对话 ID
   * @param request 原始执行请求
   * @param subTasks 子任务列表
   * @param traceId 链路 ID
   * @return 并行子任务执行结果（含结果列表和聚合 Token 用量）
   */
  private ParallelSubTaskResult executeSubTasksWithIsolation(
      String convId,
      AgentExecutionRequest request,
      List<SubTask> subTasks,
      String traceId) {
    Map<Integer, String> taskResults = new HashMap<>(COLLECTION_CAPACITY);
    TokenUsage totalUsage = TokenUsage.zero();

    // 按依赖层级分组：key=依赖深度（0=无依赖，1=依赖 depth 0 的任务...）
    Map<Integer, List<SubTask>> layers = groupByDependencyDepth(subTasks);
    List<Integer> sortedDepths = layers.keySet().stream().sorted().toList();

    // 按层级顺序执行，层内并行
    for (int depth : sortedDepths) {
      List<SubTask> layerTasks = layers.get(depth);
      log.info("[Supervisor] 开始执行第 {} 层，共 {} 个子任务", depth, layerTasks.size());

      // 为每个子任务提交并行到子 Agent 线程池执行
      List<CompletableFuture<TaskResult>> futures = new ArrayList<>(layerTasks.size());
      for (SubTask subTask : layerTasks) {
        // 构建隔离的子对话 ID（对话记忆隔离）
        String subConversationId = buildSubAgentConversationId(convId, subTask);
        CompletableFuture<TaskResult> future =
            CompletableFuture.supplyAsync(
                    () -> executeSubTaskIsolated(subConversationId, request, subTask, traceId),
                    SubAgentExecutorPool.getExecutor())
                .orTimeout(SUB_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle(
                    (result, ex) -> {
                      if (ex != null) {
                        return handleSubTaskException(subTask, ex, traceId);
                      }
                      return result;
                    });
        futures.add(future);
      }

      // 等待当前层全部完成，累计 Token 用量
      for (CompletableFuture<TaskResult> future : futures) {
        try {
          TaskResult taskResult = future.get();
          taskResults.put(taskResult.taskId(), taskResult.result());
          if (taskResult.usage() != null) {
            totalUsage = totalUsage.add(taskResult.usage());
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.error("[Supervisor] 子任务等待被中断: {}", e.getMessage());
        } catch (ExecutionException e) {
          // handle() 已处理异常，此处不应到达；兜底记录
          log.error("[Supervisor] 子任务执行异常: {}", e.getCause().getMessage());
        }
      }
    }

    // 按任务 ID 顺序返回结果
    List<String> results =
        subTasks.stream().map(task -> taskResults.getOrDefault(task.id(), "")).toList();
    return new ParallelSubTaskResult(results, totalUsage);
  }

  /**
   * 按依赖深度对子任务进行分层分组。
   *
   * <p>深度 0 = 无依赖（可立即执行）；深度 N = 所有依赖任务的深度均小于 N。
   *
   * @param subTasks 子任务列表
   * @return 按深度分层的子任务映射
   */
  private Map<Integer, List<SubTask>> groupByDependencyDepth(List<SubTask> subTasks) {
    Map<Integer, List<SubTask>> layers = new HashMap<>();
    Map<Integer, Integer> taskDepths = new HashMap<>();

    for (SubTask task : subTasks) {
      int depth = computeDepth(task, subTasks, taskDepths);
      layers.computeIfAbsent(depth, k -> new ArrayList<>()).add(task);
    }
    return layers;
  }

  /**
   * 递归计算子任务的依赖深度。
   *
   * @param task 当前子任务
   * @param allTasks 全部子任务列表
   * @param memo 深度缓存（避免重复计算）
   * @return 依赖深度（0 = 无依赖）
   */
  private int computeDepth(SubTask task, List<SubTask> allTasks, Map<Integer, Integer> memo) {
    Integer cached = memo.get(task.id());
    if (cached != null) {
      return cached;
    }
    int depth;
    if (task.dependsOn().isEmpty()) {
      depth = 0;
    } else {
      int maxDepDepth = 0;
      for (int depId : task.dependsOn()) {
        for (SubTask candidate : allTasks) {
          if (candidate.id() == depId) {
            maxDepDepth = Math.max(maxDepDepth, computeDepth(candidate, allTasks, memo));
            break;
          }
        }
      }
      depth = maxDepDepth + 1;
    }
    memo.put(task.id(), depth);
    return depth;
  }

  /**
   * 构建子 Agent 的隔离对话 ID。
   *
   * <p>命名约定与 {@link AgentExecutionContext#copyForSubTask} 一致：
   * 格式为 {@code parentConversationId:subTaskCode}，确保子 Agent 的对话记忆命名空间与主 Agent 完全隔离。
   *
   * @param parentConversationId 父对话 ID
   * @param subTask 子任务定义
   * @return 子 Agent 的隔离对话 ID
   */
  private String buildSubAgentConversationId(String parentConversationId, SubTask subTask) {
    return parentConversationId + ":sub-" + subTask.id();
  }

  /**
   * 在隔离子对话中执行单个子任务，仅返回最终结果。
   *
   * <p>子 Agent 的所有中间 LLM 调用结果保存在子对话的记忆中，不会污染主 Agent 上下文。
   *
   * @param subConversationId 子对话 ID（隔离命名空间）
   * @param request 原始执行请求
   * @param subTask 子任务定义
   * @param traceId 链路 ID
   * @return 子任务执行结果（含 ID、结果文本、Token 用量）
   */
  private TaskResult executeSubTaskIsolated(
      String subConversationId,
      AgentExecutionRequest request,
      SubTask subTask,
      String traceId) {
    AgentExecutionRequest subRequest =
        request.deriveForSubAgent(subTask.description(), subConversationId, List.of());
    AgentExecutor worker = createWorker(subTask.type(), request);
    try {
      ChatResponse workerResponse = worker.execute(subRequest);
      traceRecorder.recordStep(
          traceId,
          "SUB_TASK_DONE",
          "Sub-task " + subTask.id() + " completed",
          subTask.description(),
          workerResponse.getContent(),
          0);
      return new TaskResult(subTask.id(), workerResponse.getContent(), workerResponse.getUsage());
    } catch (Exception e) {
      log.error("[Supervisor] 子任务 {} 执行失败: {}", subTask.id(), e.getMessage());
      traceRecorder.recordStep(
          traceId,
          "SUB_TASK_ERROR",
          "Sub-task " + subTask.id() + " failed",
          subTask.description(),
          e.getMessage(),
          0);
      return new TaskResult(
          subTask.id(), "[子任务 " + subTask.id() + " 执行失败: " + e.getMessage() + "]", null);
    }
  }

  /**
   * 处理子任务执行异常（超时、中断等），返回错误结果。
   *
   * @param subTask 子任务定义
   * @param ex 异常对象
   * @param traceId 链路 ID
   * @return 包含错误描述的结果
   */
  private TaskResult handleSubTaskException(SubTask subTask, Throwable ex, String traceId) {
    String reason;
    if (ex instanceof TimeoutException) {
      reason = "执行超时";
    } else if (ex instanceof InterruptedException) {
      reason = "执行被中断";
    } else {
      reason = "执行异常: " + ex.getMessage();
    }
    log.error("[Supervisor] 子任务 {} {}: {}", subTask.id(), reason, ex.getMessage());
    traceRecorder.recordStep(
        traceId,
        "SUB_TASK_TIMEOUT",
        "Sub-task " + subTask.id() + " " + reason,
        subTask.description(),
        ex.getMessage(),
        0);
    return new TaskResult(subTask.id(), "[子任务 " + subTask.id() + reason + "]", null);
  }

  /**
   * {@inheritDoc}
   *
   * <p>真实流式执行（非同步执行后模拟）。流式阶段：
   *
   * <ol>
   *   <li><b>规划阶段</b>：推送任务分解结果（子任务列表）
   *   <li><b>执行阶段</b>：逐子任务流式执行，实时推送每个 Worker 的输出
   *   <li><b>汇总阶段</b>：流式生成最终总结
   * </ol>
   *
   * <p>每个子任务通过 {@link AgentExecutor#executeStream} 流式执行（若 Worker 支持）； 若 Worker 仅支持同步，则执行完成后推送完整结果。
   */
  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "SUPERVISOR_STREAM");
    log.info("[Supervisor-Stream] 开始流式执行: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      emitRejectionStream(responseId, chunkConsumer);
      return;
    }

    // 1. 规划阶段
    List<SubTask> subTasks = planTasks(userInput, traceId);
    traceRecorder.recordStep(
        traceId, "PLAN", "Task plan created", userInput, subTasks.size() + " subtasks", 0);
    // 推送规划结果
    chunkConsumer.accept(
        ChatChunk.content(responseId, model, formatPlanMessage(subTasks)));

    // 2. 流式执行子任务
    List<String> results = new ArrayList<>(subTasks.size());
    Map<Integer, String> taskResults = new HashMap<>(COLLECTION_CAPACITY);
    TokenUsage[] totalUsage = {TokenUsage.zero()};
    StreamingPiiMasker streamingMasker = new StreamingPiiMasker();
    List<SubTask> pending = new ArrayList<>(subTasks);
    while (!pending.isEmpty()) {
      boolean progressed = false;
      Iterator<SubTask> iterator = pending.iterator();
      while (iterator.hasNext()) {
        SubTask subTask = iterator.next();
        if (!subTask.dependsOn().stream().allMatch(taskResults::containsKey)) {
          continue;
        }
        iterator.remove();
        progressed = true;
        // 推送子任务开始标记
        chunkConsumer.accept(
            ChatChunk.content(
                responseId, model, String.format("\n\n[执行任务 %d/%d] %s\n",
                    subTask.id(), subTasks.size(), subTask.description())));
        // 流式执行子任务
        String result = executeSubTaskStream(
            convId, request, subTask, traceId, totalUsage, responseId,
            model, streamingMasker, chunkConsumer);
        taskResults.put(subTask.id(), result);
        results.add(result);
      }
      if (!progressed) {
        log.warn("[Supervisor-Stream] 子任务依赖无法满足，按剩余顺序兜底执行: remaining={}",
            pending.size());
        for (SubTask subTask : pending) {
          chunkConsumer.accept(
              ChatChunk.content(
                  responseId, model, String.format("\n\n[执行任务 %d/%d] %s\n",
                      subTask.id(), subTasks.size(), subTask.description())));
          String result = executeSubTaskStream(
              convId, request, subTask, traceId, totalUsage, responseId,
              model, streamingMasker, chunkConsumer);
          taskResults.put(subTask.id(), result);
          results.add(result);
        }
        break;
      }
    }

    // 3. 流式汇总（PII 脱敏已在 synthesizeResultsStreaming 内部处理）
    chunkConsumer.accept(ChatChunk.content(responseId, model, "\n\n[汇总中]\n"));
    synthesizeResultsStreaming(
        userInput, results, convId, responseId, model, streamingMasker, chunkConsumer);

    traceRecorder.endTrace(traceId, "SUCCESS");
    log.info(
        "[Supervisor-Stream] 流式执行完成: convId={}, subTasks={}, tokens={}",
        convId, subTasks.size(), totalUsage[0].getTotalTokens());
    // 推送完成 chunk（冲刷剩余 PII 缓冲）
    String maskedRest = streamingMasker.flush();
    if (!maskedRest.isEmpty()) {
      chunkConsumer.accept(ChatChunk.content(responseId, model, maskedRest));
    }
    chunkConsumer.accept(
        ChatChunk.finish(responseId, model, "stop", totalUsage[0]));
  }

  /**
   * 格式化规划消息（推送任务分解结果给前端）。
   *
   * @param subTasks 子任务列表
   * @return 规划描述文本
   */
  private String formatPlanMessage(List<SubTask> subTasks) {
    StringBuilder sb = new StringBuilder();
    sb.append("[任务规划] 将任务分解为 ").append(subTasks.size()).append(" 个子任务：\n");
    for (SubTask task : subTasks) {
      sb.append(String.format("  %d. [%s] %s\n", task.id(), task.type(), task.description()));
    }
    return sb.toString();
  }

  /**
   * 流式执行单个子任务，实时推送 Worker 输出。
   *
   * @param convId 对话 ID
   * @param request 原始执行请求
   * @param subTask 子任务定义
   * @param traceId 链路 ID
   * @param usageAcc Token 用量累加器
   * @param responseId 响应 ID
   * @param model 模型名称
   * @param streamingMasker PII 脱敏器
   * @param chunkConsumer 流式消费者
   * @return 子任务完整结果文本
   */
  private String executeSubTaskStream(
      String convId,
      AgentExecutionRequest request,
      SubTask subTask,
      String traceId,
      TokenUsage[] usageAcc,
      String responseId,
      String model,
      StreamingPiiMasker streamingMasker,
      Consumer<ChatChunk> chunkConsumer) {
    AgentExecutionRequest subRequest = buildSubRequest(convId, request, subTask);
    AgentExecutor worker = createWorker(subTask.type(), request);
    StringBuilder resultBuilder = new StringBuilder();
    // 子代理片段来源标识：父流按 supervisor/{子任务号} 区分归属（P0-2）
    String subSource = SUB_SOURCE_PREFIX + subTask.id();
    try {
      // 使用流式执行（worker 支持流式则流式，否则回退到同步）
      worker.executeStream(
          subRequest,
          chunk -> {
            if (chunk.hasContent()) {
              // PII 脱敏后推送
              String maskedDelta = streamingMasker.mask(chunk.getDeltaContent());
              if (!maskedDelta.isEmpty()) {
                resultBuilder.append(maskedDelta);
                chunkConsumer.accept(
                    ChatChunk.content(responseId, model, maskedDelta, chunk.getDeltaToolCalls())
                        .withSource(subSource));
              }
            } else if (chunk.isFinished()) {
              // 冲刷剩余缓冲
              String maskedRest = streamingMasker.flush();
              if (!maskedRest.isEmpty()) {
                resultBuilder.append(maskedRest);
                chunkConsumer.accept(
                    ChatChunk.content(responseId, model, maskedRest).withSource(subSource));
              }
              if (chunk.getUsage() != null) {
                usageAcc[0] = usageAcc[0].add(chunk.getUsage());
              }
            } else {
              chunkConsumer.accept(chunk.withSource(subSource));
            }
          });
      traceRecorder.recordStep(
          traceId,
          "SUB_TASK_DONE",
          "Sub-task " + subTask.id() + " completed",
          subTask.description(),
          resultBuilder.toString(),
          0);
    } catch (Exception e) {
      log.error("[Supervisor-Stream] 子任务 {} 执行失败: {}", subTask.id(), e.getMessage());
      traceRecorder.recordStep(
          traceId,
          "SUB_TASK_ERROR",
          "Sub-task " + subTask.id() + " failed",
          subTask.description(),
          e.getMessage(),
          0);
      String errorResult = "[子任务 " + subTask.id() + " 执行失败: " + e.getMessage() + "]";
      resultBuilder.append(errorResult);
      chunkConsumer.accept(ChatChunk.content(responseId, model, errorResult));
    }
    return resultBuilder.toString();
  }

  /**
   * 流式汇总子任务结果（通过 LLM 流式生成最终总结）。
   *
   * @param originalTask 原始用户任务
   * @param results 子任务结果列表
   * @param convId 对话 ID
   * @param responseId 响应 ID
   * @param model 模型名称
   * @param streamingMasker PII 脱敏器
   * @param chunkConsumer 流式消费者
   * @return 最终回答文本
   */
  private String synthesizeResultsStreaming(
      String originalTask,
      List<String> results,
      String convId,
      String responseId,
      String model,
      StreamingPiiMasker streamingMasker,
      Consumer<ChatChunk> chunkConsumer) {
    if (results.isEmpty()) {
      String msg = "抱歉，无法完成您的任务。";
      chunkConsumer.accept(ChatChunk.content(responseId, model, msg));
      return msg;
    }
    if (results.size() == 1) {
      // 单任务：直接流式输出结果
      chunkConsumer.accept(ChatChunk.content(responseId, model, results.get(0)));
      return results.get(0);
    }
    // 多任务：构建汇总 prompt，通过 LLM 流式生成总结
    StringBuilder synthesizePrompt = new StringBuilder();
    synthesizePrompt.append("以下是针对用户请求\"").append(originalTask).append("\"的各子任务执行结果：\n\n");
    for (int i = 0; i < results.size(); i++) {
      synthesizePrompt.append("## 任务 ").append(i + 1).append("\n");
      synthesizePrompt.append(results.get(i)).append("\n\n");
    }
    synthesizePrompt.append("请基于以上结果，生成一份简洁、连贯的最终回复（不要重复子任务标题，直接给出总结性回答）：");

    ChatRequest synthesizeRequest =
        ChatRequest.builder()
            .model(model)
            .messages(
                List.of(
                    ChatMessage.system("你是结果汇总助手，负责将多个子任务结果整合为连贯的最终回复。"),
                    ChatMessage.user(synthesizePrompt.toString(), convId)))
            .temperature(SYNTHESIZE_TEMPERATURE)
            .maxTokens(properties.getLlm().getMaxTokens())
            .isStream(true)
            .build();

    StringBuilder finalAnswer = new StringBuilder();
    try {
      llmClient.stream(
          synthesizeRequest,
          chunk -> {
            if (chunk.hasContent()) {
              String maskedDelta = streamingMasker.mask(chunk.getDeltaContent());
              if (!maskedDelta.isEmpty()) {
                finalAnswer.append(maskedDelta);
                chunkConsumer.accept(
                    ChatChunk.content(responseId, model, maskedDelta, chunk.getDeltaToolCalls()));
              }
            } else if (chunk.isFinished()) {
              String maskedRest = streamingMasker.flush();
              if (!maskedRest.isEmpty()) {
                finalAnswer.append(maskedRest);
                chunkConsumer.accept(ChatChunk.content(responseId, model, maskedRest));
              }
              chunkConsumer.accept(chunk);
            } else {
              chunkConsumer.accept(chunk);
            }
          });
    } catch (Exception e) {
      log.error("[Supervisor-Stream] 汇总生成失败: {}", e.getMessage());
      // 降级：直接拼接结果
      String fallback = String.join("\n\n", results);
      chunkConsumer.accept(ChatChunk.content(responseId, model, fallback));
      return fallback;
    }
    return finalAnswer.toString();
  }

  @Override
  public String getType() {
    return "supervisor";
  }

  @Override
  public boolean supports(String type) {
    return "supervisor".equalsIgnoreCase(type);
  }

  /**
   * 通过 LLM 分析任务并生成执行计划
   *
   * @param userTask 用户原始任务
   * @param traceId 链路 ID
   * @return 子任务列表（最多 3 个）
   */
  private List<SubTask> planTasks(String userTask, String traceId) {
    String planTemplate =
        promptTemplateProvider.loadOrDefault(
            properties.getPromptTemplate().getSupervisorPlanCode(), DEFAULT_PLAN_PROMPT_TEMPLATE);
    String planSystemPrompt =
        promptTemplateProvider.loadOrDefault(
            properties.getPromptTemplate().getSupervisorPlanSystemCode(), DEFAULT_PLAN_SYSTEM_PROMPT);

    String planPrompt = planTemplate.replace("{task}", userTask);
    ChatRequest planRequest =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(
                List.of(
                    ChatMessage.system(planSystemPrompt),
                    ChatMessage.user(planPrompt, null)))
            .temperature(0.0)
            .maxTokens(PLAN_MAX_TOKENS)
            .build();
    try {
      ChatResponse planResponse = llmClient.chat(planRequest);
      traceRecorder.recordStep(
          traceId, "LLM_PLAN", "Planning LLM call", planPrompt, planResponse.getContent(), 0);
      return parsePlanResponse(planResponse.getContent());
    } catch (Exception e) {
      log.warn("[Supervisor] 任务规划失败，使用单任务降级: {}", e.getMessage());
      // 降级为单 REACT 任务
      return List.of(new SubTask(1, "REACT", "直接处理用户请求: " + userTask, List.of()));
    }
  }

  /**
   * 解析 LLM 输出的 JSON 执行计划。
   *
   * <p>P1 修复：原实现仅用 {@code json.contains(type.name())} 判断类型、忽略 {@code description} 与 {@code
   * depends_on}。现真实反序列化 {@code tasks} 数组， 完整保留 id / type / description / depends_on，交由拓扑调度按依赖执行。
   */
  private List<SubTask> parsePlanResponse(String content) {
    if (content == null || content.isBlank()) {
      return List.of();
    }
    try {
      // 提取 JSON 部分（去除可能的 markdown 代码块）
      String json = content.trim();
      if (json.contains("```")) {
        int start = json.indexOf("```");
        int end = json.lastIndexOf("```");
        String inner = json.substring(start + MD_FENCE_LENGTH, end);
        if (inner.startsWith("json")) {
          inner = inner.substring(JSON_LANG_TAG_LENGTH);
        }
        json = inner.trim();
      }
      Map<String, Object> root = YdszJson.parseMap(json);
      Object tasksObj = root.get("tasks");
      if (!(tasksObj instanceof List<?> taskList) || taskList.isEmpty()) {
        log.warn("[Supervisor] 计划中无 tasks 数组，使用默认任务");
        return List.of(new SubTask(1, "REACT", "处理用户请求", List.of()));
      }
      List<SubTask> tasks = new ArrayList<>(taskList.size());
      for (Object item : taskList) {
        if (!(item instanceof Map)) {
          continue;
        }
        Map<String, Object> map = Map.class.cast(item);
        int id = parseTaskId(map.get("id"));
        String type = String.valueOf(map.getOrDefault("type", "REACT")).trim().toUpperCase();
        String description = String.valueOf(map.getOrDefault("description", ""));
        List<Integer> dependsOn = parseDependsOn(map.get("depends_on"));
        tasks.add(new SubTask(id, type, description, dependsOn));
      }
      return tasks.isEmpty() ? List.of(new SubTask(1, "REACT", "处理用户请求", List.of())) : tasks;
    } catch (Exception e) {
      log.warn("[Supervisor] 计划解析失败，使用默认任务: {}", e.getMessage());
      return List.of(new SubTask(1, "REACT", "处理用户请求", List.of()));
    }
  }

  /** 解析任务 ID（兼容数字或数字字符串）。 */
  private int parseTaskId(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** 解析 depends_on 依赖任务 ID 列表（兼容 JSON 数组或逗号分隔字符串）。 */
  private List<Integer> parseDependsOn(Object value) {
    if (value == null) {
      return List.of();
    }
    List<Integer> dependsOn = new ArrayList<>(COLLECTION_CAPACITY);
    if (value instanceof List<?> list) {
      for (Object item : list) {
        dependsOn.add(parseTaskId(item));
      }
    } else if (value instanceof String str && !str.isBlank()) {
      for (String part : str.split(",")) {
        dependsOn.add(parseTaskId(part.trim()));
      }
    }
    return dependsOn;
  }

  /** 汇总子任务结果生成最终回答 */
  private String synthesizeResults(String originalTask, List<String> results, String convId) {
    if (results.isEmpty()) {
      return MessageUtils.getMessage("agent.supervisor.noResult", "抱歉，无法完成您的任务。");
    }
    if (results.size() == 1) {
      return results.get(0);
    }
    // 多任务结果拼接 + 最终总结
    String header = MessageUtils.getMessage("agent.supervisor.multiTaskHeader", "以下是各子任务的执行结果：\n\n");
    String taskPrefix = MessageUtils.getMessage("agent.supervisor.taskPrefix", "## 任务 ");
    String footer = MessageUtils.getMessage("agent.supervisor.footer", new Object[] {originalTask},
        "---\n这就是针对您的请求\"" + originalTask + "\"的处理结果。");
    StringBuilder sb = new StringBuilder();
    sb.append(header);
    for (int i = 0; i < results.size(); i++) {
      sb.append(taskPrefix).append(i + 1).append("\n");
      sb.append(results.get(i)).append("\n\n");
    }
    sb.append(footer);
    return sb.toString();
  }

  /**
   * 构建子任务执行请求（强制父级管控继承）。
   *
   * <p>P1-3：对标 AgentScope「子代理强制继承父级 DENY 规则」，子请求经
   * {@link AgentExecutionRequest#deriveForSubAgent} 派生——工具白名单取父级与子级的交集，
   * 保证子 Agent 的工具面不会宽于父级；迭代上限、系统提示词、预置上下文一并继承，
   * 避免子级自行放宽管控。此前子请求未设置任何工具白名单，等于给子 Agent 开放全部工具。
   *
   * @param convId 父对话 ID
   * @param request 父级执行请求
   * @param subTask 子任务定义
   * @return 收紧后的子任务请求
   */
  private AgentExecutionRequest buildSubRequest(
      String convId, AgentExecutionRequest request, SubTask subTask) {
    return request.deriveForSubAgent(
        subTask.description(), convId + "-sub-" + subTask.id(), List.of());
  }

  /** 创建 Worker Agent 执行器 */
  private AgentExecutor createWorker(String type, AgentExecutionRequest request) {
    AgentDefinition def =
        new AgentDefinition(
            IdGenerator.nextIdStr(),
            "supervisor-worker",
            "Worker",
            AgentDefinition.Type.valueOf(type),
            request.getSystemPrompt(),
            request.getEnabledTools(),
            BigDecimal.valueOf(properties.getLlm().getTemperature()),
            properties.getLlm().getMaxTokens(),
            request.getMaxIterations(),
            properties.getLlm().getDefaultModel());
    return agentFactory.getExecutor(def);
  }

  /**
   * 子任务执行结果（隔离执行后的轻量返回值，仅携带最终结果，不含中间过程数据）。
   *
   * @param taskId 子任务 ID
   * @param result 子任务最终结果文本
   * @param usage Token 用量（可能为 null）
   */
  private record TaskResult(int taskId, String result, TokenUsage usage) {}

  /**
   * 并行子任务聚合执行结果。
   *
   * @param results 按子任务 ID 排序的结果文本列表
   * @param totalUsage 所有子任务 Token 用量的总和
   */
  private record ParallelSubTaskResult(List<String> results, TokenUsage totalUsage) {}

  /**
   * 子任务定义
   *
   * @param id 任务序号
   * @param type Worker Agent 类型
   * @param description 任务描述
   * @param dependsOn 依赖的前置任务 ID 列表（空列表表示无依赖，可立即执行）
   */
  private record SubTask(int id, String type, String description, List<Integer> dependsOn) {}
}
