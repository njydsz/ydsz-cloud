package com.njydsz.common.sentry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.SlaDefinition;
import com.njydsz.common.sentry.spi.AlertPublisher;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.SlaCollector;
import com.njydsz.common.sentry.spi.TraceContext;

/**
 * 可观测性统一 API 门面（Facade）。
 *
 * <p>为业务模块提供静态入口，透明封装底层 SPI 实现（Micrometer / SkyWalking / Loki 等），
 * 业务方无需注入多个 Bean 即可完成常见可观测性操作：
 * <pre>{@code
 * // 计时埋点
 * SentryObservation.time("order.create", () -> {
 *     orderService.create(dto);
 * });
 *
 * // 计数埋点
 * SentryObservation.count("order.count", 1, Map.of("status", "success"));
 *
 * // 告警
 * SentryObservation.alert(AlertEvent.builder()
 *     .severity(AlertSeverity.P0)
 *     .message("订单错误率超阈值")
 *     .build());
 *
 * // 获取当前 traceId
 * String traceId = SentryObservation.traceId();
 * }</pre>
 *
 * <p>本类采用懒加载设计：首次调用时通过 {@link ServiceLoaderFacade} 发现 Spring 容器中的 Bean 实例，
 * 之后直接引用；所有方法均为线程安全且对底层 SPI 不可用的场景做了降级（no-op）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MetricsCollector
 * @see TraceContext
 * @see LogPublisher
 * @see AlertPublisher
 * @see SlaCollector
 */
@Slf4j
public final class SentryObservation {

    /** 是否已完成初始化（register 已被调用） */
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /**
     * SPI 聚合持有者（懒加载）
     */
    private static final class Holder {

        static final SpiBundle INSTANCE = ServiceLoaderFacade.load();
    }

    /**
     * SPI 聚合对象，持有所有可观测性组件引用
     */
    private static final class SpiBundle {

        private final AtomicReference<MetricsCollector> metrics = new AtomicReference<>();
        private final AtomicReference<TraceContext> trace = new AtomicReference<>();
        private final AtomicReference<LogPublisher> logging = new AtomicReference<>();
        private final AtomicReference<AlertPublisher> alerting = new AtomicReference<>();
        private final AtomicReference<SlaCollector> sla = new AtomicReference<>();

        void setMetrics(MetricsCollector collector) {
            if (collector != null) {
                metrics.set(collector);
            }
        }

        void setTrace(TraceContext context) {
            if (context != null) {
                trace.set(context);
            }
        }

        void setLogging(LogPublisher publisher) {
            if (publisher != null) {
                logging.set(publisher);
            }
        }

        void setAlerting(AlertPublisher publisher) {
            if (publisher != null) {
                alerting.set(publisher);
            }
        }

        void setSla(SlaCollector collector) {
            if (collector != null) {
                sla.set(collector);
            }
        }

        MetricsCollector metrics() {
            return metrics.get();
        }

        TraceContext trace() {
            return trace.get();
        }

        LogPublisher logging() {
            return logging.get();
        }

        AlertPublisher alerting() {
            return alerting.get();
        }

        SlaCollector sla() {
            return sla.get();
        }
    }

    private SentryObservation() {
        // 静态门面，禁止实例化
    }

    // ==================== 初始化 ====================

    /**
     * 注册 SPI 实现到门面。
     *
     * <p>由 {@link SentryAutoConfiguration} 内部调用，业务模块无需关心注册时机。
     *
     * @param metricsCollector 指标采集器
     * @param traceContext     链路追踪上下文
     * @param logPublisher     日志发布器
     * @param alertPublisher   告警发布器
     * @param slaCollector     SLA 采集器
     */
    public static void register(MetricsCollector metricsCollector,
                                TraceContext traceContext,
                                LogPublisher logPublisher,
                                AlertPublisher alertPublisher,
                                SlaCollector slaCollector) {
        SpiBundle bundle = Holder.INSTANCE;
        bundle.setMetrics(metricsCollector);
        bundle.setTrace(traceContext);
        bundle.setLogging(logPublisher);
        bundle.setAlerting(alertPublisher);
        bundle.setSla(slaCollector);
        INITIALIZED.set(true);
    }

    /**
     * 检查门面是否已完成初始化，未初始化时输出告警日志。
     *
     * <p>SentryObservation 需由 {@code SelfMonitorAutoConfiguration} 在 {@code @PostConstruct}
     * 中调用 {@link #register} 完成 SPI 注册。业务方在容器启动完成前（如静态初始化块、
     * {@code @PostConstruct} 早于自监控配置时）调用本门面方法会走到 no-op 分支，
     * 此处通过日志提醒开发者排查注册时序。
     *
     * @return {@code true} 表示已初始化
     */
    private static boolean checkInitialized() {
        if (INITIALIZED.get()) {
            return true;
        }
        log.warn("[Sentry] SentryObservation 未完成初始化，本次调用将 no-op。" +
                "请检查 SelfMonitorAutoConfiguration 是否正确装配");
        return false;
    }

    // ==================== Metrics ====================

