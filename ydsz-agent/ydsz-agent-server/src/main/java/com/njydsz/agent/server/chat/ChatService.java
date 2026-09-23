package com.njydsz.agent.server.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.CostEstimate;
import com.njydsz.agent.domain.model.MessageContent;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.event.AgentEventPublisher;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.metrics.AgentRuntimeMetrics;
import com.njydsz.agent.server.quota.TenantQuotaService;
import com.njydsz.agent.domain.model.TenantQuota;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 对话服务
 *
 * <p>提供同步和流式两种对话模式，支持文本和多模态（Vision）两种输入。
 *
 * <p>对话流程（公共阶段由辅助方法统一编排）：
 *
 * <ol>
 *   <li>应用输入护栏（Prompt 注入检测、PII 脱敏）
 *   <li>预保存用户消息（LLM 调用前持久化，避免崩溃丢失）
 *   <li>配额预检（Token 估算 → 拦截超额请求）
 *   <li>调用 LLM（同步 / 流式）
 *   <li>成本核算 + 配额用量记录
 *   <li>应用输出护栏 + 保存助手响应
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Service
@Slf4j
public class ChatService {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** 同步日志前缀 */
  private static final String LOG_PREFIX_SYNC = "[Chat]";
  /** 流式日志前缀 */
  private static final String LOG_PREFIX_STREAM = "[Chat-Stream]";

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆（用于保存/加载用户消息历史） */
  private final ConversationMemory memory;

  /** Agent 配置属性 */
  private final AgentProperties properties;

  /** 护栏编排服务 */
  private final GuardrailService guardrailService;

  /** Agent 指标采集 */
  private final AgentMetrics metrics;

  /** Agent 运行态指标采集 */
  private final AgentRuntimeMetrics runtimeMetrics;

  /** 链路记录器 */
  private final TraceRecorder traceRecorder;

  /** 对话后处理器（封装成本/配额/记忆/事件/护栏等 LLM 调用后副作用） */
  private final ChatPostProcessor postProcessor;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  public ChatService(
      LlmClient llmClient,
      ConversationMemory memory,
      AgentProperties properties,
      GuardrailService guardrailService,
      AgentMetrics metrics,
      AgentRuntimeMetrics runtimeMetrics,
      CostAnalysisService costAnalysisService,
      TraceRecorder traceRecorder,
      AgentEventPublisher eventPublisher,
      SnowflakeIdGenerator snowflakeIdGenerator,
      TokenCostCalculator tokenCostCalculator,
      TenantQuotaService quotaService) {
    this.llmClient = llmClient;
    this.memory = memory;
    this.properties = properties;
    this.guardrailService = guardrailService;
    this.metrics = metrics;
    this.runtimeMetrics = runtimeMetrics;
    this.traceRecorder = traceRecorder;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
    // 将 5 个副作用依赖（含 memory/runtimeMetrics 副本）封装为 ChatPostProcessor
    this.postProcessor = new ChatPostProcessor(
        tokenCostCalculator,
        costAnalysisService,
        quotaService,
        memory,
        guardrailService,
        runtimeMetrics,
        eventPublisher,
        properties);
  }

  // ======================== 公开 API ========================

