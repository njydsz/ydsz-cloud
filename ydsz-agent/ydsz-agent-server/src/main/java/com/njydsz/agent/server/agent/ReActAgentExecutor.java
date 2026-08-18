package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.gateway.PromptTemplateProvider;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.common.util.id.IdGenerator;

/**
 * ReAct Agent 执行器
 *
 * <p>实现 ReAct（Reasoning + Acting）模式：
 *
 * <pre>
 * Thought → Action (Tool Call) → Observation (Tool Result) → Thought → ... → Final Answer
 * </pre>
 *
 * <p>可观测性：
 *
 * <ul>
 *   <li>{@link TraceRecorder} — 记录每次 LLM 调用和工具执行步骤
 *   <li>{@link AgentMetrics} — 采集 LLM 调用耗时/Token/状态指标
 *   <li>{@link CostAnalysisService} — 核算 Token 用量成本
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ReActAgentExecutor extends AbstractAgentExecutor {

  /** 工具注册中心 */
  private final ToolRegistry toolRegistry;

  /** RAG 检索服务（可选，为 null 时不启用知识增强） */
  private final RagService ragService;

  public ReActAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      ToolRegistry toolRegistry,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService,
      PromptTemplateProvider promptTemplateProvider,
      RagService ragService) {
    super(
        llmClient,
        memory,
        properties,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider);
    this.toolRegistry = toolRegistry;
    this.ragService = ragService;
  }

  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "REACT");
    LOG.info(
        "[ReAct] 开始执行: convId={}, traceId={}, maxIterations={}",
        convId,
        traceId,
        request.getMaxIterations());

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      return buildRejectedResponse("输入被护栏拒绝");
    }

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(buildSystemPrompt(request, userInput)));
    messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
    messages.add(ChatMessage.user(userInput, convId));

    TokenUsage totalUsage = TokenUsage.zero();
    // P2 优化：工具定义按请求缓存，避免每轮迭代重复收集
    List<ToolDefinition> toolDefinitions = new ArrayList<>(toolRegistry.getToolDefinitions());

    for (int i = 0; i < request.getMaxIterations(); i++) {
      ChatRequest llmRequest =
          ChatRequest.builder()
              .model(properties.getLlm().getDefaultModel())
              .messages(messages)
              .temperature(properties.getLlm().getTemperature())
              .maxTokens(properties.getLlm().getMaxTokens())
              .tools(toolDefinitions)
              .build();

      long llmStart = System.currentTimeMillis();
      ChatResponse response;
      try {
        response = llmClient.chat(llmRequest);
      } catch (Exception e) {
        recordLlmError(traceId, "REACT", request, e, llmStart);
        throw e;
      }

      if (response.getUsage() != null) {
        totalUsage = totalUsage.add(response.getUsage());
      }
      recordLlmSuccess(convId, traceId, "ReAct iteration " + (i + 1), messages, response, llmStart);

      if (!response.hasToolCalls()) {
        String output = applyOutputGuardrails(response.getContent());
        saveConversation(convId, userInput, output, response.getUsage());
        traceRecorder.endTrace(traceId, "SUCCESS");
        LOG.info(
            "[ReAct] 完成: convId={}, iterations={}, tokens={}",
            convId,
            i + 1,
            totalUsage.getTotalTokens());
        return new ChatResponse(
            response.getId(),
            response.getModel(),
            ChatMessage.assistant(output, convId, totalUsage),
            totalUsage,
            "stop",
            List.of());
      }

      messages.add(response.getMessage());
      for (ToolCall toolCall : response.getToolCalls()) {
        LOG.info("[ReAct] 执行工具: {}", toolCall.getName());
        long toolStart = System.currentTimeMillis();
        String result = toolRegistry.execute(toolCall);
        long toolDuration = System.currentTimeMillis() - toolStart;

        // P0-1: TraceRecorder 记录工具调用步骤
        traceRecorder.recordStep(
            traceId,
            "TOOL_CALL",
            toolCall.getName(),
            toolCall.getArguments(),
            result,
            toolDuration);

        ChatMessage toolMsg = ChatMessage.tool(toolCall.getId(), result, convId);
        messages.add(toolMsg);
      }
    }

    LOG.warn("[ReAct] 超过最大迭代次数: convId={}", convId);
    traceRecorder.endTrace(traceId, "MAX_ITERATIONS");
    return buildMaxIterationsResponse(convId, totalUsage);
  }

  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "REACT_STREAM");
    LOG.info("[ReAct-Stream] 开始流式执行: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();
    TokenUsage totalUsage = TokenUsage.zero();

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      emitRejectionStream(responseId, chunkConsumer);
      return;
    }

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(buildSystemPrompt(request, userInput)));
    messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
    messages.add(ChatMessage.user(userInput, convId));

    // P2 优化：工具定义按请求缓存，避免每轮迭代重复收集
    List<ToolDefinition> toolDefinitions = new ArrayList<>(toolRegistry.getToolDefinitions());

    for (int i = 0; i < request.getMaxIterations(); i++) {
      ChatRequest llmRequest =
          ChatRequest.builder()
              .model(model)
              .messages(messages)
              .temperature(properties.getLlm().getTemperature())
              .maxTokens(properties.getLlm().getMaxTokens())
              .tools(toolDefinitions)
              .build();

      long llmStart = System.currentTimeMillis();
      ChatResponse response;
      try {
        response = llmClient.chat(llmRequest);
      } catch (Exception e) {
        recordLlmError(traceId, "REACT_STREAM", request, e, llmStart);
        chunkConsumer.accept(
            ChatChunk.content(responseId, model, "[错误] LLM 调用失败: " + e.getMessage()));
        chunkConsumer.accept(ChatChunk.finish(responseId, model, "error", null));
        throw e;
      }

      if (response.getUsage() != null) {
        totalUsage = totalUsage.add(response.getUsage());
      }
      recordLlmSuccess(convId, traceId, "ReAct iteration " + (i + 1), messages, response, llmStart);

      // P0-6: 推送 LLM 回复内容（Thought / Final Answer）
      if (response.getContent() != null && !response.getContent().isBlank()) {
        String prefix = i > 0 ? "\n\n[思考" + (i + 1) + "] " : "";
        chunkConsumer.accept(ChatChunk.content(responseId, model, prefix + response.getContent()));
      }

      if (!response.hasToolCalls()) {
        String output = applyOutputGuardrails(response.getContent());
        saveConversation(convId, userInput, output, totalUsage);
        traceRecorder.endTrace(traceId, "SUCCESS");
        chunkConsumer.accept(ChatChunk.finish(responseId, model, "stop", totalUsage));
        return;
      }

      // 推送工具调用事件
      messages.add(response.getMessage());
      for (ToolCall toolCall : response.getToolCalls()) {
        chunkConsumer.accept(
            ChatChunk.content(responseId, model, "\n\n[工具调用] " + toolCall.getName() + "..."));
        long toolStart = System.currentTimeMillis();
        String result = toolRegistry.execute(toolCall);
        long toolDuration = System.currentTimeMillis() - toolStart;
        traceRecorder.recordStep(
            traceId,
            "TOOL_CALL",
            toolCall.getName(),
            toolCall.getArguments(),
            result,
            toolDuration);
        chunkConsumer.accept(
            ChatChunk.content(responseId, model, "\n[工具结果] " + truncateResult(result)));
        ChatMessage toolMsg = ChatMessage.tool(toolCall.getId(), result, convId);
        messages.add(toolMsg);
      }
    }

    LOG.warn("[ReAct-Stream] 超过最大迭代次数: convId={}", convId);
    traceRecorder.endTrace(traceId, "MAX_ITERATIONS");
    chunkConsumer.accept(ChatChunk.content(responseId, model, "\n\n抱歉，我已达到最大推理次数限制，无法完成此任务。"));
    chunkConsumer.accept(ChatChunk.finish(responseId, model, "max_iterations", totalUsage));
  }

  private String truncateResult(String result) {
    if (result == null) {
      return "";
    }
    return result.length() > 200 ? result.substring(0, 200) + "..." : result;
  }

  @Override
  public String getType() {
    return "react";
  }

  @Override
  public boolean supports(String type) {
    return "react".equalsIgnoreCase(type) || "react_agent".equalsIgnoreCase(type)
        || "rag".equalsIgnoreCase(type);
  }

  /**
   * 构建系统 Prompt（含可选 RAG 知识增强）。
   *
   * <p>当 {@link #ragService} 不为 null 时，会根据用户输入检索知识库，将检索到的上下文注入 System Prompt。
   *
   * @param request 执行请求
   * @param userInput 用户输入（用于 RAG 检索）
   * @return 构建后的系统 Prompt
   */
  private String buildSystemPrompt(AgentExecutionRequest request, String userInput) {
    StringBuilder sb = new StringBuilder();
    if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
      sb.append(request.getSystemPrompt());
    } else {
      String templateContent =
          promptTemplateProvider.load(properties.getPromptTemplate().getReactSystemCode());
      sb.append(
          templateContent != null
              ? templateContent
              : "你是 YDSZ 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。");
    }
    // P1-1: RAG 知识增强（可选）
    if (ragService != null && userInput != null && !userInput.isBlank()) {
      String ragContext = retrieveRagContext(userInput, request);
      if (ragContext != null && !ragContext.isBlank()) {
        sb.append("\n\n").append(ragContext);
      }
    }
    if (toolRegistry.size() > 0) {
      sb.append("\n\n你可以使用以下工具：\n");
      for (var tool : toolRegistry.getToolDefinitions()) {
        sb.append("- ").append(tool.getName());
        if (tool.getDescription() != null) {
          sb.append(": ").append(tool.getDescription());
        }
        sb.append("\n");
      }
      sb.append("\n请根据用户需求决定是否使用工具。如果不需要工具，直接回答即可。");
    }
    return sb.toString();
  }

  /**
   * 检索 RAG 上下文（内部方法，失败时返回 null 而非抛出异常）。
   *
   * @param userInput 用户输入
   * @param request 执行请求（用于 trace 记录）
   * @return RAG 上下文字符串，检索失败时返回 null
   */
  private String retrieveRagContext(String userInput, AgentExecutionRequest request) {
    try {
      List<TextChunk> chunks = ragService.retrieve(userInput);
      if (chunks.isEmpty()) {
        return null;
      }
      return ragService.buildContext(chunks);
    } catch (Exception e) {
      LOG.warn("[ReAct] RAG 检索失败，跳过知识增强: {}", e.getMessage());
      return null;
    }
  }

  private ChatResponse buildMaxIterationsResponse(String convId, TokenUsage usage) {
    ChatMessage msg = ChatMessage.assistant("抱歉，我已达到最大推理次数限制，无法完成此任务。请尝试简化您的问题。", convId, usage);
    return new ChatResponse(
        IdGenerator.nextIdStr(),
        properties.getLlm().getDefaultModel(),
        msg,
        usage,
        "max_iterations",
        List.of());
  }
}
