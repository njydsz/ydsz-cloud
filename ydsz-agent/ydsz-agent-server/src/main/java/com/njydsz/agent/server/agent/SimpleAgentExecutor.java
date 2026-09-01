package com.njydsz.agent.server.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.agent.AgentExecutionRequest;
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
import com.njydsz.common.util.id.IdGenerator;

/**
 * 简单 Agent 执行器（单轮 LLM 调用，无工具）
 *
 * <p>最基础的 Agent 模式：System Prompt + 历史消息 + 用户消息 → LLM → 响应。 适用于不需要工具调用的纯对话场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SimpleAgentExecutor extends AbstractAgentExecutor {

  public SimpleAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      AgentProperties properties,
      TraceRecorder traceRecorder,
      AgentMetrics agentMetrics,
      CostAnalysisService costAnalysisService,
      GuardrailService guardrailService,
      PromptTemplateProvider promptTemplateProvider) {
    super(
        llmClient,
        memory,
        properties,
        traceRecorder,
        agentMetrics,
        costAnalysisService,
        guardrailService,
        promptTemplateProvider);
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行流程：输入护栏 → 构建消息（System + 历史 + 用户）→ LLM 调用 → 指标/成本/追踪埋点 → 输出护栏 → 保存对话记忆 → 返回响应。 输入护栏拒绝时返回
   * guardrail_rejected 响应，LLM 调用失败时抛出原始异常。
   */
  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "CHAT");
    log.info("[Simple-Agent] 执行: convId={}, traceId={}", convId, traceId);

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      return buildRejectedResponse("您的输入被安全护栏拒绝");
    }

    List<ChatMessage> messages = new ArrayList<>();
    String systemPrompt =
        resolveSystemPrompt(
            request,
            properties.getPromptTemplate().getDefaultSystemCode(),
            properties.getDefaultSystemPrompt());
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
      recordLlmError(traceId, "CHAT", request, e, llmStart);
      throw e;
    }

    recordLlmSuccess(convId, traceId, "Simple chat", messages, response, llmStart);

    String output = applyOutputGuardrails(response.getContent());
    saveConversation(convId, userInput, output, response.getUsage());

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
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "CHAT_STREAM");
    log.info("[Simple-Agent-Stream] 流式执行: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      emitRejectionStream(responseId, chunkConsumer);
      return;
    }

    List<ChatMessage> messages = new ArrayList<>();
    String systemPrompt =
        resolveSystemPrompt(
            request,
            properties.getPromptTemplate().getDefaultSystemCode(),
            properties.getDefaultSystemPrompt());
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
      recordLlmStreamError(traceId, llmRequest, e, startTime);
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

    String output = applyOutputGuardrails(contentBuilder.toString());
    saveConversation(convId, userInput, output, usage[0]);

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
