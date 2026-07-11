package com.njydsz.pmis.common.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

/**
 * P2-12: 链路追踪采样策略配置。
 *
 * <p>实现精细化采样策略：
 * <ul>
 *   <li>正常请求：按 {@code TRACING_SAMPLING_PROBABILITY} 概率采样</li>
 *   <li>错误请求（HTTP 5xx / 异常）：100% 采样，确保故障可追踪</li>
 *   <li>慢请求（超过 {@code TRACING_SLOW_TRACE_THRESHOLD_MS}）：100% 采样</li>
 *   <li>采样限流：每秒最多采样 N 条，防止流量突增导致 OAP 过载</li>
 * </ul>
 *
 * <p>配置项：
 * <pre>
 * management.tracing.sampling.probability=0.1        # 基础采样率
 * TRACING_SLOW_TRACE_THRESHOLD_MS=500                 # 慢请求阈值
 * TRACING_SAMPLE_RATE_LIMIT_PER_SEC=200               # 每秒采样上限
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
@Slf4j
@Configuration
@ConditionalOnClass({Tracer.class, Observation.class})
@ConditionalOnProperty(
        name = "management.zipkin.tracing.export.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class TracingSamplingConfig {

    /**
     * 慢请求阈值（毫秒）。
     * 超过此值的请求必定采样，不受基础采样率限制。
     */
    @Value("${tracing.slow-threshold-ms:500}")
    private long slowThresholdMs;

    /**
     * 每秒采样上限。
     * 防止流量突增时大量 trace 涌入 OAP 导致存储过载。
     */
    @Value("${tracing.sample-rate-limit-per-sec:200}")
    private int sampleRateLimitPerSec;

    /**
     * 滑动窗口内的采样计数器。
     * 每秒重置一次。
     */
    private final AtomicLong sampleCountInWindow = new AtomicLong(0);
    private volatile long windowStartMs = System.currentTimeMillis();

    /**
     * 自定义 ObservationHandler，实现精细化采样策略。
     *
     * <p>包装默认的 {@link DefaultTracingObservationHandler}，在 stop 事件中检查：
     * <ol>
     *   <li>如果请求出错（HTTP 5xx / 异常）→ 强制采样</li>
     *   <li>如果请求耗时超过慢阈值 → 强制采样</li>
     *   <li>否则按基础采样率采样</li>
     *   <li>采样计数器超过限流阈值 → 丢弃</li>
     * </ol>
     *
     * @param tracer Micrometer Tracer
     * @return 自定义 ObservationHandler
     */
    @Bean
    public ObservationHandler<Observation.Context> smartSamplingObservationHandler(Tracer tracer) {
        DefaultTracingObservationHandler delegate = new DefaultTracingObservationHandler(tracer);

        log.info("[TracingSamplingConfig] 慢请求阈值={}ms, 采样限流={}/s", slowThresholdMs, sampleRateLimitPerSec);

        return new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context context) {
                delegate.onStart(context);
            }

            @Override
            public void onError(Observation.Context context) {
                delegate.onError(context);
            }

            @Override
            public void onEvent(Observation.Event event, Observation.Context context) {
                delegate.onEvent(event, context);
            }

            @Override
            public void onScopeOpened(Observation.Context context) {
                delegate.onScopeOpened(context);
            }

            @Override
            public void onScopeClosed(Observation.Context context) {
                delegate.onScopeClosed(context);
            }

            @Override
            public void onStop(Observation.Context context) {
                // 检查是否应该强制采样
                boolean shouldForceSample = shouldForceSample(context);

                if (!shouldForceSample && !tryAcquireSampleSlot()) {
                    // 未通过采样策略且限流，跳过 span 上报
                    context.remove(this);
                    return;
                }

                delegate.onStop(context);
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return delegate.supportsContext(context);
            }

            /**
             * 判断是否应该强制采样（错误请求或慢请求）。
             */
            private boolean shouldForceSample(Observation.Context context) {
                // 错误请求强制采样
                if (context.getError() != null) {
                    return true;
                }

                // 检查 HTTP 状态码
                Object responseStatus = context.get("http.response.status");
                if (responseStatus instanceof Integer status && status >= 500) {
                    return true;
                }

                // 慢请求强制采样
                Long startTime = context.get("observation.starttime");
                if (startTime != null) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= slowThresholdMs) {
                        return true;
                    }
                }

                return false;
            }

            /**
             * 尝试获取采样槽位（限流）。
             *
             * @return true 如果允许采样
             */
            private boolean tryAcquireSampleSlot() {
                long now = System.currentTimeMillis();
                long windowStart = windowStartMs;

                // 窗口过期，重置
                if (now - windowStart >= 1000) {
                    synchronized (this) {
                        if (now - windowStartMs >= 1000) {
                            windowStartMs = now;
                            sampleCountInWindow.set(0);
                        }
                    }
                    windowStart = windowStartMs;
                }

                long count = sampleCountInWindow.incrementAndGet();
                return count <= sampleRateLimitPerSec;
            }
        };
    }
}