    /**
     * 计数埋点（递增 1）。
     *
     * @param name        指标名称（建议以 ydsz. 开头）
     * @param description 指标描述
     * @param tags        标签（可为 {@code null}）
     */
    public static void count(String name, String description, Map<String, String> tags) {
        MetricsCollector collector = Holder.INSTANCE.metrics();
        if (collector != null) {
            collector.incrementCounter(name, description, tags);
        } else {
            checkInitialized();
        }
    }

    /**
     * 计数埋点（指定递增量）。
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签（可为 {@code null}）
     * @param amount      递增量
     */
    public static void count(String name, String description, Map<String, String> tags, double amount) {
        MetricsCollector collector = Holder.INSTANCE.metrics();
        if (collector != null) {
            collector.incrementCounter(name, description, tags, amount);
        } else {
            checkInitialized();
        }
    }

    /**
     * 计时埋点：执行操作并自动记录耗时。
     *
     * <p>无论操作是否抛出异常都会记录耗时，异常不会传播到调用方之外
     * （仍由操作自身抛出，但耗时一定被记录）。
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签（可为 {@code null}）
     * @param operation   要执行的操作
     * @return 操作的返回值
     * @throws Exception 操作执行中的异常
     */
    public static <T> T time(String name, String description, Map<String, String> tags,
                             CheckedSupplier<T> operation) throws Exception {
        MetricsCollector collector = Holder.INSTANCE.metrics();
        long start = System.currentTimeMillis();
        try {
            return operation.get();
        } finally {
            if (collector != null) {
                long tookMillis = System.currentTimeMillis() - start;
                collector.recordTimer(name, description, tags, Duration.ofMillis(tookMillis));
            } else {
                checkInitialized();
            }
        }
    }

    /**
     * 计时埋点（无返回值版）。
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签（可为 {@code null}）
     * @param operation   要执行的操作
     */
    public static void time(String name, String description, Map<String, String> tags,
                            Runnable operation) {
        MetricsCollector collector = Holder.INSTANCE.metrics();
        long start = System.currentTimeMillis();
        try {
            operation.run();
        } finally {
            if (collector != null) {
                long tookMillis = System.currentTimeMillis() - start;
                collector.recordTimer(name, description, tags, Duration.ofMillis(tookMillis));
            }
        }
    }

    /**
     * 设置 Gauge 指标值。
     *
     * @param name        指标名称
     * @param description 指标描述
     * @param tags        标签（可为 {@code null}）
     * @param value       值
     */
    public static void gauge(String name, String description, Map<String, String> tags, double value) {
        MetricsCollector collector = Holder.INSTANCE.metrics();
        if (collector != null) {
            collector.setGauge(name, description, tags, value);
        } else {
            checkInitialized();
        }
    }

    // ==================== Tracing ====================

    /**
     * 获取当前 TraceId。
     *
     * @return 当前 TraceId，未在追踪链路中时返回 {@code null}
     */
    public static String traceId() {
        TraceContext context = Holder.INSTANCE.trace();
        if (context == null) {
            checkInitialized();
            return null;
        }
        return context.getTraceId();
    }

    /**
     * 获取当前 SpanId。
     *
     * @return 当前 SpanId，未在追踪链路中时返回 {@code null}
     */
    public static String spanId() {
        TraceContext context = Holder.INSTANCE.trace();
        if (context == null) {
            checkInitialized();
            return null;
        }
        return context.getSpanId();
    }

    /**
     * 判断当前是否在追踪链路中。
     *
     * @return {@code true} 表示在追踪链路中
     */
    public static boolean isTracing() {
        TraceContext context = Holder.INSTANCE.trace();
        return context != null && context.isTracing();
    }

    /**
     * 向当前 Span 注入标签。
     *
     * @param key   标签键
     * @param value 标签值
     */
    public static void tag(String key, String value) {
        TraceContext context = Holder.INSTANCE.trace();
        if (context != null) {
            context.tag(key, value);
        } else {
            checkInitialized();
        }
    }

    // ==================== Alerting ====================

    /**
     * 发布告警事件（经收敛后可能被丢弃）。
     *
     * @param event 告警事件
     * @return 是否真正发布成功
     */
    public static boolean alert(AlertEvent event) {
        AlertPublisher publisher = Holder.INSTANCE.alerting();
        if (publisher == null) {
            checkInitialized();
            return false;
        }
        return publisher.publish(event);
    }

    // ==================== SLA ====================

    /**
     * 注册 SLA 定义。
     *
     * @param definition SLA 定义
     */
    public static void registerSla(SlaDefinition definition) {
        SlaCollector collector = Holder.INSTANCE.sla();
        if (collector != null) {
            collector.register(definition);
        } else {
            checkInitialized();
        }
    }

    /**
     * 记录 SLA 执行结果。
     *
     * @param name       SLA 名称
     * @param stepName   步骤名
     * @param tookMillis 耗时（毫秒）
     * @param success    是否成功
     */
    public static void recordSla(String name, String stepName, long tookMillis, boolean success) {
        SlaCollector collector = Holder.INSTANCE.sla();
        if (collector != null) {
            collector.record(name, stepName, tookMillis, success);
        } else {
            checkInitialized();
        }
    }

    // ==================== Functional Interface ====================

    /**
     * 可抛异常的 Supplier。
     *
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface CheckedSupplier<T> {
        /**
         * 获取结果。
         *
         * @return 结果
         * @throws Exception 执行异常
         */
        T get() throws Exception;
    }
}
