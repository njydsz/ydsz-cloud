package com.njydsz.agent.server.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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
import com.njydsz.agent.domain.model.TenantQuota;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.trace.TraceRecorder;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.event.AgentEventPublisher;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.metrics.AgentRuntimeMetrics;
import com.njydsz.agent.server.quota.TenantQuotaService;
import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 对话服务
 *
 * <p>提供同步和流式两种对话模式，支持文本和多模态（Vision）两种输入。
 *
 * <p>对话流程（{@link #executeChat} 模板统一编排）：
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

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /** 对话记忆 */
  private final ConversationMemory memory;

  /** Agent 配置属性 */
  private final AgentProperties properties;

  /** 护栏编排服务（统一驱动输入/输出护栏，消除重复逻辑） */
  private final GuardrailService guardrailService;

  /** Agent 指标采集 */
  private final AgentMetrics metrics;

  /** Agent 运行态指标采集（P2 增强：活跃度、执行耗时、消息量等） */
  private final AgentRuntimeMetrics runtimeMetrics;

  /** 成本分析服务 */
  private final CostAnalysisService costAnalysisService;

  /** 链路记录器 */
  private final TraceRecorder traceRecorder;

  /** Agent 事件统一发布器 */
  private final AgentEventPublisher eventPublisher;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** Token 预计算与成本核算 */
  private final TokenCostCalculator tokenCostCalculator;

  /** 租户配额管理服务 */
  private final TenantQuotaService quotaService;

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
    this.costAnalysisService = costAnalysisService;
    this.traceRecorder = traceRecorder;
    this.eventPublisher = eventPublisher;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
    this.tokenCostCalculator = tokenCostCalculator;
    this.quotaService = quotaService;
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
    return executeChat(
        conversationId,
        userMessage,
        systemPrompt,
        "CHAT",
        "simple",
        false,
        convId -> memory.save(convId, ChatMessage.user(userMessage, convId)),
        () -> buildMessages(resolveConvIdOrNew(conversationId), userMessage, systemPrompt),
        request -> llmClient.chat(request));
  }

  /**
   * 同步对话（多模态，Vision 模型）
   *
   * <p>与 {@link #chat(String, String, String)} 流程一致，区别在于用户消息通过 {@link MessageContent} 封装多模态内容。
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param multimodalContent 多模态内容（文本/图片段落列表）
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @return 助手回复
   */
  public ChatResponse chat(String conversationId, MessageContent multimodalContent, String systemPrompt) {
    return executeChat(
        conversationId,
        null,
        systemPrompt,
        "CHAT_MULTIMODAL",
        "multimodal",
        false,
        convId -> memory.save(convId, ChatMessage.userWithContent(multimodalContent, convId)),
        () -> buildMessages(resolveConvIdOrNew(conversationId), multimodalContent, systemPrompt),
        request -> llmClient.chat(request));
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
    StreamingContext ctx = new StreamingContext(chunkConsumer);
    executeChat(
        conversationId,
        userMessage,
        systemPrompt,
        "CHAT_STREAM",
        "simple",
        true,
        convId -> memory.save(convId, ChatMessage.user(userMessage, convId)),
        () -> buildMessages(resolveConvIdOrNew(conversationId), userMessage, systemPrompt),
        request -> {
          llmClient.requestStream(
              request,
              chunk -> handleStreamingChunk(chunk, ctx),
              usage -> ctx.usage = usage);
          return ctx.buildResponse();
        });
  }

  /**
   * 流式对话（多模态，Vision 模型）
   *
   * <p>与 {@link #stream(String, String, String, Consumer)} 流程一致，区别在于用户消息通过 {@link MessageContent} 封装多模态内容。
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
    StreamingContext ctx = new StreamingContext(chunkConsumer);
    executeChat(
        conversationId,
        null,
        systemPrompt,
        "CHAT_MULTIMODAL_STREAM",
        "multimodal",
        true,
        convId -> memory.save(convId, ChatMessage.userWithContent(multimodalContent, convId)),
        () -> buildMessages(resolveConvIdOrNew(conversationId), multimodalContent, systemPrompt),
        request -> {
          llmClient.requestStream(
              request,
              chunk -> handleStreamingChunk(chunk, ctx),
              usage -> ctx.usage = usage);
          return ctx.buildResponse();
        });
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

  // ======================== 内部模板 ========================

  /**
   * 对话执行模板。
   *
   * <p>统一编排输入护栏、配额预检、LLM 调用、成本核算、输出护栏、记忆保存的完整流程，
   * 通过回调参数适配文本/多模态、同步/流式四种调用方式。
   *
   * @param conversationId 原始对话 ID（可为 null）
   * @param rawInput 原始用户输入（文本模式为消息字符串，多模态模式为 null）
   * @param systemPrompt 系统提示词（null 时使用默认）
   * @param traceType 链路追踪类型标识（如 CHAT / CHAT_STREAM）
   * @param metricsLabel 指标标签（simple / multimodal）
   * @param isStream 是否流式模式
   * @param saveUserMessage 保存用户消息的回调
   * @param buildRequest 构建 LLM 请求的回调
   * @param callLlm 调用 LLM 并获取响应的回调
   * @return 助手回复
   */
  private ChatResponse executeChat(
      String conversationId,
      String rawInput,
      String systemPrompt,
      String traceType,
      String metricsLabel,
      boolean isStream,
      Consumer<String> saveUserMessage,
      Supplier<ChatRequest> buildRequest,
      Supplier<ChatResponse> callLlm) {

    String convId = resolveConvIdOrNew(conversationId);
    String traceId = traceRecorder.startTrace(convId, traceType);
    String logPrefix = isStream ? "[Chat-Stream]" : "[Chat]";
    log.info(
        "{} 对话开始: convId={}, traceId={}, mode={}",
        logPrefix, convId, traceId, metricsLabel);

    // P2: 运行态指标埋点 — 标记会话活跃
    runtimeMetrics.markConversationActive();

    // ========== 步骤 1: 输入护栏 ==========
    String sanitizedInput = applyInputGuardrails(logPrefix, convId, rawInput, traceId, metricsLabel);
    if (sanitizedInput == null) {
      return handleGuardrailRejection(
          convId, traceId, metricsLabel, isStream, traceType);
    }

    // ========== 步骤 2: 保存用户消息 ==========
    saveUserMessage.accept(convId);
    runtimeMetrics.recordMessage("user");

    // ========== 步骤 3: 构建请求 + 配额预检 ==========
    ChatRequest request = buildRequest.get();
    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    String tenantId = resolveTenantId(convId);

    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    log.info(
        "{} 成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        logPrefix, convId, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());

    performQuotaPreCheck(logPrefix, convId, tenantId, estimatedCost);

    // ========== 步骤 4: 启动事件 & LLM 调用 ==========
    eventPublisher.publishExecutionStarted(executionId, tenantId, null, traceType, model);
    long startTime = System.currentTimeMillis();
    ChatResponse response;
    try {
      response = callLlm.get();
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      handleLlmFailure(logPrefix, e, convId, traceId, metricsLabel, model, provider,
          executionId, tenantId, request, duration, isStream);
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;

    // ========== 步骤 5: 成功路径——成本核算 + 记忆保存 + 指标 ==========
    return handleLlmSuccess(
        logPrefix, response, convId, traceId, metricsLabel, model, provider,
        executionId, tenantId, request, duration, isStream, systemPrompt);
  }

  /**
   * 处理流式 chunk 回调（PII 脱敏 + 首 Token 时间测量）。
   */
  private void handleStreamingChunk(ChatChunk chunk, StreamingContext ctx) {
    if (!ctx.firstTokenRecorded && chunk.hasContent()) {
      long ttftMs = System.currentTimeMillis() - ctx.startTime;
      ctx.firstTokenRecorded = true;
      runtimeMetrics.recordTtft(ctx.provider, ctx.model, ttftMs);
    }
    if (chunk.hasContent()) {
      String maskedDelta = ctx.piiMasker.mask(chunk.getDeltaContent());
      if (!maskedDelta.isEmpty()) {
        ctx.contentBuilder.append(maskedDelta);
        ctx.chunkConsumer.accept(
            ChatChunk.content(chunk.getId(), chunk.getModel(), maskedDelta, chunk.getDeltaToolCalls()));
      }
    } else if (chunk.isFinished()) {
      String maskedRest = ctx.piiMasker.flush();
      if (!maskedRest.isEmpty()) {
        ctx.contentBuilder.append(maskedRest);
        ctx.chunkConsumer.accept(ChatChunk.content(chunk.getId(), chunk.getModel(), maskedRest));
      }
      ctx.chunkConsumer.accept(chunk);
    } else {
      // 工具调用等非文本 chunk 原样转发
      ctx.chunkConsumer.accept(chunk);
    }
  }

  /**
   * 输入护栏处理。
   *
   * @return 脱敏后的输入护栏通过原文；护栏拒绝时返回 null
   */
  private String applyInputGuardrails(
      String logPrefix, String convId, String rawInput, String traceId, String metricsLabel) {
    String input = rawInput;
    if (input == null && metricsLabel.equals("multimodal")) {
      // 多模态模式无纯文本输入时不走文本护栏
      return "";
    }
    if (input == null) {
      return null;
    }
    String sanitized = guardrailService.applyInputGuardrails(input);
    if (sanitized == null) {
      log.warn("{} 输入被安全护栏拒绝: convId={}", logPrefix, convId);
      metrics.recordGuardrailRejection("input-guardrail", "input");
    }
    return sanitized;
  }

  /**
   * 处理护栏拒绝场景。
   */
  private ChatResponse handleGuardrailRejection(
      String convId, String traceId, String metricsLabel, boolean isStream, String traceType) {
    traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
    ChatMessage rejectedMsg =
        ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
    memory.save(convId, rejectedMsg);
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution(metricsLabel, false, 0);
    if (isStream) {
      // 流式模式通过返回值标识拒绝（调用方已结束流）
    }
    return new ChatResponse(
        String.valueOf(snowflakeIdGenerator.nextId()),
        "guardrail",
        rejectedMsg,
        TokenUsage.zero(),
        "guardrail_rejected",
        List.of());
  }

  /**
   * 配额预检：调用前拦截超额请求。
   */
  private void performQuotaPreCheck(
      String logPrefix, String convId, String tenantId, CostEstimate estimatedCost) {
    if (!properties.getQuota().isEnabled()) {
      return;
    }
    TenantQuota quota = resolveTenantQuota();
    quotaService.preCheck(
        tenantId, quota, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    log.debug("{} 配额预检通过: convId={}", logPrefix, convId);
  }

  /**
   * 处理 LLM 调用异常。
   */
  private void handleLlmFailure(
      String logPrefix,
      Exception e,
      String convId,
      String traceId,
      String metricsLabel,
      String model,
      String provider,
      String executionId,
      String tenantId,
      ChatRequest request,
      long duration,
      boolean isStream) {
    if (isStream) {
      metrics.recordLlmStream(provider, model, duration, null, e);
    } else {
      metrics.recordLlmCall(provider, model, duration, null, e);
    }
    traceRecorder.recordStep(
        traceId, "LLM_CALL_ERROR",
        isStream ? "Stream LLM call failed" : "LLM call failed",
        request, e.getMessage(), duration);
    traceRecorder.endTrace(traceId, "FAILED");
    runtimeMetrics.recordExecution(metricsLabel, false, duration);
    eventPublisher.publishExecutionFailed(executionId, tenantId, metricsLabel, model, duration, e.getMessage());
    log.error("{} LLM 调用失败，保存错误消息: convId={}, error={}", logPrefix, convId, e.getMessage());
    ChatMessage errorMsg =
        ChatMessage.assistant("[错误] LLM 调用失败: " + e.getMessage(), convId, TokenUsage.zero());
    memory.save(convId, errorMsg);
  }

  /**
   * 处理 LLM 调用成功。
   */
  private ChatResponse handleLlmSuccess(
      String logPrefix,
      ChatResponse response,
      String convId,
      String traceId,
      String metricsLabel,
      String model,
      String provider,
      String executionId,
      String tenantId,
      ChatRequest request,
      long duration,
      boolean isStream,
      String systemPrompt) {
    TokenUsage usage = response.getUsage();

    if (isStream) {
      metrics.recordLlmStream(provider, model, duration, usage, null);
    } else {
      metrics.recordLlmCall(provider, model, duration, response, null);
    }

    // P0: 调用后精确成本核算
    CostEstimate actualCost = tokenCostCalculator.calculateActual(usage, model);
    if (usage != null && !usage.equals(TokenUsage.zero()) && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, usage);
    }
    // P0: 配额用量记录 — 累加实际用量
    if (properties.getQuota().isEnabled()) {
      quotaService.recordUsage(tenantId, actualCost);
    }
    log.info(
        "{} 成本核算: convId={}, actualTokens={}, actualCostUsd={}",
        logPrefix, convId, actualCost.getActualTotalTokens(), actualCost.getActualCostUsd());
    traceRecorder.recordStep(traceId, "LLM_CALL",
        isStream ? "Stream LLM call" : "Chat LLM call", request, response, duration);

    // 输出护栏 + 记忆保存
    String output = guardrailService.applyOutputGuardrails(response.getContent());
    ChatMessage assistantMsg = ChatMessage.assistant(output, convId, usage);
    memory.save(convId, assistantMsg);
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution(metricsLabel, true, duration);

    traceRecorder.endTrace(traceId, "SUCCESS");
    eventPublisher.publishExecutionCompleted(
        executionId, tenantId, metricsLabel, model, duration,
        actualCost.getActualTotalTokens(), actualCost.getActualCostUsd());
    log.info(
        "{} 对话完成: convId={}, tokens={}, costUsd={}",
        logPrefix, convId,
        usage != null ? usage.getTotalTokens() : 0,
        actualCost.getActualCostUsd());

    return new ChatResponse(
        response.getId(),
        response.getModel(),
        assistantMsg,
        usage,
        response.getFinishReason(),
        List.of(),
        actualCost);
  }

  // ======================== 辅助方法 ========================

  /**
   * 解析对话 ID（原始 ID 为 null 时生成雪花 ID）。
   */
  private String resolveConvIdOrNew(String conversationId) {
    return conversationId != null
        ? conversationId
        : String.valueOf(snowflakeIdGenerator.nextId());
  }

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
    AgentProperties.Quota config = properties.getQuota();
    return new TenantQuota(
        "default",
        config.getDailyTokenLimit(),
        config.getMonthlyBudgetUsd(),
        config.getAlertThreshold());
  }

  // ======================== 内部数据载体 ========================

  /**
   * 流式执行上下文载体。
   *
   * <p>封装流式 LLM 调用过程中的状态数据，避免多维数组/原子引用散落在方法体中。
   */
  private static final class StreamingContext {
    final Consumer<ChatChunk> chunkConsumer;
    final String provider;
    final String model;
    final long startTime;
    final StringBuilder contentBuilder;
    final StreamingPiiMasker piiMasker;
    TokenUsage usage;
    boolean firstTokenRecorded;

    StreamingContext(Consumer<ChatChunk> chunkConsumer) {
      this.chunkConsumer = chunkConsumer;
      this.provider = null;
      this.model = null;
      this.startTime = System.currentTimeMillis();
      this.contentBuilder = new StringBuilder();
      this.piiMasker = new StreamingPiiMasker();
      this.usage = TokenUsage.zero();
      this.firstTokenRecorded = false;
    }

    ChatResponse buildResponse() {
      return new ChatResponse(
          String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextLong()),
          model,
          null,
          usage,
          "stop",
          List.of());
    }
  }
}
