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
import com.njydsz.agent.domain.rag.TextChunk;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.chat.GuardrailService;
import com.njydsz.agent.server.chat.StreamingPiiMasker;
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.rag.RagService;
import com.njydsz.common.util.id.IdGenerator;

/**
 * RAG 增强 Agent 执行器
 *
 * <p>在 LLM 调用前先检索知识库，将检索到的上下文注入 System Prompt， 使 LLM 能够基于私有知识回答问题。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class RagAgentExecutor extends AbstractAgentExecutor {

  /** RAG 检索服务（向量检索 + 全文检索 + RRF 融合） */
  private final RagService ragService;

  public RagAgentExecutor(
      LlmClient llmClient,
      ConversationMemory memory,
      AgentProperties properties,
      RagService ragService,
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
    this.ragService = ragService;
  }

  /**
   * {@inheritDoc}
   *
   * <p>RAG 执行流程：输入护栏 → RAG 知识检索 → 构建增强 prompt（检索上下文 + System + 历史 + 用户）→ LLM 调用 → 指标/成本/追踪埋点 → 输出护栏
   * → 保存对话记忆 → 返回响应。
   */
  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "RAG");
    log.info("[RAG-Agent] 执行: convId={}, traceId={}", convId, traceId);

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      return buildRejectedResponse("您的输入被安全护栏拒绝");
    }

    long ragStart = System.currentTimeMillis();
    List<TextChunk> retrievedChunks = ragService.retrieve(userInput);
    long ragDuration = System.currentTimeMillis() - ragStart;
    String ragContext = ragService.buildContext(retrievedChunks);

    traceRecorder.recordStep(
        traceId,
        "RAG_RETRIEVE",
        "Retrieved " + retrievedChunks.size() + " chunks",
        userInput,
        retrievedChunks,
        ragDuration);

    String systemPrompt = buildSystemPrompt(request, ragContext);
    List<ChatMessage> messages = new ArrayList<>();
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
      recordLlmError(traceId, "RAG", request, e, llmStart);
      throw e;
    }

    recordLlmSuccess(convId, traceId, "RAG enhanced LLM call", messages, response, llmStart);

    String output = applyOutputGuardrails(response.getContent());
    saveConversation(convId, userInput, output, response.getUsage());

    traceRecorder.endTrace(traceId, "SUCCESS");
    log.info(
        "[RAG-Agent] 完成: convId={}, retrieved={}, tokens={}",
        convId,
        retrievedChunks.size(),
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
   * <p>RAG 流式执行流程：输入护栏 → RAG 知识检索 → 构建增强 prompt → LLM 流式调用（逐 chunk 推送，增量 PII 脱敏）→ 累积内容 → 输出护栏 →
   * 保存对话记忆 → 追踪记录。
   */
  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    String convId = extractConvId(request);
    String traceId = startTrace(convId, "RAG_STREAM");
    log.info("[RAG-Agent-Stream] 流式执行: convId={}, traceId={}", convId, traceId);

    String responseId = IdGenerator.nextIdStr();
    String model = properties.getLlm().getDefaultModel();

    String userInput = applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      emitRejectionStream(responseId, chunkConsumer);
      return;
    }

    long ragStart = System.currentTimeMillis();
    List<TextChunk> retrievedChunks = ragService.retrieve(userInput);
    long ragDuration = System.currentTimeMillis() - ragStart;
    String ragContext = ragService.buildContext(retrievedChunks);

    traceRecorder.recordStep(
        traceId,
        "RAG_RETRIEVE",
        "Retrieved " + retrievedChunks.size() + " chunks",
        userInput,
        retrievedChunks,
        ragDuration);

    String systemPrompt = buildSystemPrompt(request, ragContext);
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(systemPrompt));
    messages.addAll(memory.load(convId, properties.getMemory().getMaxMessages()));
    messages.add(ChatMessage.user(userInput, convId));

    ChatRequest llmRequest =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
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
              // 流式增量 PII 脱敏——先脱敏后推送，避免已发出的 token 含敏感信息
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

    agentMetrics.recordLlmStream(
        llmClient.getProvider(), properties.getLlm().getDefaultModel(), duration, usage[0], null);
    if (usage[0] != null && !usage[0].equals(TokenUsage.zero()) && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, properties.getLlm().getDefaultModel(), usage[0]);
    }
    traceRecorder.recordStep(
        traceId,
        "LLM_CALL",
        "RAG stream LLM call",
        llmRequest,
        contentBuilder.toString(),
        duration);

    String output = applyOutputGuardrails(contentBuilder.toString());
    saveConversation(convId, userInput, output, usage[0]);

    traceRecorder.endTrace(traceId, "SUCCESS");
    log.info(
        "[RAG-Agent-Stream] 完成: convId={}, retrieved={}, tokens={}",
        convId,
        retrievedChunks.size(),
        usage[0].getTotalTokens());
  }

  @Override
  public String getType() {
    return "rag";
  }

  @Override
  public boolean supports(String type) {
    return "rag".equalsIgnoreCase(type);
  }

  private String buildSystemPrompt(AgentExecutionRequest request, String ragContext) {
    StringBuilder sb = new StringBuilder();
    if (request.getSystemPrompt() != null) {
      sb.append(request.getSystemPrompt());
    } else {
      sb.append("你是 YDSZ 项目管理信息系统的智能助手。请基于知识库内容回答用户问题。");
    }
    if (ragContext != null && !ragContext.isBlank()) {
      sb.append("\n\n").append(ragContext);
    }
    return sb.toString();
  }
}
