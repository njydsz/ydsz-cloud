package com.remisoft.common.metrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 框架内部 Micrometer 指标门面。
 *
 * <p><b>仅供框架内部使用</b>（过滤器、拦截器等基础设施）。
 * 业务模块请使用 {@code remi-common-sentry} 中的 {@code MetricsCollector} SPI。</p>
 *
 * <p>上报两类指标：
 * <ul>
 *   <li>{@code remi.response.total} — 按 response_code 分组的响应计数（Counter）</li>
 *   <li>{@code remi.request.hold_time} — 请求上下文持有时间（Timer，含 p50/p95/p99 百分位）</li>
 * </ul>
 *
 * <p><b>两种使用方式：</b></p>
 * <ul>
 *   <li><b>静态方法（向后兼容）：</b>委托给内部 MetricsAccessor，
 *       Micrometer 不可用时为 no-op</li>
 *   <li><b>DI 使用：</b>通过 Spring 容器注入 {@code MetricsAccessor} 实例，
 *       适用于需要模拟测试或自定义后端的场景</li>
 * </ul>
 *
 * <pre>{@code
 * // 静态方式（默认）
 * FrameworkMetrics.incrementResponse("A00000");
 * FrameworkMetrics.recordHoldTime(Duration.ofMillis(50));
 *
 * // DI 方式（可选）
 * @Autowired
 * private MetricsAccessor metrics;
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * @see MetricsAccessor
 */
public final class FrameworkMetrics {

    private FrameworkMetrics() {
    }

    /**
     * Micrometer 指标访问接口。
     *
     * <p>提供响应计数与耗时记录两个核心操作。默认 {@link #NO_OP} 实现为无操作，
     * Spring 容器中的实际实现会委托给 MeterRegistry。</p>
     *
     * @since 1.8.0
     */
    public interface MetricsAccessor {

        /**
         * 上报一次响应结果。
         *
         * @param responseCode 响应码字符串（如 A00000、C99999）
         */
        void incrementResponse(String responseCode);

        /**
         * 上报一次请求上下文的持有时间。
         *
         * @param holdTime 上下文持有时间（不为 null）
         */
        void recordHoldTime(Duration holdTime);

        /**
         * 默认无操作实现。
         */
        MetricsAccessor NO_OP = new MetricsAccessor() {
            @Override
            public void incrementResponse(String responseCode) {
                // no-op
            }

            @Override
            public void recordHoldTime(Duration holdTime) {
                // no-op
            }
        };
    }

    private static volatile MetricsAccessor accessor = MetricsAccessor.NO_OP;

    /**
     * 注册实际的 MetricsAccessor 实现（由 {@code MetricsAutoConfiguration} 调用）。
     *
     * <p>仅允许注册一次；重复注册将被忽略并记录警告。</p>
     *
     * @param newAccessor 新的 accessor 实现，不能为 null
     */
    public static void registerAccessor(MetricsAccessor newAccessor) {
        if (newAccessor == null) {
            throw new IllegalArgumentException("MetricsAccessor must not be null");
        }
        MetricsAccessor previous = accessor;
        if (previous != MetricsAccessor.NO_OP) {
            LoggerFactory.getLogger(FrameworkMetrics.class)
                    .warn("MetricsAccessor already registered, ignoring subsequent registration. "
                            + "Previous: {}, new: {}", previous.getClass().getName(),
                            newAccessor.getClass().getName());
            return;
        }
        accessor = newAccessor;
    }

    /**
     * 重置 accessor 为 no-op 状态（仅用于测试）。
     */
    public static void __testResetAccessor() {
        accessor = MetricsAccessor.NO_OP;
    }

    /**
     * 上报一次响应结果。
     *
     * <p>若当前未注册 Micrometer accessor，调用为无操作（no-op）。</p>
     *
     * @param responseCode 响应码字符串（如 A00000、C99999）
     */
    public static void incrementResponse(String responseCode) {
        if (responseCode == null || responseCode.isBlank()) {
            return;
        }
        accessor.incrementResponse(responseCode);
    }

    /**
     * 上报一次请求上下文的持有时间。
     *
     * <p>由过滤器的 finally 块在关闭请求上下文时调用。
     * 若 Micrometer 不可用，则为 no-op。</p>
     *
     * @param holdTime 上下文持有时间（不为 null）
     */
    public static void recordHoldTime(Duration holdTime) {
        if (holdTime == null || holdTime.isNegative()) {
            return;
        }
        accessor.recordHoldTime(holdTime);
    }

    /**
     * 基于 Micrometer MeterRegistry 创建实际的 MetricsAccessor 实现。
     *
     * @param registry MeterRegistry 实例
     * @return MetricsAccessor 实例
     */
    public static MetricsAccessor createAccessor(MeterRegistry registry) {
        return new MicrometerMetricsAccessor(registry);
    }

    /**
     * Micrometer 实现类。
     */
    private static final class MicrometerMetricsAccessor implements MetricsAccessor {

        private final MeterRegistry registry;

        MicrometerMetricsAccessor(MeterRegistry registry) {
            this.registry = registry;
        }

        @Override
        public void incrementResponse(String responseCode) {
            try {
                registry.counter("remi.response.total",
                        "response_code", responseCode).increment();
            } catch (Exception e) {
                LoggerFactory.getLogger(MicrometerMetricsAccessor.class)
                        .warn("Failed to increment response counter for code={}: {}", responseCode, e.getMessage());
            }
        }

        @Override
        public void recordHoldTime(Duration holdTime) {
            try {
                Timer.builder("remi.request.hold_time")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(registry)
                        .record(holdTime.toNanos(), TimeUnit.NANOSECONDS);
            } catch (Exception e) {
                LoggerFactory.getLogger(MicrometerMetricsAccessor.class)
                        .warn("Failed to record hold time: {}", e.getMessage());
            }
        }
    }

}
