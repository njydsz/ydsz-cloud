package com.njydsz.agent.server.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.config.AgentProperties;
import com.njydsz.agent.domain.config.properties.QuotaProperties;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.CostEstimate;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.server.analytics.CostAnalysisService;
import com.njydsz.agent.server.event.AgentEventPublisher;
import com.njydsz.agent.server.metrics.AgentRuntimeMetrics;
import com.njydsz.agent.server.quota.TenantQuotaService;

/**
 * 对话后处理器 — 封装 LLM 调用成功后的副作用操作（成本核算、配额记录、记忆保存、事件发布）。
 *
 * <p>从 {@link ChatService} 中抽取此逻辑，将 5 个副作用依赖（costAnalysisService、quotaService、
 * memory、runtimeMetrics、eventPublisher）隔离为单一职责组件，使 ChatService 专注于对话流程编排。
 *
 * <p>处理流程：精确成本核算 → 配额用量记录 → 链路追踪 → 输出护栏 → 记忆保存 → 指标记录 → 事件发布。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@Component
public class ChatPostProcessor {

  /** 流式日志前缀 */
  private static final String LOG_PREFIX_STREAM = "[Chat-Stream]";
  /** 同步日志前缀 */
  private static final String LOG_PREFIX_SYNC = "[Chat]";

  private final TokenCostCalculator tokenCostCalculator;
  private final CostAnalysisService costAnalysisService;
  private final TenantQuotaService quotaService;
  private final ConversationMemory memory;
  private final GuardrailService guardrailService;
  private final AgentRuntimeMetrics runtimeMetrics;
  private final AgentEventPublisher eventPublisher;
  private final AgentProperties properties;

  /**
   * 构造对话后处理器。
   *
   * @param tokenCostCalculator Token 成本计算器
   * @param costAnalysisService 成本分析服务
   * @param quotaService        租户配额服务
   * @param memory              对话记忆
   * @param guardrailService    护栏编排服务
   * @param runtimeMetrics      运行态指标
   * @param eventPublisher      事件发布器
   * @param properties          Agent 配置
   */
  public ChatPostProcessor(
      TokenCostCalculator tokenCostCalculator,
      CostAnalysisService costAnalysisService,
      TenantQuotaService quotaService,
      ConversationMemory memory,
      GuardrailService guardrailService,
      AgentRuntimeMetrics runtimeMetrics,
      AgentEventPublisher eventPublisher,
      AgentProperties properties) {
    this.tokenCostCalculator = tokenCostCalculator;
    this.costAnalysisService = costAnalysisService;
    this.quotaService = quotaService;
    this.memory = memory;
    this.guardrailService = guardrailService;
    this.runtimeMetrics = runtimeMetrics;
    this.eventPublisher = eventPublisher;
    this.properties = properties;
  }

  /**
   * 执行成功后副作用处理（同步 / 流式通用）。
   *
   * <p>封装成本核算、配额记录、链路、护栏、记忆保存、指标、事件的完整流程。
   * 返回值为同步模式的 {@link ChatResponse}（流式模式返回 null）。
   *
   * @param logPrefix   日志前缀
   * @param usage       Token 用量
   * @param rawContent  原始输出文本
   * @param response    同步模式完整响应（流式传 null）
   * @param convId      对话 ID
   * @param traceId     链路追踪 ID
   * @param metricsLabel 指标标签
   * @param model       模型名称
   * @param provider    模型提供方
   * @param executionId 执行 ID
   * @param tenantId    租户 ID
   * @param request     LLM 请求对象
   * @param duration    调用耗时（毫秒）
   * @param eventType   事件类型
   * @return 同步模式响应（流式返回 null）
   */
  public ChatResponse finalizeSuccess(
      String logPrefix, TokenUsage usage, String rawContent, ChatResponse response,
      String convId, String traceId, String metricsLabel,
      String model, String provider, String executionId, String tenantId,
      ChatRequest request, long duration, String eventType) {
    // 1. 精确成本核算
    CostEstimate actualCost = tokenCostCalculator.calculateActual(usage, model);
    if (usage != null && !usage.equals(TokenUsage.zero()) && costAnalysisService != null) {
      costAnalysisService.recordUsage(convId, model, usage);
    }
    // 2. 配额用量记录
    if (properties.getQuota().isEnabled()) {
      quotaService.recordUsage(tenantId, actualCost);
    }
    log.info("{} 成本核算: convId={}, actualTokens={}, actualCostUsd={}",
        logPrefix, convId, actualCost.getActualTotalTokens(), actualCost.getActualCostUsd());

    // 3. 链路追踪
    request.isStream();
    log.info("{} 链路记录: convId={}, duration={}ms", logPrefix, convId, duration);

    // 4. 输出护栏
    String output = guardrailService.applyOutputGuardrails(rawContent);

    // 5. 记忆保存
    ChatMessage assistantMsg = ChatMessage.assistant(output, convId, usage);
    memory.save(convId, assistantMsg);
    runtimeMetrics.recordMessage("assistant");

    // 6. 指标记录
    runtimeMetrics.recordExecution(metricsLabel, true, duration);

    // 7. 事件发布
    eventPublisher.publishExecutionCompleted(
        executionId, tenantId, eventType, model, duration,
        actualCost.getActualTotalTokens(), actualCost.getActualCostUsd());

    if (response != null) {
      return new ChatResponse(
          response.getId(), response.getModel(), assistantMsg, usage,
          response.getFinishReason(), java.util.List.of(), actualCost);
    }
    return null;
  }
}
