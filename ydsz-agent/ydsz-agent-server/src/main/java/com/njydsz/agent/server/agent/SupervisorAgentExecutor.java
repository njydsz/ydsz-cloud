package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.njydsz.agent.domain.agent.AgentDefinition;
import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.chat.StreamingPiiMasker;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.id.IdGenerator;

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
 * @since 1.0.0
 */
public class SupervisorAgentExecutor extends AbstractAgentExecutor {

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
      AgentFactory agentFactory) {
    super(
        llmClient,
        memory,
        properties,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider);
    this.agentFactory = agentFactory;
  }

  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "SUPERVISOR");
    LOG.info("[Supervisor] 开始执行: convId={}, traceId={}", convId, traceId);

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      return buildRejectedResponse("您的输入被安全护栏拒绝");
    }

    // 1. 任务分解
    List<SubTask> subTasks = planTasks(userInput, traceId);
    traceRecorder.recordStep(
        traceId, "PLAN", "Task plan created", userInput, subTasks.size() + " subtasks", 0);

    // 2. 按依赖顺序执行子任务（depends_on 拓扑调度，无依赖者先执行）
    List<String> results = new ArrayList<>(subTasks.size());
    Map<Integer, String> taskResults = new HashMap<>();
    TokenUsage[] totalUsage = {TokenUsage.zero()};
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
        String result = executeSubTask(convId, request, subTask, traceId, totalUsage);
        taskResults.put(subTask.id(), result);
        results.add(result);
      }
      if (!progressed) {
        // 依赖环或缺失依赖：兜底按剩余顺序执行，避免死循环
        LOG.warn("[Supervisor] 子任务依赖无法满足，按剩余顺序兜底执行: remaining={}", pending.size());
        for (SubTask subTask : pending) {
          String result = executeSubTask(convId, request, subTask, traceId, totalUsage);
          taskResults.put(subTask.id(), result);
          results.add(result);
        }
        break;
      }
    }

    // 3. 汇总结果
    String finalAnswer = synthesizeResults(userInput, results, convId);
    String output = guardrailService.applyOutputGuardrails(finalAnswer);
    traceRecorder.endTrace(traceId, "SUCCESS");
    LOG.info(
        "[Supervisor] 执行完成: convId={}, subTasks={}, tokens={}",
        convId,
        subTasks.size(),
        totalUsage[0].getTotalTokens());

    return new ChatResponse(
        IdGenerator.nextIdStr(),
        properties.getLlm().getDefaultModel(),
        ChatMessage.assistant(output, convId, totalUsage[0]),
        totalUsage[0],
        "stop",
        List.of());
  }

  /**
   * 执行单个子任务并记录结果（成功/失败均返回结果文本）。
   *
   * @param convId 对话 ID
   * @param request 原始执行请求（用于继承推理参数）
   * @param subTask 子任务定义
   * @param traceId 链路 ID
   * @param usageAcc Token 用量累加器（单元素数组，跨方法可变）
   * @return 子任务执行结果文本
   */
  private String executeSubTask(
      String convId,
      AgentExecutionRequest request,
      SubTask subTask,
      String traceId,
      TokenUsage[] usageAcc) {
    AgentExecutionRequest subRequest =
        AgentExecutionRequest.builder()
            .userInput(subTask.description())
            .conversationId(convId + "-sub-" + subTask.id())
            .systemPrompt(null)
            .maxIterations(request.getMaxIterations())
            .build();
    AgentExecutor worker = createWorker(subTask.type(), request);
    try {
      ChatResponse workerResponse = worker.execute(subRequest);
      if (workerResponse.getUsage() != null) {
        usageAcc[0] = usageAcc[0].add(workerResponse.getUsage());
      }
      traceRecorder.recordStep(
          traceId,
          "SUB_TASK_DONE",
          "Sub-task " + subTask.id() + " completed",
          subTask.description(),
          workerResponse.getContent(),
          0);
      return workerResponse.getContent();
    } catch (Exception e) {
      LOG.error("[Supervisor] 子任务 {} 执行失败: {}", subTask.id(), e.getMessage());
      traceRecorder.recordStep(
          traceId,
          "SUB_TASK_ERROR",
          "Sub-task " + subTask.id() + " failed",
          subTask.description(),
          e.getMessage(),
          0);
      return "[子任务 " + subTask.id() + " 执行失败: " + e.getMessage() + "]";
    }
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
    LOG.info("[Supervisor-Stream] 开始流式执行: convId={}, traceId={}", convId, traceId);

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
    Map<Integer, String> taskResults = new HashMap<>();
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
        LOG.warn("[Supervisor-Stream] 子任务依赖无法满足，按剩余顺序兜底执行: remaining={}",
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
    LOG.info(
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
    AgentExecutionRequest subRequest =
        AgentExecutionRequest.builder()
            .userInput(subTask.description())
            .conversationId(convId + "-sub-" + subTask.id())
            .systemPrompt(null)
            .maxIterations(request.getMaxIterations())
            .build();
    AgentExecutor worker = createWorker(subTask.type(), request);
    StringBuilder resultBuilder = new StringBuilder();
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
                    ChatChunk.content(responseId, model, maskedDelta, chunk.getDeltaToolCalls()));
              }
            } else if (chunk.isFinished()) {
              // 冲刷剩余缓冲
              String maskedRest = streamingMasker.flush();
              if (!maskedRest.isEmpty()) {
                resultBuilder.append(maskedRest);
                chunkConsumer.accept(ChatChunk.content(responseId, model, maskedRest));
              }
              if (chunk.getUsage() != null) {
                usageAcc[0] = usageAcc[0].add(chunk.getUsage());
              }
            } else {
              chunkConsumer.accept(chunk);
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
      LOG.error("[Supervisor-Stream] 子任务 {} 执行失败: {}", subTask.id(), e.getMessage());
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
            .temperature(0.5)
            .maxTokens(properties.getLlm().getMaxTokens())
            .stream(true)
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
      LOG.error("[Supervisor-Stream] 汇总生成失败: {}", e.getMessage());
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
            .maxTokens(500)
            .build();
    try {
      ChatResponse planResponse = llmClient.chat(planRequest);
      traceRecorder.recordStep(
          traceId, "LLM_PLAN", "Planning LLM call", planPrompt, planResponse.getContent(), 0);
      return parsePlanResponse(planResponse.getContent());
    } catch (Exception e) {
      LOG.warn("[Supervisor] 任务规划失败，使用单任务降级: {}", e.getMessage());
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
        String inner = json.substring(start + 3, end);
        if (inner.startsWith("json")) {
          inner = inner.substring(4);
        }
        json = inner.trim();
      }
      Map<String, Object> root = YdszJson.parseMap(json);
      Object tasksObj = root.get("tasks");
      if (!(tasksObj instanceof List<?> taskList) || taskList.isEmpty()) {
        LOG.warn("[Supervisor] 计划中无 tasks 数组，使用默认任务");
        return List.of(new SubTask(1, "REACT", "处理用户请求", List.of()));
      }
      List<SubTask> tasks = new ArrayList<>(taskList.size());
      for (Object item : taskList) {
        if (!(item instanceof Map)) {
          continue;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) item;
        int id = parseTaskId(map.get("id"));
        String type = String.valueOf(map.getOrDefault("type", "REACT")).trim().toUpperCase();
        String description = String.valueOf(map.getOrDefault("description", ""));
        List<Integer> dependsOn = parseDependsOn(map.get("depends_on"));
        tasks.add(new SubTask(id, type, description, dependsOn));
      }
      return tasks.isEmpty() ? List.of(new SubTask(1, "REACT", "处理用户请求", List.of())) : tasks;
    } catch (Exception e) {
      LOG.warn("[Supervisor] 计划解析失败，使用默认任务: {}", e.getMessage());
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
    List<Integer> dependsOn = new ArrayList<>();
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
      return "抱歉，无法完成您的任务。";
    }
    if (results.size() == 1) {
      return results.get(0);
    }
    // 多任务结果拼接 + 最终总结
    StringBuilder sb = new StringBuilder();
    sb.append("以下是各子任务的执行结果：\n\n");
    for (int i = 0; i < results.size(); i++) {
      sb.append("## 任务 ").append(i + 1).append("\n");
      sb.append(results.get(i)).append("\n\n");
    }
    sb.append("---\n以上就是针对您的请求\"").append(originalTask).append("\"的处理结果。");
    return sb.toString();
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
            List.of(),
            properties.getLlm().getTemperature(),
            properties.getLlm().getMaxTokens(),
            request.getMaxIterations(),
            properties.getLlm().getDefaultModel());
    return agentFactory.getExecutor(def);
  }

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
