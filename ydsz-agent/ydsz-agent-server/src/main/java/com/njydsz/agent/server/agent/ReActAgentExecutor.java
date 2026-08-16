package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
import com.njydsz.agent.domain.agent.AgentExecutor;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.agent.domain.tool.ToolRegistry;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
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
public class ReActAgentExecutor implements AgentExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(ReActAgentExecutor.class);

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆 */
  private final ConversationMemory memory;

  /** 工具注册中心 */
  private final ToolRegistry toolRegistry;

  /** Agent 配置属性 */
  private final AgentProperties properties;

  /** 链路记录器 */
  private final TraceRecorder traceRecorder;

  /** Agent 指标采集 */
  private final AgentMetrics agentMetrics;

  /** 成本分析服务（Token 用量核算，可为 null，调用处已做空判断） */
  private final CostAnalysisService costAnalysisService;

  /** 护栏编排服务（统一驱动输入/输出护栏，消除重复逻辑） */
  private final GuardrailService guardrailService;

  public ReActAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      ToolRegistry toolRegistry,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.toolRegistry = toolRegistry;
    this.properties = properties;
    this.traceRecorder = traceRecorder;
    this.agentMetrics = agentMetrics;
    this.costAnalysisService = costAnalysisService;
    this.guardrailService = guardrailService;
  }

  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId =
        request.getConversationId() != null ? request.getConversationId() : IdGenerator.nextIdStr();
    String traceId = traceRecorder.startTrace(convId, "REACT");
    LOG.info(
        "[ReAct] 开始执行: convId={}, traceId={}, maxIterations={}",
        convId,
        traceId,
        request.getMaxIterations());

    String userInput = guardrailService.applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      return buildRejectedResponse("输入被护栏拒绝");
    }

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(buildSystemPrompt(request)));
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
        long llmDuration = System.currentTimeMillis() - llmStart;
        agentMetrics.recordLlmCall(
            llmClient.getProvider(), properties.getLlm().getDefaultModel(), llmDuration, null, e);
        traceRecorder.recordStep(
            traceId,
            "LLM_CALL_ERROR",
            "LLM 调用失败 (iteration=" + i + ")",
            request.getUserInput(),
            e.getMessage(),
            llmDuration);
        traceRecorder.endTrace(traceId, "FAILED");
        throw e;
      }
      long llmDuration = System.currentTimeMillis() - llmStart;

      if (response.getUsage() != null) {
        totalUsage = totalUsage.add(response.getUsage());
      }

      // P0-3: AgentMetrics 指标采集
      agentMetrics.recordLlmCall(
          llmClient.getProvider(),
          properties.getLlm().getDefaultModel(),
          llmDuration,
          response,
          null);

      // P0-2: CostAnalysisService 成本核算
      if (response.getUsage() != null && costAnalysisService != null) {
        costAnalysisService.recordUsage(
            convId, properties.getLlm().getDefaultModel(), response.getUsage());
      }

      // P0-1: TraceRecorder 记录 LLM 调用步骤
      traceRecorder.recordStep(
          traceId, "LLM_CALL", "ReAct iteration " + (i + 1), messages, response, llmDuration);

      if (!response.hasToolCalls()) {
        String output = guardrailService.applyOutputGuardrails(response.getContent());
        memory.save(convId, ChatMessage.user(userInput, convId));
        memory.save(convId, ChatMessage.assistant(output, convId, response.getUsage()));
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
    String convId =
        request.getConversationId() != null ? request.getConversationId() : IdGenerator.nextIdStr();
    String traceId = traceRecorder.startTrace(convId, "REACT_STREAM");
    LOG.info("[ReAct-Stream] 开始流式执行: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();
    TokenUsage totalUsage = TokenUsage.zero();

    String userInput = guardrailService.applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      chunkConsumer.accept(ChatChunk.content(responseId, model, "抱歉，您的输入被安全护栏拒绝。"));
      chunkConsumer.accept(ChatChunk.finish(responseId, model, "guardrail_rejected", null));
      return;
    }

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(buildSystemPrompt(request)));
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
        long llmDuration = System.currentTimeMillis() - llmStart;
        agentMetrics.recordLlmCall(llmClient.getProvider(), model, llmDuration, null, e);
        traceRecorder.endTrace(traceId, "FAILED");
        chunkConsumer.accept(
            ChatChunk.content(responseId, model, "[错误] LLM 调用失败: " + e.getMessage()));
        chunkConsumer.accept(ChatChunk.finish(responseId, model, "error", null));
        throw e;
      }
      long llmDuration = System.currentTimeMillis() - llmStart;

      if (response.getUsage() != null) {
        totalUsage = totalUsage.add(response.getUsage());
      }
      agentMetrics.recordLlmCall(llmClient.getProvider(), model, llmDuration, response, null);
      if (response.getUsage() != null && costAnalysisService != null) {
        costAnalysisService.recordUsage(convId, model, response.getUsage());
      }
      traceRecorder.recordStep(
          traceId, "LLM_CALL", "ReAct iteration " + (i + 1), messages, response, llmDuration);

      // P0-6: 推送 LLM 回复内容（Thought / Final Answer）
      if (response.getContent() != null && !response.getContent().isBlank()) {
        String prefix = i > 0 ? "\n\n[思考" + (i + 1) + "] " : "";
        chunkConsumer.accept(ChatChunk.content(responseId, model, prefix + response.getContent()));
      }

      if (!response.hasToolCalls()) {
        String output = guardrailService.applyOutputGuardrails(response.getContent());
        memory.save(convId, ChatMessage.user(userInput, convId));
        memory.save(convId, ChatMessage.assistant(output, convId, totalUsage));
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
    return "react".equalsIgnoreCase(type) || "react_agent".equalsIgnoreCase(type);
  }

  private String buildSystemPrompt(AgentExecutionRequest request) {
    StringBuilder sb = new StringBuilder();
    if (request.getSystemPrompt() != null) {
      sb.append(request.getSystemPrompt());
    } else {
      sb.append("你是 YDSZ 项目管理信息系统的智能助手。你可以使用工具来帮助用户完成任务。");
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

  private ChatResponse buildRejectedResponse(String reason) {
    ChatMessage msg = ChatMessage.assistant("抱歉，" + reason + "。", null, TokenUsage.zero());
    return new ChatResponse(
        IdGenerator.nextIdStr(),
        "guardrail",
        msg,
        TokenUsage.zero(),
        "guardrail_rejected",
        List.of());
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
