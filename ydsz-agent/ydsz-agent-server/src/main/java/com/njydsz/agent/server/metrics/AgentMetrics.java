package com.njydsz.agent.server.metrics;

import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.njydsz.agent.domain.gateway.LlmException;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * Agent 模块 Micrometer 指标
 *
 * <p>P2-3: 继承 {@link SentryMetricsAdapter} 统一指标命名前缀管理。
 *
 * <p>暴露以下 Prometheus 指标：
 * <ul>
 *   <li>{@code agent_llm_calls_total{provider,model,status}} — LLM 调用次数（成功/失败）</li>
 *   <li>{@code agent_llm_call_duration_seconds{provider,model}} — LLM 调用耗时</li>
 *   <li>{@code agent_llm_tokens_total{provider,model,type}} — Token 消耗（prompt/completion）</li>
 *   <li>{@code agent_guardrail_rejections_total{guard,direction}} — 安全护栏拒绝次数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AgentMetrics extends SentryMetricsAdapter {

    /** LLM 调用次数指标名 */
    private static final String METRIC_LLM_CALLS = "agent_llm_calls_total";
    /** LLM 调用耗时指标名 */
    private static final String METRIC_LLM_DURATION = "agent_llm_call_duration_seconds";
    /** LLM Token 消耗指标名 */
    private static final String METRIC_LLM_TOKENS = "agent_llm_tokens_total";
    /** 安全护栏拒绝次数指标名 */
    private static final String METRIC_GUARDRAIL_REJECTIONS = "agent_guardrail_rejections_total";

    public AgentMetrics(MeterRegistry meterRegistry) {
        super(meterRegistry, "agent_");
    }

    /**
     * 记录 LLM 同步调用结果
     *
     * @param provider  Provider 名称
     * @param model     模型名称
     * @param durationMs 耗时（毫秒）
     * @param response  响应（null 表示失败）
     * @param error     异常（null 表示成功）
     */
    public void recordLlmCall(String provider, String model, long durationMs,
                              ChatResponse response, Throwable error) {
        String status = error == null ? "success" : "failure";
        String errorType = error instanceof LlmException le
                ? le.getErrorType().name() : error != null ? "UNKNOWN" : "NONE";

        registry.counter(METRIC_LLM_CALLS,
                "provider", provider,
                "model", model,
                "status", status,
                "error_type", errorType).increment();

        Timer.builder(METRIC_LLM_DURATION)
                .tag("provider", provider)
                .tag("model", model)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        if (response != null && response.getUsage() != null) {
            TokenUsage usage = response.getUsage();
            registry.counter(METRIC_LLM_TOKENS,
                    "provider", provider,
                    "model", model,
                    "type", "prompt").increment(usage.getPromptTokens());
            registry.counter(METRIC_LLM_TOKENS,
                    "provider", provider,
                    "model", model,
                    "type", "completion").increment(usage.getCompletionTokens());
        }
    }

    /**
     * 记录 LLM 流式调用结果
     *
     * @param provider   Provider 名称
     * @param model      模型名称
     * @param durationMs 耗时（毫秒）
     * @param tokenUsage Token 用量（null 表示失败）
     * @param error      异常（null 表示成功）
     */
    public void recordLlmStream(String provider, String model, long durationMs,
                                TokenUsage tokenUsage, Throwable error) {
        String status = error == null ? "success" : "failure";
        String errorType = error instanceof LlmException le
                ? le.getErrorType().name() : error != null ? "UNKNOWN" : "NONE";

        registry.counter(METRIC_LLM_CALLS,
                "provider", provider,
                "model", model,
                "status", status,
                "error_type", errorType,
                "mode", "stream").increment();

        Timer.builder(METRIC_LLM_DURATION)
                .tag("provider", provider)
                .tag("model", model)
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);

        if (tokenUsage != null) {
            registry.counter(METRIC_LLM_TOKENS,
                    "provider", provider,
                    "model", model,
                    "type", "prompt").increment(tokenUsage.getPromptTokens());
            registry.counter(METRIC_LLM_TOKENS,
                    "provider", provider,
                    "model", model,
                    "type", "completion").increment(tokenUsage.getCompletionTokens());
        }
    }

    /**
     * 记录安全护栏拒绝
     *
     * @param guardName 护栏名称
     * @param direction 方向（input/output）
     */
    public void recordGuardrailRejection(String guardName, String direction) {
        registry.counter(METRIC_GUARDRAIL_REJECTIONS,
                "guard", guardName,
                "direction", direction).increment();
    }
}
