package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class SupervisorAgentExecutor implements AgentExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(SupervisorAgentExecutor.class);

  /** 任务分解 Prompt 模板 */
  private static final String PLAN_PROMPT_TEMPLATE =
      """
            你是 YDSZ 智能助手的任务规划器。请分析用户任务，将其分解为可执行的子任务。

            可用的 Worker Agent 类型：
            - CHAT: 简单问答、文本生成、总结
            - REACT: 需要调用工具完成任务（查询、操作）
            - RAG: 需要检索知识库回答问题
            - PLAN_EXECUTE: 复杂多步任务

            用户任务: {task}

            请以 JSON 格式输出执行计划（不要 markdown 代码块）：
            {{"tasks": [{"id": 1, "type": "REACT", "description": "任务描述", "depends_on": []}]}}

            - id: 任务序号
            - type: Worker 类型（CHAT/REACT/RAG/PLAN_EXECUTE）
            - description: 子任务的具体描述
            - depends_on: 依赖的前置任务 id 列表（无依赖为空数组）

            最多分解为 3 个子任务。
            """;

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆 */
  private final ConversationMemory memory;

  /** Agent 配置属性 */
  private final AgentProperties properties;

  /** 链路记录器 */
  private final TraceRecorder traceRecorder;

  /** Agent 指标采集 */
  private final AgentMetrics agentMetrics;

  /** 成本分析服务 */
  private final CostAnalysisService costAnalysisService;

  /** 护栏编排服务 */
  private final GuardrailService guardrailService;

  /** Agent 工厂 */
  private final AgentFactory agentFactory;

  /** Prompt 模板提供者（加载外部化模板） */
  private final PromptTemplateProvider promptTemplateProvider;

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
    this.llmClient = llmClient;
    this.memory = memory;
    this.properties = properties;
    this.traceRecorder = traceRecorder;
    this.agentMetrics = agentMetrics;
    this.costAnalysisService = costAnalysisService;
    this.guardrailService = guardrailService;
    this.promptTemplateProvider = promptTemplateProvider;
    this.agentFactory = agentFactory;
  }

  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId =
        request.getConversationId() != null ? request.getConversationId() : IdGenerator.nextIdStr();
    String traceId = traceRecorder.startTrace(convId, "SUPERVISOR");
    LOG.info("[Supervisor] 开始执行: convId={}, traceId={}", convId, traceId);

    String userInput = guardrailService.applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      return buildRejectedResponse();
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

  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    // 同步执行后模拟流式输出（Supervisor 模式不适合真正的流式，因为需等待所有子任务完成）
    ChatResponse response = execute(request);
    String[] parts = response.getContent().split("(?<=\n)");
    for (String part : parts) {
      chunkConsumer.accept(ChatChunk.content(response.getId(), response.getModel(), part));
    }
    chunkConsumer.accept(
        ChatChunk.finish(
            response.getId(),
            response.getModel(),
            response.getFinishReason(),
            response.getUsage()));
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
    String planPrompt = PLAN_PROMPT_TEMPLATE.replace("{task}", userTask);
    ChatRequest planRequest =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(
                List.of(
                    ChatMessage.system("你是任务规划器，只输出 JSON 格式的执行计划。"),
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

  private ChatResponse buildRejectedResponse() {
    ChatMessage msg = ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", null, TokenUsage.zero());
    return new ChatResponse(
        IdGenerator.nextIdStr(),
        "guardrail",
        msg,
        TokenUsage.zero(),
        "guardrail_rejected",
        List.of());
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
