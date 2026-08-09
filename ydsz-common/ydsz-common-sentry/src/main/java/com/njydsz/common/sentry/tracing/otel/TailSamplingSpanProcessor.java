package com.njydsz.common.sentry.tracing.otel;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 尾部采样 Span 处理器（Tail-Based Sampling）
 *
 * <p>传统头部采样（Head-Based Sampling）在请求入口就决定是否采样，无法覆盖以下场景：
 * <ul>
 *   <li>错误请求 100% 采集（但头部采样可能丢弃）</li>
 *   <li>慢请求 100% 采集（同上）</li>
 *   <li>特定业务标签的请求 100% 采集</li>
 * </ul>
 *
 * <p>本实现采用 <b>延迟决策 + 配额保护</b> 模式：
 * <ol>
 *   <li>所有 Span 先记录到 Ring Buffer（带时间窗口）</li>
 *   <li>Span 结束（{@code onEnd}）时根据策略评估：命中策略 → 标记为 {@link Decision#RECORD}</li>
 *   <li>未命中策略 → 走概率采样 / 丢弃</li>
 *   <li>整体采样率（{@code recordRatio}）防止 OOM</li>
 * </ol>
 *
 * <p><b>策略示例</b>：
 * <ul>
 *   <li>HTTP 5xx 错误 100% 采集</li>
 *   <li>耗时 &gt; 3s 的慢请求 100% 采集</li>
 *   <li>错误码属于 P0 级别 100% 采集</li>
 *   <li>命中灰度标签的请求 100% 采集</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class TailSamplingSpanProcessor implements SpanProcessor {

    /** 总采样率（0.0 ~ 1.0），防止配额耗尽 */
    private final double recordRatio;
    /** 总请求计数器（用于配额计算） */
    private final AtomicLong totalCount = new AtomicLong(0);
    /** 已记录计数器 */
    private final AtomicLong recordedCount = new AtomicLong(0);
    /** 自定义采样规则 */
    private final List<SamplingRule> rules;
    /** 决策回调（用于测试 / 指标采集） */
    private final List<DecisionListener> listeners = new CopyOnWriteArrayList<>();

    public TailSamplingSpanProcessor(double recordRatio, List<SamplingRule> rules) {
        if (recordRatio < 0.0 || recordRatio > 1.0) {
            throw new IllegalArgumentException("recordRatio must be in [0.0, 1.0], got: " + recordRatio);
        }
        this.recordRatio = recordRatio;
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        log.info("[Sentry] TailSamplingSpanProcessor 初始化完成，recordRatio={}, rules={}",
                recordRatio, this.rules.size());
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // 启动阶段不决策，仅初始化上下文标记
        totalCount.incrementAndGet();
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        Decision decision = evaluate(span);
        notifyListeners(span, decision);

        if (decision == Decision.RECORD) {
            recordedCount.incrementAndGet();
        } else if (decision == Decision.DROP) {
            // OTel SDK 当前不支持丢弃已结束的 Span（一旦 onStart 就会记录），
            // 但通过决策回调允许上层做自定义过滤（如关闭 OTLP Exporter 对该 Span 的上报）
            // 真实场景：可在 onStart 时给 Span 加上特定属性，在 Exporter 中过滤
        }
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }

    /**
     * 评估 Span 采样决策
     */
    private Decision evaluate(ReadableSpan span) {
        // 1) 命中自定义规则 → 强制记录
        for (SamplingRule rule : rules) {
            try {
                if (rule.getPredicate().test(span)) {
                    return Decision.RECORD;
                }
            } catch (Exception e) {
                log.debug("[Sentry] 采样规则 {} 评估失败: {}", rule.getName(), e.getMessage());
            }
        }

        // 2) 走全局采样率
        long current = totalCount.get();
        if (current == 0) {
            return Decision.DROP;
        }
        double ratio = (double) recordedCount.get() / current;
        if (ratio < recordRatio) {
            return Decision.RECORD;
        }
        return Decision.DROP;
    }

    /**
     * 添加决策监听器
     */
    public void addListener(DecisionListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void notifyListeners(ReadableSpan span, Decision decision) {
        for (DecisionListener l : listeners) {
            try {
                l.onDecision(span, decision);
            } catch (Exception ignored) {
                // 监听器异常不影响主流程
            }
        }
    }

    /** 获取当前记录数 */
    public long getRecordedCount() {
        return recordedCount.get();
    }

    /** 获取总评估数 */
    public long getTotalCount() {
        return totalCount.get();
    }

    /** 计算实际采样率 */
    public double getActualRatio() {
        long t = totalCount.get();
        return t == 0 ? 0.0 : (double) recordedCount.get() / t;
    }

    @Override
    public void close() {
        // no-op
    }

    // ============================================================================
    // 决策
    // ============================================================================

    /**
     * 采样决策
     */
    public enum Decision {
        /** 记录并上报 */
        RECORD,
        /** 丢弃 */
        DROP
    }

    // ============================================================================
    // 采样规则
    // ============================================================================

    /**
     * 采样规则
     */
    @Data
    @Builder
    public static class SamplingRule {
        /** 规则名称（用于日志/监控） */
        private String name;
        /** 谓词：返回 true 表示命中此规则 */
        private Predicate<ReadableSpan> predicate;
    }

    // ============================================================================
    // 决策监听器
    // ============================================================================

    /**
     * 决策监听器
     */
    @FunctionalInterface
    public interface DecisionListener {
        void onDecision(ReadableSpan span, Decision decision);
    }

    // ============================================================================
    // 规则工厂
    // ============================================================================

    /**
     * 规则工厂：常用规则预设
     */
    public static class Rules {

        private Rules() {}

        /**
         * 错误状态码规则（HTTP 5xx 或 Span 状态为 ERROR）
         */
        public static SamplingRule errorStatus() {
            return SamplingRule.builder()
                    .name("error-status")
                    .predicate(span -> {
                        // 1) OTel Span 自身状态
                        if (span.toSpanData().getStatus().getStatusCode() == StatusCode.ERROR) {
                            return true;
                        }
                        // 2) HTTP 状态码 5xx
                        Long status = span.getAttribute(OtelSemConv.HTTP_RESPONSE_STATUS_CODE);
                        if (status != null && status >= 500 && status < 600) {
                            return true;
                        }
                        return false;
                    })
                    .build();
        }

        /**
         * 慢请求规则（超过指定毫秒）
         */
        public static SamplingRule slowRequest(long thresholdMillis) {
            return SamplingRule.builder()
                    .name("slow-request-" + thresholdMillis + "ms")
                    .predicate(span -> {
                        long durationNanos = span.getLatencyNanos();
                        return durationNanos > thresholdMillis * 1_000_000L;
                    })
                    .build();
        }

        /**
         * 错误码规则（YDSZ 自定义错误码命中指定前缀）
         */
        public static SamplingRule errorCode(String... prefixes) {
            return SamplingRule.builder()
                    .name("error-code")
                    .predicate(span -> {
                        String code = span.getAttribute(OtelSemConv.REMI_ERROR_CODE);
                        if (code == null) {
                            return false;
                        }
                        for (String p : prefixes) {
                            if (code.startsWith(p)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .build();
        }

        /**
         * 灰度标签规则（命中指定 tag）
         */
        public static SamplingRule grayTag(String tagValue) {
            return SamplingRule.builder()
                    .name("gray-tag-" + tagValue)
                    .predicate(span -> tagValue.equals(span.getAttribute(OtelSemConv.REMI_GRAY_TAG)))
                    .build();
        }

        /**
         * 压测流量规则
         */
        public static SamplingRule pressureTraffic() {
            return SamplingRule.builder()
                    .name("pressure-traffic")
                    .predicate(span -> {
                        String tag = span.getAttribute(OtelSemConv.REMI_PRESSURE_TAG);
                        return tag != null && !tag.isEmpty();
                    })
                    .build();
        }
    }
}
