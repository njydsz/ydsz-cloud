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
 * @since 1.0.0
 */
public class RagAgentExecutor implements AgentExecutor {

  /** 日志记录器 */
  private static final Logger LOG = LoggerFactory.getLogger(RagAgentExecutor.class);

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆（历史消息加载/保存） */
  private final ConversationMemory memory;

  /** Agent 配置属性 */
  private final AgentProperties properties;

  /** RAG 检索服务（向量检索 + 全文检索 + RRF 融合） */
  private final RagService ragService;

  /** 链路追踪记录器 */
  private final TraceRecorder traceRecorder;

  /** Agent 监控指标采集器 */
  private final AgentMetrics agentMetrics;

  /** 成本分析服务（Token 用量核算） */
  private final CostAnalysisService costAnalysisService;

  /** 护栏编排服务（统一驱动输入/输出护栏，消除重复逻辑） */
  private final GuardrailService guardrailService;

  /** Prompt 模板提供者（加载外部化模板） */
  private final PromptTemplateProvider promptTemplateProvider;

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
    this.llmClient = llmClient;
    this.memory = memory;
    this.properties = properties;
    this.ragService = ragService;
    this.traceRecorder = traceRecorder;
    this.agentMetrics = agentMetrics;
    this.costAnalysisService = costAnalysisService;
    this.guardrailService = guardrailService;
    this.promptTemplateProvider = promptTemplateProvider;
  }

  /**
   * {@inheritDoc}
   *
   * <p>RAG 执行流程：输入护栏 → RAG 知识检索 → 构建增强 prompt（检索上下文 + System + 历史 + 用户）→ LLM 调用 → 指标/成本/追踪埋点 → 输出护栏
   * → 保存对话记忆 → 返回响应。
   */
  @Override
  public ChatResponse execute(AgentExecutionRequest request) {
    String convId =
        request.getConversationId() != null ? request.getConversationId() : IdGenerator.nextIdStr();
    String traceId = traceRecorder.startTrace(convId, "RAG");
    LOG.info("[RAG-Agent] 执行: convId={}, traceId={}", convId, traceId);

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
      long llmDuration = System.currentTimeMillis() - llmStart;
      agentMetrics.recordLlmCall(
          llmClient.getProvider(), properties.getLlm().getDefaultModel(), llmDuration, null, e);
      traceRecorder.recordStep(
          traceId, "LLM_CALL_ERROR", "LLM 调用失败", messages, e.getMessage(), llmDuration);
      traceRecorder.endTrace(traceId, "FAILED");
      throw e;
    }
    long llmDuration = System.currentTimeMillis() - llmStart;

    agentMetrics.recordLlmCall(
        llmClient.getProvider(),
        properties.getLlm().getDefaultModel(),
        llmDuration,
        response,
        null);

    if (response.getUsage() != null && costAnalysisService != null) {
      costAnalysisService.recordUsage(
          convId, properties.getLlm().getDefaultModel(), response.getUsage());
    }

    traceRecorder.recordStep(
        traceId, "LLM_CALL", "RAG enhanced LLM call", messages, response, llmDuration);

    String output = guardrailService.applyOutputGuardrails(response.getContent());

    memory.save(convId, ChatMessage.user(userInput, convId));
    memory.save(convId, ChatMessage.assistant(output, convId, response.getUsage()));

    traceRecorder.endTrace(traceId, "SUCCESS");
    LOG.info(
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
   * <p>RAG 流式执行流程：输入护栏 → RAG 知识检索 → 构建增强 prompt → LLM 流式调用（逐 chunk 推送）→ 累积内容 → 输出护栏 → 保存对话记忆 →
   * 追踪记录。
   */
  @Override
  public void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer) {
    String convId =
        request.getConversationId() != null ? request.getConversationId() : IdGenerator.nextIdStr();
    String traceId = traceRecorder.startTrace(convId, "RAG_STREAM");
    LOG.info("[RAG-Agent-Stream] 流式执行: convId={}, traceId={}", convId, traceId);

    String userInput = guardrailService.applyInputGuardrails(request.getUserInput());
    if (userInput == null) {
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      chunkConsumer.accept(ChatChunk.content("", "guardrail", "抱歉，您的输入被安全护栏拒绝。"));
      chunkConsumer.accept(ChatChunk.finish("", "guardrail", "guardrail_rejected", null));
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

    try {
      llmClient.stream(
          llmRequest,
          chunk -> {
            if (chunk.hasContent()) {
              contentBuilder.append(chunk.getDeltaContent());
            }
            if (chunk.isFinished() && chunk.getUsage() != null) {
              usage[0] = chunk.getUsage();
            }
            chunkConsumer.accept(chunk);
          });
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      agentMetrics.recordLlmCall(
          llmClient.getProvider(), properties.getLlm().getDefaultModel(), duration, null, e);
      traceRecorder.recordStep(
          traceId, "LLM_CALL_ERROR", "Stream failed", llmRequest, e.getMessage(), duration);
      traceRecorder.endTrace(traceId, "FAILED");
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

    String output = guardrailService.applyOutputGuardrails(contentBuilder.toString());
    memory.save(convId, ChatMessage.user(userInput, convId));
    memory.save(convId, ChatMessage.assistant(output, convId, usage[0]));

    traceRecorder.endTrace(traceId, "SUCCESS");
    LOG.info(
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
