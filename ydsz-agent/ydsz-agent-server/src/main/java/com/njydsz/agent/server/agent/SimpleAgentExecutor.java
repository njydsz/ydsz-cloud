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
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.chat.StreamingPiiMasker;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 简单 Agent 执行器（单轮 LLM 调用，无工具）
 *
 * <p>最基础的 Agent 模式：System Prompt + 历史消息 + 用户消息 → LLM → 响应。 适用于不需要工具调用的纯对话场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SimpleAgentExecutor implements AgentExecutor {

  /** 日志记录器 */
  private static final Logger log = LoggerFactory.getLogger(SimpleAgentExecutor.class);

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆（历史消息加载/保存） */
  private final ConversationMemory memory;

  /** Agent 配置属性 */
  private final AgentProperties properties;

  /** 链路追踪记录器 */
  private final TraceRecorder traceRecorder;

  /** Agent 监控指标采集器 */
  private final AgentMetrics agentMetrics;

  /** 成本分析服务（Token 用量核算） */
  private final CostAnalysisService costAnalysisService;

  /** 护栏编排服务（统一驱动输入/输出护栏，消除重复逻辑） */
  private final GuardrailService guardrailService;

  public SimpleAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.properties = properties;
    this.traceRecorder = traceRecorder;
    this.agentMetrics = agentMetrics;
    this.costAnalysisService = costAnalysisService;
    this.guardrailService = guardrailService;
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行流程：输入护栏 → 构建消息（System + 历史 + 用户）→ LLM 调用 → 指标/成本/追踪埋点 → 输出护栏 → 保存对话记忆 → 返回响应。 输入护栏拒绝时返回
   * guardrail_rejected 响应，LLM 调用失败时抛出原始异常。
   */
  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId =
        request.getConversationId() != null ? request.getConversationId() : IdGenerator.nextIdStr();
    String traceId = traceRecorder.startTrace(convId, "CHAT");
    log.info("[Simple-Agent] 执行: convId={}, traceId={}", convId, traceId);

    String userInput = guardrailService.applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      ChatMessage msg = ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
      return new ChatResponse(
          IdGenerator.nextIdStr(),
          "guardrail",
          msg,
          TokenUsage.zero(),
          "guardrail_rejected",
          List.of());
    }

    List<ChatMessage> messages = new ArrayList<>();
    String systemPrompt =
        request.getSystemPrompt() != null
            ? request.getSystemPrompt()
            : "你是 YDSZ 项目管理信息系统的智能助手。请用中文回答。";
    messages.add(ChatMessage.system(systemPrompt));
    messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
    messages.add(ChatMessage.user(userInput, convId));

    ChatRequest llmRequest =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(messages)
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
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
          "LLM 调用失败",
          request.getUserInput(),
          e.getMessage(),
          llmDuration);
      traceRecorder.endTrace(traceId, "FAILED");
      throw e;
    }
    long llmDuration = System.currentTimeMillis() - llmStart;

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
    traceRecorder.recordStep(traceId, "LLM_CALL", "Simple chat", messages, response, llmDuration);

    String output = guardrailService.applyOutputGuardrails(response.getContent());

    memory.save(convId, ChatMessage.user(userInput, convId));
    memory.save(convId, ChatMessage.assistant(output, convId, response.getUsage()));

    traceRecorder.endTrace(traceId, "SUCCESS");
    log.info(
        "[Simple-Agent] 完成: convId={}, tokens={}",
        convId,
        response.getUsage() != null ? response.getUsage().getTotalTokens() : 0);
    return new ChatResponse(
        response.getId(),
        response.getModel(),
        ChatMessage.assistant(output, convId, response.getUsage()),
        response.getUsage(),
        response.getFinishReason(),
        List.of());
  }

  /**
   * {@inheritDoc}
   *
   * <p>流式执行流程：输入护栏 → 构建消息 → LLM 流式调用（逐 chunk 推送）→ 累积内容 → 输出护栏 → 保存对话记忆 → 追踪记录。 输入护栏拒绝时推送拒绝消息并结束流。
   */
  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    String convId =
        request.getConversationId() != null ? request.getConversationId() : IdGenerator.nextIdStr();
    String traceId = traceRecorder.startTrace(convId, "CHAT_STREAM");
    log.info("[Simple-Agent-Stream] 流式执行: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();

    String userInput = guardrailService.applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      chunkConsumer.accept(ChatChunk.content(responseId, model, "抱歉，您的输入被安全护栏拒绝。"));
      chunkConsumer.accept(ChatChunk.finish(responseId, model, "guardrail_rejected", null));
      return;
    }

    List<ChatMessage> messages = new ArrayList<>();
    String systemPrompt =
        request.getSystemPrompt() != null
            ? request.getSystemPrompt()
            : "你是 YDSZ 项目管理信息系统的智能助手。请用中文回答。";
    messages.add(ChatMessage.system(systemPrompt));
    messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
    messages.add(ChatMessage.user(userInput, convId));

    ChatRequest llmRequest =
        ChatRequest.builder()
            .model(model)
            .messages(messages)
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .stream(true)
            .build();

    long startTime = System.currentTimeMillis();
    StringBuilder contentBuilder = new StringBuilder();
    TokenUsage[] usage = {TokenUsage.zero()};
    StreamingPiiMasker streamingMasker = new StreamingPiiMasker();

    try {
      llmClient.stream(
          llmRequest,
          chunk -> {
            if (chunk.hasContent()) {
              // P0: 流式增量 PII 脱敏——先脱敏后推送，避免已发出的 token 含敏感信息
              String maskedDelta = streamingMasker.mask(chunk.getDeltaContent());
              if (!maskedDelta.isEmpty()) {
                contentBuilder.append(maskedDelta);
                chunkConsumer.accept(
                    ChatChunk.content(
                        chunk.getId(), chunk.getModel(), maskedDelta, chunk.getDeltaToolCalls()));
              }
            } else if (chunk.isFinished()) {
              String maskedRest = streamingMasker.flush();
              if (!maskedRest.isEmpty()) {
                contentBuilder.append(maskedRest);
                chunkConsumer.accept(
                    ChatChunk.content(chunk.getId(), chunk.getModel(), maskedRest));
              }
              if (chunk.getUsage() != null) {
                usage[0] = chunk.getUsage();
              }
              chunkConsumer.accept(chunk);
            } else {
              // 工具调用等非文本 chunk 原样转发
              chunkConsumer.accept(chunk);
            }
          });
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      agentMetrics.recordLlmCall(llmClient.getProvider(), model, duration, null, e);
      traceRecorder.recordStep(
          traceId, "LLM_CALL_ERROR", "Stream failed", llmRequest, e.getMessage(), duration);
      traceRecorder.endTrace(traceId, "FAILED");
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;

    agentMetrics.recordLlmStream(llmClient.getProvider(), model, duration, usage[0], null);
    if (usage[0] != null && !usage[0].equals(TokenUsage.zero()) && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, usage[0]);
    }
    traceRecorder.recordStep(
        traceId,
        "LLM_CALL",
        "Simple stream LLM call",
        llmRequest,
        contentBuilder.toString(),
        duration);

    String output = guardrailService.applyOutputGuardrails(contentBuilder.toString());
    memory.save(convId, ChatMessage.user(userInput, convId));
    memory.save(convId, ChatMessage.assistant(output, convId, usage[0]));

    traceRecorder.endTrace(traceId, "SUCCESS");
    log.info("[Simple-Agent-Stream] 完成: convId={}, tokens={}", convId, usage[0].getTotalTokens());
  }

  @Override
  public String getType() {
    return "chat";
  }

  @Override
  public boolean supports(String type) {
    return "chat".equalsIgnoreCase(type) || "simple".equalsIgnoreCase(type);
  }
}
