package com.njydsz.agent.server.metrics;

import java.util.concurrent.TimeUnit;


import com.njydsz.agent.domain.gateway.CacheMetricsRecorder;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * Agent 模块 Micrometer 指标
 *
 * <p>P2-3: 继承 {@link SentryMetricsAdapter} 统一指标命名前缀管理， 符合《云顶编码规范》第 27.2.1 节「禁止直接操作
 * MeterRegistry」的强制要求。
 *
 * <p>暴露以下 Prometheus 指标：
 *
 * <ul>
 *   <li>{@code agent_llm_calls_total{provider,model,status}} — LLM 调用次数（成功/失败）
 *   <li>{@code agent_llm_call_duration_seconds{provider,model}} — LLM 调用耗时
 *   <li>{@code agent_llm_tokens_total{provider,model,type}} — Token 消耗（prompt/completion）
 *   <li>{@code agent_guardrail_rejections_total{guard,direction}} — 安全护栏拒绝次数
 *   <li>{@code agent_cache_hits_total{provider}} / {@code agent_cache_misses_total{provider}} — LLM 缓存命中/未命中
 * </ul>
 *
 * <p><b>DDD 合规</b>：实现 domain 层 {@link CacheMetricsRecorder} SPI， 供 infra 层
 * {@code CachedLlmClient} 回调上报缓存指标，避免 infra 反向依赖 server 层。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class AgentMetrics extends SentryMetricsAdapter implements CacheMetricsRecorder {

  /** LLM 调用次数指标名 */
  private static final String METRIC_LLM_CALLS = "llm_calls_total";

  /** LLM 调用耗时指标名 */
  private static final String METRIC_LLM_DURATION = "llm_call_duration_seconds";

  /** LLM Token 消耗指标名 */
  private static final String METRIC_LLM_TOKENS = "llm_tokens_total";

  /** 安全护栏拒绝次数指标名 */
  private static final String METRIC_GUARDRAIL_REJECTIONS = "guardrail_rejections_total";

  /** LLM 缓存命中次数指标名 */
  private static final String METRIC_CACHE_HITS = "cache_hits_total";

  /** LLM 缓存未命中次数指标名 */
  private static final String METRIC_CACHE_MISSES = "cache_misses_total";

  public AgentMetrics() {
    super("agent_");
  }

  /**
   * 记录 LLM 同步调用结果
   *
   * @param provider Provider 名称
   * @param model 模型名称
   * @param durationMs 耗时（毫秒）
   * @param response 响应（null 表示失败）
   * @param error 异常（null 表示成功）
   */
  public void recordLlmCall(
      String provider, String model, long durationMs, ChatResponse response, Throwable error) {
    String status = error == null ? "success" : "failure";
    String errorType =
        error instanceof LlmException le
            ? le.getErrorType().name()
            : error != null ? "UNKNOWN" : "NONE";

    counter(
            METRIC_LLM_CALLS,
            "provider",
            provider,
            "model",
            model,
            "status",
            status,
            "error_type",
            errorType)
        .increment();

    timer(METRIC_LLM_DURATION, "provider", provider, "model", model)
        .record(durationMs, TimeUnit.MILLISECONDS);

    if (response != null && response.getUsage() != null) {
      TokenUsage usage = response.getUsage();
      incrementCounter(
          METRIC_LLM_TOKENS,
          usage.getPromptTokens(),
          "provider",
          provider,
          "model",
          model,
          "type",
          "prompt");
      incrementCounter(
          METRIC_LLM_TOKENS,
          usage.getCompletionTokens(),
          "provider",
          provider,
          "model",
          model,
          "type",
          "completion");
    }
  }

  /**
   * 记录 LLM 流式调用结果
   *
   * @param provider Provider 名称
   * @param model 模型名称
   * @param durationMs 耗时（毫秒）
   * @param tokenUsage Token 用量（null 表示失败）
   * @param error 异常（null 表示成功）
   */
  public void recordLlmStream(
      String provider, String model, long durationMs, TokenUsage tokenUsage, Throwable error) {
    String status = error == null ? "success" : "failure";
    String errorType =
        error instanceof LlmException le
            ? le.getErrorType().name()
            : error != null ? "UNKNOWN" : "NONE";

    counter(
            METRIC_LLM_CALLS,
            "provider",
            provider,
            "model",
            model,
            "status",
            status,
            "error_type",
            errorType,
            "mode",
            "stream")
        .increment();

    timer(METRIC_LLM_DURATION, "provider", provider, "model", model)
        .record(durationMs, TimeUnit.MILLISECONDS);

    if (tokenUsage != null) {
      incrementCounter(
          METRIC_LLM_TOKENS,
          tokenUsage.getPromptTokens(),
          "provider",
          provider,
          "model",
          model,
          "type",
          "prompt");
      incrementCounter(
          METRIC_LLM_TOKENS,
          tokenUsage.getCompletionTokens(),
          "provider",
          provider,
          "model",
          model,
          "type",
          "completion");
    }
  }

  /**
   * 记录安全护栏拒绝
   *
   * @param guardName 护栏名称
   * @param direction 方向（input/output）
   */
  public void recordGuardrailRejection(String guardName, String direction) {
    incrementCounter(METRIC_GUARDRAIL_REJECTIONS, "guard", guardName, "direction", direction);
  }

  /**
   * 记录 LLM 缓存命中。
   *
   * @param provider Provider 名称
   */
  public void recordCacheHit(String provider) {
    incrementCounter(METRIC_CACHE_HITS, "provider", provider);
  }

  /**
   * 记录 LLM 缓存未命中。
   *
   * @param provider Provider 名称
   */
  public void recordCacheMiss(String provider) {
    incrementCounter(METRIC_CACHE_MISSES, "provider", provider);
  }
}
