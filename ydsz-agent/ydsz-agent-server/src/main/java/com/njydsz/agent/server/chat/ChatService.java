package com.njydsz.agent.server.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
import com.njydsz.agent.server.config.AgentProperties;
import com.njydsz.agent.server.metrics.AgentMetrics;
import com.njydsz.agent.server.metrics.AgentRuntimeMetrics;
import com.njydsz.agent.server.event.AgentEventPublisher;
import com.njydsz.agent.server.quota.TenantQuotaService;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 对话服务
 *
 * <p>提供同步和流式两种对话模式：
 *
 * <ul>
 *   <li>{@link #chat} — 同步调用，返回完整响应
 *   <li>{@link #stream} — 流式调用，逐 token 回调
 * </ul>
 *
 * <p>对话流程：
 *
 * <ol>
 *   <li>加载历史消息（滑动窗口）
 *   <li>拼接 System Prompt + 历史消息 + 用户消息
 *   <li>调用 LLM 获取响应
 *   <li>保存用户消息和助手响应到记忆
 *   <li>返回响应
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
public class ChatService {

  private static final Logger LOG = LoggerFactory.getLogger(ChatService.class);

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

  /**
   * 同步对话
   *
   * <p>流程：
   *
   * <ol>
   *   <li>应用输入护栏（Prompt 注入检测、PII 脱敏）
   *   <li>预保存用户消息（LLM 调用前持久化，避免崩溃丢失）
   *   <li>调用 LLM
   *   <li>应用输出护栏
   *   <li>保存助手响应
   * </ol>
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param userMessage 用户消息
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @return 助手回复
   */
  public ChatResponse chat(String conversationId, String userMessage, String systemPrompt) {
    String convId =
        conversationId != null ? conversationId : String.valueOf(snowflakeIdGenerator.nextId());
    String traceId = traceRecorder.startTrace(convId, "CHAT");
    LOG.info(
        "[Chat] 同步对话: convId={}, traceId={}, messageLen={}", convId, traceId, userMessage.length());

    // P2: 运行态指标埋点 — 标记会话活跃
    runtimeMetrics.markConversationActive();

    String sanitizedInput = guardrailService.applyInputGuardrails(userMessage);
    if (sanitizedInput == null) {
      LOG.warn("[Chat] 输入被安全护栏拒绝: convId={}", convId);
      metrics.recordGuardrailRejection("input-guardrail", "input");
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      ChatMessage rejectedMsg = ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero());
      memory.save(convId, rejectedMsg);
      runtimeMetrics.recordMessage("assistant");
      runtimeMetrics.recordExecution("simple", false, 0);
      return new ChatResponse(
          String.valueOf(snowflakeIdGenerator.nextId()),
          "guardrail",
          rejectedMsg,
          TokenUsage.zero(),
          "guardrail_rejected",
          List.of());
    }

    memory.save(convId, ChatMessage.user(sanitizedInput, convId));
    // P2: 记录用户消息
    runtimeMetrics.recordMessage("user");

    List<ChatMessage> messages = buildMessages(convId, sanitizedInput, systemPrompt);
    ChatRequest request =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(messages)
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .build();

    // P0: 调用前 Token 预计算 — 估算成本供配额预检与前端展示
    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    LOG.info(
        "[Chat] 成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        convId,
        estimatedCost.getEstimatedTotalTokens(),
        estimatedCost.getEstimatedCostUsd());

    // P0: 配额预检 — 调用前拦截超额请求
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      TenantQuota quota = resolveTenantQuota();
      quotaService.preCheck(
          tenantId, quota, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    }

    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    long startTime = System.currentTimeMillis();
    // P2: 发布执行启动事件
    eventPublisher.publishExecutionStarted(executionId, resolveTenantId(convId), null, "CHAT", model);
    ChatResponse response;
    try {
      response = llmClient.chat(request);
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      metrics.recordLlmCall(provider, model, duration, null, e);
      traceRecorder.recordStep(
          traceId, "LLM_CALL_ERROR", "LLM call failed", request, e.getMessage(), duration);
      traceRecorder.endTrace(traceId, "FAILED");
      // P2: 运行态指标埋点 — 执行失败
      runtimeMetrics.recordExecution("simple", false, duration);
      // P2: 发布执行失败事件
      eventPublisher.publishExecutionFailed(executionId, resolveTenantId(convId), "CHAT", model, duration, e.getMessage());
      LOG.error("[Chat] LLM 调用失败，保存错误消息: convId={}, error={}", convId, e.getMessage());
      ChatMessage errorMsg =
          ChatMessage.assistant("[错误] LLM 调用失败: " + e.getMessage(), convId, TokenUsage.zero());
      memory.save(convId, errorMsg);
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    metrics.recordLlmCall(provider, model, duration, response, null);

    // P0: 调用后精确成本核算
    CostEstimate actualCost = tokenCostCalculator.calculateActual(response.getUsage(), model);
    if (response.getUsage() != null && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, response.getUsage());
    }
    // P0: 配额用量记录 — 累加实际用量
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      quotaService.recordUsage(tenantId, actualCost);
    }
    LOG.info(
        "[Chat] 成本核算: convId={}, actualTokens={}, actualCostUsd={}",
        convId,
        actualCost.getActualTotalTokens(),
        actualCost.getActualCostUsd());
    traceRecorder.recordStep(traceId, "LLM_CALL", "Chat LLM call", request, response, duration);

    String output = guardrailService.applyOutputGuardrails(response.getContent());
    ChatMessage assistantMsg = ChatMessage.assistant(output, convId, response.getUsage());
    memory.save(convId, assistantMsg);
    // P2: 运行态指标埋点 — 助手消息 + Agent 执行成功
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution("simple", true, duration);

    LOG.info(
        "[Chat] 对话完成: convId={}, tokens={}, costUsd={}",
        convId,
        response.getUsage() != null ? response.getUsage().getTotalTokens() : 0,
        actualCost.getActualCostUsd());
    traceRecorder.endTrace(traceId, "SUCCESS");
    // P2: 发布执行完成事件
    eventPublisher.publishExecutionCompleted(
        executionId,
        resolveTenantId(convId),
        "CHAT",
        model,
        duration,
        actualCost.getActualTotalTokens(),
        actualCost.getActualCostUsd());
    return new ChatResponse(
        response.getId(),
        response.getModel(),
        assistantMsg,
        response.getUsage(),
        response.getFinishReason(),
        List.of(),
        actualCost);
  }

  /**
   * 同步对话（多模态，Vision 模型）
   *
   * <p>与 {@link #chat(String, String, String)} 流程一致，区别在于用户消息通过 {@link MessageContent} 封装多模态内容（文本+图片）。
   *
   * @param conversationId 对话 ID（null 则新建）
   * @param multimodalContent 多模态内容（文本/图片段落列表）
   * @param systemPrompt 系统提示词（null 则使用默认）
   * @return 助手回复
   */
  public ChatResponse chat(String conversationId, MessageContent multimodalContent, String systemPrompt) {
    String convId =
        conversationId != null ? conversationId : String.valueOf(snowflakeIdGenerator.nextId());
    String traceId = traceRecorder.startTrace(convId, "CHAT_MULTIMODAL");
    LOG.info(
        "[Chat] 多模态同步对话: convId={}, traceId={}, partsCount={}",
        convId, traceId, multimodalContent.getParts().size());

    // P2: 运行态指标埋点 — 标记会话活跃
    runtimeMetrics.markConversationActive();

    memory.save(convId, ChatMessage.userWithContent(multimodalContent, convId));
    // P2: 记录用户消息
    runtimeMetrics.recordMessage("user");

    List<ChatMessage> messages = buildMessages(convId, multimodalContent, systemPrompt);
    ChatRequest request =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(messages)
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .build();

    // P0: 调用前 Token 预计算 — 估算成本供配额预检与前端展示
    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    LOG.info(
        "[Chat] 多模态成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        convId,
        estimatedCost.getEstimatedTotalTokens(),
        estimatedCost.getEstimatedCostUsd());

    // P0: 配额预检 — 调用前拦截超额请求
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      TenantQuota quota = resolveTenantQuota();
      quotaService.preCheck(
          tenantId, quota, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    }

    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    long startTime = System.currentTimeMillis();
    // P2: 发布执行启动事件
    eventPublisher.publishExecutionStarted(executionId, resolveTenantId(convId), null, "CHAT_MULTIMODAL", model);
    ChatResponse response;
    try {
      response = llmClient.chat(request);
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      metrics.recordLlmCall(provider, model, duration, null, e);
      traceRecorder.recordStep(
          traceId, "LLM_CALL_ERROR", "Multimodal LLM call failed", request, e.getMessage(), duration);
      traceRecorder.endTrace(traceId, "FAILED");
      runtimeMetrics.recordExecution("multimodal", false, duration);
      eventPublisher.publishExecutionFailed(executionId, resolveTenantId(convId), "CHAT_MULTIMODAL", model, duration, e.getMessage());
      LOG.error("[Chat] 多模态 LLM 调用失败: convId={}, error={}", convId, e.getMessage());
      ChatMessage errorMsg =
          ChatMessage.assistant("[错误] LLM 调用失败: " + e.getMessage(), convId, TokenUsage.zero());
      memory.save(convId, errorMsg);
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    metrics.recordLlmCall(provider, model, duration, response, null);

    // P0: 调用后精确成本核算
    CostEstimate actualCost = tokenCostCalculator.calculateActual(response.getUsage(), model);
    if (response.getUsage() != null && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, response.getUsage());
    }
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      quotaService.recordUsage(tenantId, actualCost);
    }
    traceRecorder.recordStep(traceId, "LLM_CALL", "Multimodal chat LLM call", request, response, duration);

    String output = guardrailService.applyOutputGuardrails(response.getContent());
    ChatMessage assistantMsg = ChatMessage.assistant(output, convId, response.getUsage());
    memory.save(convId, assistantMsg);
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution("multimodal", true, duration);

    traceRecorder.endTrace(traceId, "SUCCESS");
    eventPublisher.publishExecutionCompleted(
        executionId,
        resolveTenantId(convId),
        "CHAT_MULTIMODAL",
        model,
        duration,
        actualCost.getActualTotalTokens(),
        actualCost.getActualCostUsd());
    return new ChatResponse(
        response.getId(),
        response.getModel(),
        assistantMsg,
        response.getUsage(),
        response.getFinishReason(),
        List.of(),
        actualCost);
  }

  /**
   * 流式对话
   *
   * <p>流程与 {@link #chat} 一致，区别在于 LLM 响应通过 SSE 逐 token 推送。 用户消息在 LLM 调用前预保存，LLM 失败时保存错误消息。
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
    String convId =
        conversationId != null ? conversationId : String.valueOf(snowflakeIdGenerator.nextId());
    String traceId = traceRecorder.startTrace(convId, "CHAT_STREAM");
    LOG.info(
        "[Chat-Stream] 流式对话: convId={}, traceId={}, messageLen={}",
        convId,
        traceId,
        userMessage.length());

    // P2: 运行态指标埋点 — 标记会话活跃
    runtimeMetrics.markConversationActive();

    String sanitizedInput = guardrailService.applyInputGuardrails(userMessage);
    if (sanitizedInput == null) {
      LOG.warn("[Chat-Stream] 流式输入被安全护栏拒绝: convId={}", convId);
      metrics.recordGuardrailRejection("input-guardrail", "input");
      traceRecorder.recordStep(
          traceId,
          "GUARDRAIL_REJECT_INPUT",
          "Input rejected by guardrail",
          userMessage,
          "rejected",
          0);
      traceRecorder.endTrace(traceId, "GUARDRAIL_REJECTED");
      memory.save(convId, ChatMessage.assistant("抱歉，您的输入被安全护栏拒绝。", convId, TokenUsage.zero()));
      runtimeMetrics.recordMessage("assistant");
      runtimeMetrics.recordExecution("simple", false, 0);
      chunkConsumer.accept(ChatChunk.content("", "guardrail", "抱歉，您的输入被安全护栏拒绝。"));
      chunkConsumer.accept(ChatChunk.finish("", "guardrail", "guardrail_rejected", null));
      return;
    }

    memory.save(convId, ChatMessage.user(sanitizedInput, convId));
    // P2: 记录用户消息
    runtimeMetrics.recordMessage("user");

    List<ChatMessage> messages = buildMessages(convId, sanitizedInput, systemPrompt);
    ChatRequest request =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(messages)
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .stream(true)
            .build();

    // P0: 调用前 Token 预计算 — 估算成本供配额预检与前端展示
    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    LOG.info(
        "[Chat-Stream] 成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        convId,
        estimatedCost.getEstimatedTotalTokens(),
        estimatedCost.getEstimatedCostUsd());

    // P0: 配额预检 — 调用前拦截超额请求
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      TenantQuota quota = resolveTenantQuota();
      quotaService.preCheck(
          tenantId, quota, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    }

    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    long startTime = System.currentTimeMillis();
    // P2: 发布执行启动事件
    eventPublisher.publishExecutionStarted(executionId, resolveTenantId(convId), null, "CHAT_STREAM", model);
    StringBuilder contentBuilder = new StringBuilder();
    final TokenUsage[] usage = {TokenUsage.zero()};
    // P2: 流式首 Token 测 TTFT
    final boolean[] firstTokenRecorded = {false};

    StreamingPiiMasker streamingMasker = new StreamingPiiMasker();
    try {
      llmClient.stream(
          request,
          chunk -> {
            if (!firstTokenRecorded[0] && chunk.hasContent()) {
              long ttftMs = System.currentTimeMillis() - startTime;
              runtimeMetrics.recordTtft(provider, model, ttftMs);
              firstTokenRecorded[0] = true;
            }
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
              // 冲刷剩余缓冲：确保尾部 PII 在流结束前完成脱敏
              String maskedRest = streamingMasker.flush();
              if (!maskedRest.isEmpty()) {
                contentBuilder.append(maskedRest);
                chunkConsumer.accept(
                    ChatChunk.content(chunk.getId(), chunk.getModel(), maskedRest));
              }
              if (chunk.getUsage() != null) {
                usage[0] = chunk.getUsage();
                // P2: 记录完整流式调用结果
                long duration = System.currentTimeMillis() - startTime;
                if (!firstTokenRecorded[0]) {
                  runtimeMetrics.recordTtft(provider, model, duration);
                }
              }
              chunkConsumer.accept(chunk);
            } else {
              // 工具调用等非文本 chunk 原样转发
              chunkConsumer.accept(chunk);
            }
          });
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      metrics.recordLlmStream(provider, model, duration, null, e);
      traceRecorder.recordStep(
          traceId, "LLM_CALL_ERROR", "Stream LLM call failed", request, e.getMessage(), duration);
      traceRecorder.endTrace(traceId, "FAILED");
      // P2: 运行态指标埋点 — 执行失败
      runtimeMetrics.recordExecution("simple", false, duration);
      // P2: 发布执行失败事件
      eventPublisher.publishExecutionFailed(executionId, resolveTenantId(convId), "CHAT_STREAM", model, duration, e.getMessage());
      LOG.error("[Chat-Stream] 流式 LLM 调用失败，保存错误消息: convId={}, error={}", convId, e.getMessage());
      memory.save(
          convId,
          ChatMessage.assistant("[错误] LLM 流式调用失败: " + e.getMessage(), convId, TokenUsage.zero()));
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    metrics.recordLlmStream(provider, model, duration, usage[0], null);

    // P0: 调用后精确成本核算
    CostEstimate actualCost = tokenCostCalculator.calculateActual(usage[0], model);
    if (usage[0] != null && !usage[0].equals(TokenUsage.zero()) && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, usage[0]);
    }
    // P0: 配额用量记录 — 累加实际用量
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      quotaService.recordUsage(tenantId, actualCost);
    }
    LOG.info(
        "[Chat-Stream] 成本核算: convId={}, actualTokens={}, actualCostUsd={}",
        convId,
        actualCost.getActualTotalTokens(),
        actualCost.getActualCostUsd());
    traceRecorder.recordStep(
        traceId, "LLM_CALL", "Stream LLM call", request, contentBuilder.toString(), duration);

    String output = guardrailService.applyOutputGuardrails(contentBuilder.toString());
    ChatMessage assistantMsg = ChatMessage.assistant(output, convId, usage[0]);
    memory.save(convId, assistantMsg);
    // P2: 运行态指标埋点 — 助手消息 + Agent 执行成功
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution("simple", true, duration);

    traceRecorder.endTrace(traceId, "SUCCESS");
    // P2: 发布执行完成事件
    eventPublisher.publishExecutionCompleted(
        executionId,
        resolveTenantId(convId),
        "CHAT_STREAM",
        model,
        duration,
        actualCost.getActualTotalTokens(),
        actualCost.getActualCostUsd());
    LOG.info(
        "[Chat-Stream] 流式对话完成: convId={}, tokens={}, costUsd={}",
        convId,
        usage[0].getTotalTokens(),
        actualCost.getActualCostUsd());
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
    String convId =
        conversationId != null ? conversationId : String.valueOf(snowflakeIdGenerator.nextId());
    String traceId = traceRecorder.startTrace(convId, "CHAT_MULTIMODAL_STREAM");
    LOG.info(
        "[Chat-Stream] 多模态流式对话: convId={}, traceId={}, partsCount={}",
        convId,
        traceId,
        multimodalContent.getParts().size());

    // P2: 运行态指标埋点 — 标记会话活跃
    runtimeMetrics.markConversationActive();

    memory.save(convId, ChatMessage.userWithContent(multimodalContent, convId));
    // P2: 记录用户消息
    runtimeMetrics.recordMessage("user");

    List<ChatMessage> messages = buildMessages(convId, multimodalContent, systemPrompt);
    ChatRequest request =
        ChatRequest.builder()
            .model(properties.getLlm().getDefaultModel())
            .messages(messages)
            .temperature(properties.getLlm().getTemperature())
            .maxTokens(properties.getLlm().getMaxTokens())
            .stream(true)
            .build();

    // P0: 调用前 Token 预计算 — 估算成本供配额预检与前端展示
    CostEstimate estimatedCost = tokenCostCalculator.estimateBeforeCall(request);
    LOG.info(
        "[Chat-Stream] 多模态成本估算: convId={}, estimatedTokens={}, estimatedCostUsd={}",
        convId,
        estimatedCost.getEstimatedTotalTokens(),
        estimatedCost.getEstimatedCostUsd());

    // P0: 配额预检 — 调用前拦截超额请求
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      TenantQuota quota = resolveTenantQuota();
      quotaService.preCheck(
          tenantId, quota, estimatedCost.getEstimatedTotalTokens(), estimatedCost.getEstimatedCostUsd());
    }

    String model = properties.getLlm().getDefaultModel();
    String provider = llmClient.getProvider();
    String executionId = String.valueOf(snowflakeIdGenerator.nextId());
    long startTime = System.currentTimeMillis();
    // P2: 发布执行启动事件
    eventPublisher.publishExecutionStarted(executionId, resolveTenantId(convId), null, "CHAT_MULTIMODAL_STREAM", model);
    StringBuilder contentBuilder = new StringBuilder();
    final TokenUsage[] usage = {TokenUsage.zero()};
    // P2: 流式首 Token 测 TTFT
    final boolean[] firstTokenRecorded = {false};

    StreamingPiiMasker streamingMasker = new StreamingPiiMasker();
    try {
      llmClient.stream(
          request,
          chunk -> {
            if (!firstTokenRecorded[0] && chunk.hasContent()) {
              long ttftMs = System.currentTimeMillis() - startTime;
              runtimeMetrics.recordTtft(provider, model, ttftMs);
              firstTokenRecorded[0] = true;
            }
            if (chunk.hasContent()) {
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
                long duration = System.currentTimeMillis() - startTime;
                if (!firstTokenRecorded[0]) {
                  runtimeMetrics.recordTtft(provider, model, duration);
                }
              }
              chunkConsumer.accept(chunk);
            } else {
              chunkConsumer.accept(chunk);
            }
          });
    } catch (Exception e) {
      long duration = System.currentTimeMillis() - startTime;
      metrics.recordLlmStream(provider, model, duration, null, e);
      traceRecorder.recordStep(
          traceId, "LLM_CALL_ERROR", "Multimodal stream LLM call failed", request, e.getMessage(), duration);
      traceRecorder.endTrace(traceId, "FAILED");
      runtimeMetrics.recordExecution("multimodal", false, duration);
      eventPublisher.publishExecutionFailed(executionId, resolveTenantId(convId), "CHAT_MULTIMODAL_STREAM", model, duration, e.getMessage());
      LOG.error("[Chat-Stream] 多模态流式 LLM 调用失败: convId={}, error={}", convId, e.getMessage());
      memory.save(
          convId,
          ChatMessage.assistant("[错误] LLM 流式调用失败: " + e.getMessage(), convId, TokenUsage.zero()));
      throw e;
    }
    long duration = System.currentTimeMillis() - startTime;
    metrics.recordLlmStream(provider, model, duration, usage[0], null);

    // P0: 调用后精确成本核算
    CostEstimate actualCost = tokenCostCalculator.calculateActual(usage[0], model);
    if (usage[0] != null && !usage[0].equals(TokenUsage.zero()) && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, usage[0]);
    }
    if (properties.getQuota().isEnabled()) {
      String tenantId = resolveTenantId(convId);
      quotaService.recordUsage(tenantId, actualCost);
    }
    traceRecorder.recordStep(
        traceId, "LLM_CALL", "Multimodal stream LLM call", request, contentBuilder.toString(), duration);

    String output = guardrailService.applyOutputGuardrails(contentBuilder.toString());
    ChatMessage assistantMsg = ChatMessage.assistant(output, convId, usage[0]);
    memory.save(convId, assistantMsg);
    runtimeMetrics.recordMessage("assistant");
    runtimeMetrics.recordExecution("multimodal", true, duration);

    traceRecorder.endTrace(traceId, "SUCCESS");
    eventPublisher.publishExecutionCompleted(
        executionId,
        resolveTenantId(convId),
        "CHAT_MULTIMODAL_STREAM",
        model,
        duration,
        actualCost.getActualTotalTokens(),
        actualCost.getActualCostUsd());
  }

  /** 获取对话历史 */
  public List<ChatMessage> getHistory(String conversationId) {
    return memory.load(conversationId, properties.getMemory().getMaxMessages());
  }

  /** 清除对话历史 */
  public void clearHistory(String conversationId) {
    memory.clear(conversationId);
  }

  private List<ChatMessage> buildMessages(
      String conversationId, String userMessage, String systemPrompt) {
    List<ChatMessage> messages = new ArrayList<>();
    String prompt = systemPrompt != null ? systemPrompt : getDefaultSystemPrompt();
    messages.add(ChatMessage.system(prompt));
    List<ChatMessage> history =
        memory.load(conversationId, properties.getMemory().getMaxMessages());
    messages.addAll(history);
    messages.add(ChatMessage.user(userMessage, conversationId));
    return messages;
  }

  /**
   * 构建消息列表（多模态版本）
   *
   * <p>与 {@link #buildMessages(String, String, String)} 逻辑一致，区别在于用户消息使用 {@link
   * ChatMessage#userWithContent} 封装多模态内容。
   *
   * @param conversationId 对话 ID
   * @param multimodalContent 多模态内容
   * @param systemPrompt 系统提示词
   * @return 完整消息列表（system + history + user multimodal）
   */
  private List<ChatMessage> buildMessages(
      String conversationId, MessageContent multimodalContent, String systemPrompt) {
    List<ChatMessage> messages = new ArrayList<>();
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
      LOG.debug("[Chat] 获取租户 ID 失败，使用默认值: convId={}, error={}", convId, e.getMessage());
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
}