  /**
   * 同步对话
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param userMessage 用户消息
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @return 助手回复
   */
  public ChatResponse chat(String conversationId, String userMessage, String systemPrompt) {
    String convId = resolveConvIdOrNew(conversationId);
    String traceId = traceRecorder.startTrace(convId, "CHAT");
    String logPrefix = LOG_PREFIX_SYNC;
    log.info("{} 同步对话: convId={}, traceId={}, messageLen={}", logPrefix, convId, traceId, userMessage.length());

    runtimeMetrics.markConversationActive();

    String sanitizedInput = applyTextGuardrails(logPrefix, convId, userMessage, traceId, "simple");
    if (sanitizedInput == null) {
      return rejectAndBuildResponse(convId, traceId, "simple", false);
    }

    memory.save(convId, ChatMessage.user(sanitizedInput, convId));
    runtimeMetrics.recordMessage("user");

    ChatRequest request = buildTextRequest(convId, sanitizedInput, systemPrompt, false);
    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    String tenantId = resolveTenantId(convId);

    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    log.info("{} 成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        logPrefix, convId, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    performQuotaPreCheck(tenantId, estimatedCost);

    eventPublisher.publishExecutionStarted(executionId, tenantId, null, "CHAT", model);
    long startTime = System.currentTimeMillis();
    ChatResponse response;
    try {
      response = llmClient.chat(request);
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      handleLlmException(e, convId, traceId, "simple", model, provider, executionId, tenantId, request, duration, false);
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    return finalizeSuccess(
        logPrefix, response, convId, traceId, "simple", model, provider, executionId, tenantId,
        request, duration, false, "CHAT");
  }

  /**
   * 同步对话（多模态，Vision 模型）
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param multimodalContent 多模态内容（文本/图片段落列表）
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @return 助手回复
   */
  public ChatResponse chat(String conversationId, MessageContent multimodalContent, String systemPrompt) {
    String convId = resolveConvIdOrNew(conversationId);
    String traceId = traceRecorder.startTrace(convId, "CHAT_MULTIMODAL");
    String logPrefix = LOG_PREFIX_SYNC;
    log.info("{} 多模态同步对话: convId={}, traceId={}, partsCount={}",
        logPrefix, convId, traceId, multimodalContent.getParts().size());

    runtimeMetrics.markConversationActive();

    // 多模态模式无纯文本输入时不走文本护栏
    memory.save(convId, ChatMessage.userWithContent(multimodalContent, convId));
    runtimeMetrics.recordMessage("user");

    ChatRequest request = buildMultimodalRequest(convId, multimodalContent, systemPrompt, false);
    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    String tenantId = resolveTenantId(convId);

    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    log.info("{} 多模态成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        logPrefix, convId, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    performQuotaPreCheck(tenantId, estimatedCost);

    eventPublisher.publishExecutionStarted(executionId, tenantId, null, "CHAT_MULTIMODAL", model);
    long startTime = System.currentTimeMillis();
    ChatResponse response;
    try {
      response = llmClient.chat(request);
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      handleLlmException(e, convId, traceId, "multimodal", model, provider, executionId, tenantId, request, duration, false);
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    return finalizeSuccess(
        logPrefix, response, convId, traceId, "multimodal", model, provider, executionId, tenantId,
        request, duration, false, "CHAT_MULTIMODAL");
  }

  /**
   * 流式对话
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param userMessage 用户消息
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @param chunkConsumer 流式片段消费者
   */
  public void stream(
      String conversationId,
      String userMessage,
      String systemPrompt,
      Consumer<ChatChunk> chunkConsumer) {
    String convId = resolveConvIdOrNew(conversationId);
    String traceId = traceRecorder.startTrace(convId, "CHAT_STREAM");
    String logPrefix = LOG_PREFIX_STREAM;
    log.info("{} 流式对话: convId={}, traceId={}, messageLen={}", logPrefix, convId, traceId, userMessage.length());

    runtimeMetrics.markConversationActive();

    String sanitizedInput = applyTextGuardrails(logPrefix, convId, userMessage, traceId, "simple");
    if (sanitizedInput == null) {
      handleStreamRejection(convId, chunkConsumer);
      return;
    }

    memory.save(convId, ChatMessage.user(sanitizedInput, convId));
    runtimeMetrics.recordMessage("user");

    ChatRequest request = buildTextRequest(convId, sanitizedInput, systemPrompt, true);
    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    String tenantId = resolveTenantId(convId);

    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    log.info("{} 成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        logPrefix, convId, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    performQuotaPreCheck(tenantId, estimatedCost);

    eventPublisher.publishExecutionStarted(executionId, tenantId, null, "CHAT_STREAM", model);
    long startTime = System.currentTimeMillis();
    StringBuilder contentBuilder = new StringBuilder(COLLECTION_CAPACITY);
    final TokenUsage[] usage = {TokenUsage.zero()};
    final boolean[] firstTokenRecorded = {false};
    StreamingPiiMasker streamingMasker = new StreamingPiiMasker();

    try {
      llmClient.stream(
          request,
          chunk -> handleStreamChunk(chunk, streamingMasker, contentBuilder, usage, firstTokenRecorded,
              startTime, provider, model, chunkConsumer));
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      handleLlmException(e, convId, traceId, "simple", model, provider, executionId, tenantId, request, duration, true);
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    finalizeStreamSuccess(
        logPrefix, usage[0], convId, traceId, "simple", model, provider, executionId, tenantId,
        request, duration, contentBuilder, "CHAT_STREAM");
    log.info("{} 流式对话完成: convId={}, tokens={}, costUsd={}",
        logPrefix, convId, usage[0].getTotalTokens(),
        tokenCostCalculator.calculateActual(usage[0], model).getActualCostUsd());
  }

  /**
   * 流式对话（多模态，Vision 模型）
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param multimodalContent 多模态内容（文本/图片段落列表）
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @param chunkConsumer 流式片段消费者
   */
  public void stream(
      String conversationId,
      MessageContent multimodalContent,
      String systemPrompt,
      Consumer<ChatChunk> chunkConsumer) {
    String convId = resolveConvIdOrNew(conversationId);
    String traceId = traceRecorder.startTrace(convId, "CHAT_MULTIMODAL_STREAM");
    String logPrefix = LOG_PREFIX_STREAM;
    log.info("{} 多模态流式对话: convId={}, traceId={}, partsCount={}",
        logPrefix, convId, traceId, multimodalContent.getParts().size());

    runtimeMetrics.markConversationActive();

    // 多模态模式无纯文本输入时不走文本护栏
    memory.save(convId, ChatMessage.userWithContent(multimodalContent, convId));
    runtimeMetrics.recordMessage("user");

    ChatRequest request = buildMultimodalRequest(convId, multimodalContent, systemPrompt, true);
    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    String tenantId = resolveTenantId(convId);

    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    log.info("{} 多模态成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        logPrefix, convId, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    performQuotaPreCheck(tenantId, estimatedCost);

    eventPublisher.publishExecutionStarted(executionId, tenantId, null, "CHAT_MULTIMODAL_STREAM", model);
    long startTime = System.currentTimeMillis();
    StringBuilder contentBuilder = new StringBuilder(COLLECTION_CAPACITY);
    final TokenUsage[] usage = {TokenUsage.zero()};
    final boolean[] firstTokenRecorded = {false};
    StreamingPiiMasker streamingMasker = new StreamingPiiMasker();

    try {
      llmClient.stream(
          request,
          chunk -> handleStreamChunk(chunk, streamingMasker, contentBuilder, usage, firstTokenRecorded,
              startTime, provider, model, chunkConsumer));
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      handleLlmException(e, convId, traceId, "multimodal", model, provider, executionId, tenantId, request, duration, true);
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    finalizeStreamSuccess(
        logPrefix, usage[0], convId, traceId, "multimodal", model, provider, executionId, tenantId,
        request, duration, contentBuilder, "CHAT_MULTIMODAL_STREAM");
  }

  /**
   * 获取对话历史。
   *
   * @param conversationId 会话 ID
   * @return 对话消息列表
   */
  public List<ChatMessage> getHistory(String conversationId) {
    return memory.load(conversationId, properties.getMemory().getMaxMessages());
  }

  /**
   * 清除对话历史。
   *
   * @param conversationId 会话 ID
   */
  public void clearHistory(String conversationId) {
    memory.clear(conversationId);
  }

  // ======================== 辅助方法（消除四方法重复） ========================

  /**
   * 同步模式的护栏处理。
   *
   * @return 护栏通过后的原文，或 null 表示被拒绝
   */
  private String applyTextGuardrails(
      String logPrefix, String convId, String rawInput, String traceId, String metricsLabel) {
    String sanitized = guardrailService.applyInputGuardrails(rawInput);
    if (sanitized == null) {
      log.warn("{} 输入被安全护栏拒绝: convId={}", logPrefix, convId);
      metrics.recordGuardrailRejection("input-guardrail", "input");
      traceRecorder.recordStep(traceId, "GUARDRAIL_REJECT_INPUT",
          "Input rejected by guardrail", rawInput, "rejected", 0);
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
    }
    return sanitized;
  }

  /**
   * 同步模式的护栏拒绝响应。
   */
  private ChatResponse rejectAndBuildResponse(
      String convId, String traceId, String metricsLabel, boolean isStream) {
    ChatMessage rejectedMsg = ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
    memory.save(convId, rejectedMsg);
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution(metricsLabel, false, 0);
    return new ChatResponse(
        String.valueOf(snowflakeIdGenerator.nextId()),
        "guardrail",
        rejectedMsg,
        TokenUsage.zero(),
        "guardrail_rejected",
        List.of());
  }

  /**
   * 流式模式的护栏拒绝处理。
   */
  private void handleStreamRejection(String convId, Consumer<ChatChunk> chunkConsumer) {
    memory.save(convId, ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero()));
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution("simple", false, 0);
    chunkConsumer.accept(ChatChunk.content("", "guardrail", "抱歉，您的输入被安全护栏拒绝。"));
    chunkConsumer.accept(ChatChunk.finish("", "guardrail", "guardrail_rejected", null));
  }

  /**
   * 配额预检（调用前拦截超额请求）。
   */
  private void performQuotaPreCheck(String tenantId, CostEstimate estimatedCost) {
    if (!properties.getQuota().isEnabled()) {
      return;
    }
    TenantQuota quota = resolveTenantQuota();
    quotaService.preCheck(
        tenantId, quota, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
  }

  /**
   * 构造文本同步请求。
   */
  private ChatRequest buildTextRequest(String convId, String sanitizedInput, String systemPrompt, boolean stream) {
    List<ChatMessage> messages = buildMessages(convId, sanitizedInput, systemPrompt);
    return ChatRequest.builder()
        .model(properties.getLlm().getDefaultModel())
        .messages(messages)
        .temperature(properties.getLlm().getTemperature())
        .maxTokens(properties.getLlm().getMaxTokens())
        .isStream(stream)
        .build();
  }

  /**
   * 构造多模态同步请求。
   */
  private ChatRequest buildMultimodalRequest(String convId, MessageContent multimodalContent, String systemPrompt, boolean stream) {
    List<ChatMessage> messages = buildMessages(convId, multimodalContent, systemPrompt);
    return ChatRequest.builder()
        .model(properties.getLlm().getDefaultModel())
        .messages(messages)
        .temperature(properties.getLlm().getTemperature())
        .maxTokens(properties.getLlm().getMaxTokens())
        .isStream(stream)
        .build();
  }


  /**
   * 处理 LLM 调用异常（统一指标、链路、事件记录）。
   */
  private void handleLlmException(
      Exception e, String convId, String traceId, String metricsLabel,
      String model, String provider, String executionId, String tenantId,
      ChatRequest request, long duration, boolean isStream) {
    if (isStream) {
      metrics.recordLlmStream(provider, model, duration, null, e);
    } else {
      metrics.recordLlmCall(provider, model, duration, null, e);
    }
    traceRecorder.recordStep(traceId, "LLM_CALL_ERROR",
        isStream ? "Stream LLM call failed" : "LLM call failed",
        request, e.getMessage(), duration);
    traceRecorder.endTrace(traceId, "FAILED");
    runtimeMetrics.recordExecution(metricsLabel, false, duration);
    eventPublisher.publishExecutionFailed(executionId, tenantId, metricsLabel, model, duration, e.getMessage());
    log.error("{} LLM 调用失败，保存错误消息: convId={}, error={}",
        isStream ? LOG_PREFIX_STREAM : LOG_PREFIX_SYNC, convId, e.getMessage());
    ChatMessage errorMsg =
        ChatMessage.assistant("[错误] LLM 调用失败: " + e.getMessage(), convId, TokenUsage.zero());
    memory.save(convId, errorMsg);
  }

  /**
   * 同步模式 LLM 调用成功后处理（成本核算 + 配额 + 记忆保存 + 指标 + 事件）。
   */
  private ChatResponse finalizeSuccess(
      String logPrefix, ChatResponse response, String convId, String traceId,
      String metricsLabel, String model, String provider, String executionId, String tenantId,
      ChatRequest request, long duration, boolean isStream, String eventType) {
    metrics.recordLlmCall(provider, model, duration, response, null);
    return doFinalize(logPrefix, response.getUsage(), response.getContent(), response, convId,
        traceId, metricsLabel, model, provider, executionId, tenantId, request, duration, eventType);
  }

  /**
   * 流式模式 LLM 调用成功后处理。
   */
  private void finalizeStreamSuccess(
      String logPrefix, TokenUsage usage, String convId, String traceId,
      String metricsLabel, String model, String provider, String executionId, String tenantId,
      ChatRequest request, long duration, StringBuilder contentBuilder, String eventType) {
    metrics.recordLlmStream(provider, model, duration, usage, null);
    doFinalize(logPrefix, usage, contentBuilder.toString(), null, convId, traceId,
        metricsLabel, model, provider, executionId, tenantId, request, duration, eventType);
  }

  /**
   * 统一的成功后处理：委托给 {@link ChatPostProcessor} 完成成本核算 + 配额用量 + 输出护栏 + 记忆保存 + 指标 + 链路 + 事件。
   *
   * @param response 同步模式的完整响应（流式模式传 null）
   * @param convId 对话 ID
   * @return 同步模式的响应（流式模式返回 null）
   */
  private ChatResponse doFinalize(
      String logPrefix, TokenUsage usage, String rawContent, ChatResponse response,
      String convId, String traceId, String metricsLabel,
      String model, String provider, String executionId, String tenantId,
      ChatRequest request, long duration, String eventType) {
    // 链路追踪
    traceRecorder.recordStep(traceId, "LLM_CALL",
        request.isStream() ? "Stream LLM call" : "Chat LLM call",
        request, response != null ? response : rawContent, duration);
    traceRecorder.endTrace(traceId, "SUCCESS");

    // 委托给 ChatPostProcessor 处理成本/配额/记忆/指标/事件/护栏等操作
    return postProcessor.finalizeSuccess(
        logPrefix, usage, rawContent, response, convId, traceId,
        metricsLabel, model, provider, executionId, tenantId,
        request, duration, eventType);
  }

  /**
   * 处理流式 chunk 回调：PII 脱敏 + 首 Token 测量 + 内容累积。
   */
  private void handleStreamChunk(
      ChatChunk chunk,
      StreamingPiiMasker piiMasker,
      StringBuilder contentBuilder,
      TokenUsage[] usage,
      boolean[] firstTokenRecorded,
      long startTime,
      String provider,
      String model,
      Consumer<ChatChunk> chunkConsumer) {
    if (!firstTokenRecorded[0] && chunk.hasContent()) {
      long ttftMs = System.currentTimeMillis() - startTime;
      runtimeMetrics.recordTtft(provider, model, ttftMs);
      firstTokenRecorded[0] = true;
    }
    if (chunk.hasContent()) {
      // P0: 流式增量 PII 脱敏——先脱敏后推送，避免已发出的 token 含敏感信息
      String maskedDelta = piiMasker.mask(chunk.getDeltaContent());
      if (!maskedDelta.isEmpty()) {
        contentBuilder.append(maskedDelta);
        chunkConsumer.accept(
            ChatChunk.content(chunk.getId(), chunk.getModel(), maskedDelta, chunk.getDeltaToolCalls()));
      }
    } else if (chunk.isFinished()) {
      // 冲刷剩余缓冲：确保尾部 PII 在流结束前完成脱敏
      String maskedRest = piiMasker.flush();
      if (!maskedRest.isEmpty()) {
        contentBuilder.append(maskedRest);
        chunkConsumer.accept(ChatChunk.content(chunk.getId(), chunk.getModel(), maskedRest));
      }
      if (chunk.getUsage() != null) {
        usage[0] = chunk.getUsage();
        if (!firstTokenRecorded[0]) {
          long duration = System.currentTimeMillis() - startTime;
          runtimeMetrics.recordTtft(provider, model, duration);
        }
      }
      chunkConsumer.accept(chunk);
    } else {
      // 工具调用等非文本 chunk 原样转发
      chunkConsumer.accept(chunk);
    }
  }

  // ======================== 请求构建辅助 ========================

  private List<ChatMessage> buildMessages(
      String conversationId, String userMessage, String systemPrompt) {
    List<ChatMessage> messages = new ArrayList<>(COLLECTION_CAPACITY);
    String prompt = systemPrompt != null ? systemPrompt : getDefaultSystemPrompt();
    messages.add(ChatMessage.system(prompt));
    List<ChatMessage> history =
        memory.load(conversationId, properties.getMemory().getMaxMessages());
    messages.addAll(history);
    messages.add(ChatMessage.user(userMessage, conversationId));
    return messages;
  }

  private List<ChatMessage> buildMessages(
      String conversationId, MessageContent multimodalContent, String systemPrompt) {
    List<ChatMessage> messages = new ArrayList<>(COLLECTION_CAPACITY);
    String prompt = systemPrompt != null ? systemPrompt : getDefaultSystemPrompt();
    messages.add(ChatMessage.system(prompt));
    List<ChatMessage> history =
        memory.load(conversationId, properties.getMemory().getMaxMessages());
    messages.addAll(history);
    messages.add(ChatMessage.userWithContent(multimodalContent, conversationId));
    return messages;
  }

  private String getDefaultSystemPrompt() {
    return properties.getDefaultSystemPrompt();
  }

  /**
   * 解析对话 ID（原始 ID 为 null 时生成雪花 ID）。
   */
  private String resolveConvIdOrNew(String conversationId) {
    return conversationId != null
        ? conversationId
        : String.valueOf(snowflakeIdGenerator.nextId());
  }

  /**
   * 解析当前租户 ID。
   *
   * <p>优先从租户上下文获取，未设置时返回 "default"。
   *
   * @param convId 对话 ID（仅用于日志）
   * @return 租户 ID
   */
  private String resolveTenantId(String convId) {
    try {
      String tenantId = TenantContextHolder.getTenantId();
      return tenantId != null && !tenantId.isBlank() ? tenantId : "default";
    } catch (Exception e) {
      log.debug("[Chat] 获取租户 ID 失败，使用默认值: convId={}, error={}", convId, e.getMessage());
      return "default";
    }
  }

  /**
   * 根据配置构建租户配额对象。
   *
   * @return 租户配额配置
   */
  private TenantQuota resolveTenantQuota() {
    QuotaProperties config = properties.getQuota();
    return new TenantQuota(
        "default",
        config.getDailyTokenLimit(),
        config.getMonthlyBudgetUsd(),
        config.getAlertThreshold());
  }
}
