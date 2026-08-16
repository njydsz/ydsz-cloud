package com.njydsz.common.sentry;

import java.time.Duration;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.SlaDefinition;
import com.njydsz.common.sentry.spi.AlertPublisher;
import com.njydsz.common.sentry.spi.LogPublisher;
import com.njydsz.common.sentry.spi.MetricsCollector;
import com.njydsz.common.sentry.spi.SlaCollector;
import com.njydsz.common.sentry.spi.TraceContext;

/**
 * 可观测性服务（Spring Bean）。
 *
 * <p>作为 {@link SentryObservation} 的实例方法版替代，通过构造器注入 SPI 实现，
 * 支持依赖注入、AOP 拦截、Mock 替换等 Spring 生态能力。
 *
 * <p>业务方可选择：
 * <ul>
 *   <li>注入 {@link SentryService}：享受完整的 DI 能力，便于单元测试替换</li>
 *   <li>继续使用 {@link SentryObservation} 静态方法：向后兼容，内部委托本 Bean</li>
 * </ul>
 *
 * <p>v2.0.0 新增：替代原静态门面模式，解决 ServiceLocator 反模式问题。
 *
 * @author ydsz-team
 * @since 2.0.0
 * @see SentryObservation
 */
@Slf4j
@Component
public class SentryService {

    private final MetricsCollector metricsCollector;
    private final TraceContext traceContext;
    private final LogPublisher logPublisher;
    private final AlertPublisher alertPublisher;
    private final SlaCollector slaCollector;

    /**
     * 构造器注入所有 SPI 实现。
     *
     * <p>任一参数可为 {@code null}：Spring 在找不到对应 Bean 时会传入 null，
     * 各方法内部已做 null 安全处理。
     *
     * @param metricsCollector 指标采集器
     * @param traceContext     链路追踪上下文
     * @param logPublisher     日志发布器
     * @param alertPublisher   告警发布器
     * @param slaCollector     SLA 采集器
     */
    public SentryService(MetricsCollector metricsCollector,
                         TraceContext traceContext,
                         LogPublisher logPublisher,
                         AlertPublisher alertPublisher,
                         SlaCollector slaCollector) {
        this.metricsCollector = metricsCollector;
        this.traceContext = traceContext;
        this.logPublisher = logPublisher;
        this.alertPublisher = alertPublisher;
        this.slaCollector = slaCollector;
        log.info("[Sentry] SentryService 初始化完成: metrics={}, trace={}, logging={}, alerting={}, sla={}",
                metricsCollector != null, traceContext != null, logPublisher != null,
                alertPublisher != null, slaCollector != null);
    }

    // ==================== Metrics ====================

    /**
     * 计数埋点（递增 1）。
     *
     * @param name        指标名称（建议以 ydsz. 开头）
     * @param description 指标描述
     * @param tags        标签（可为 {@code null}）
     */
    public void count(String name, String description, Map<String, String> tags) {
        if (metricsCollector != null) {
            metricsCollector.incrementCounter(name, description, tags);
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
    public void count(String name, String description, Map<String, String> tags, double amount) {
        if (metricsCollector != null) {
            metricsCollector.incrementCounter(name, description, tags, amount);
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
     * @param <T>         返回值类型
     * @return 操作的返回值
     * @throws Exception 操作执行中的异常
     */
    public <T> T time(String name, String description, Map<String, String> tags,
                      CheckedSupplier<T> operation) throws Exception {
        long start = System.currentTimeMillis();
        try {
            return operation.get();
        } finally {
            if (metricsCollector != null) {
                long tookMillis = System.currentTimeMillis() - start;
                metricsCollector.recordTimer(name, description, tags, Duration.ofMillis(tookMillis));
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
    public void time(String name, String description, Map<String, String> tags,
                     Runnable operation) {
        long start = System.currentTimeMillis();
        try {
            operation.run();
        } finally {
            if (metricsCollector != null) {
                long tookMillis = System.currentTimeMillis() - start;
                metricsCollector.recordTimer(name, description, tags, Duration.ofMillis(tookMillis));
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
    public void gauge(String name, String description, Map<String, String> tags, double value) {
        if (metricsCollector != null) {
            metricsCollector.setGauge(name, description, tags, value);
        }
    }

    // ==================== Tracing ====================

    /**
     * 获取当前 TraceId。
     *
     * @return 当前 TraceId，未在追踪链路中时返回 {@code null}
     */
    public String traceId() {
        return traceContext != null ? traceContext.getTraceId() : null;
    }

    /**
     * 获取当前 SpanId。
     *
     * @return 当前 SpanId，未在追踪链路中时返回 {@code null}
     */
    public String spanId() {
        return traceContext != null ? traceContext.getSpanId() : null;
    }

    /**
     * 判断当前是否在追踪链路中。
     *
     * @return {@code true} 表示在追踪链路中
     */
    public boolean isTracing() {
        return traceContext != null && traceContext.isTracing();
    }

    /**
     * 向当前 Span 注入标签。
     *
     * @param key   标签键
     * @param value 标签值
     */
    public void tag(String key, String value) {
        if (traceContext != null) {
            traceContext.tag(key, value);
        }
    }

    // ==================== Alerting ====================

    /**
     * 发布告警事件（经收敛后可能被丢弃）。
     *
     * @param event 告警事件
     * @return 是否真正发布成功
     */
    public boolean alert(AlertEvent event) {
        return alertPublisher != null && alertPublisher.publish(event);
    }

    // ==================== SLA ====================

    /**
     * 注册 SLA 定义。
     *
     * @param definition SLA 定义
     */
    public void registerSla(SlaDefinition definition) {
        if (slaCollector != null) {
            slaCollector.register(definition);
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
    public void recordSla(String name, String stepName, long tookMillis, boolean success) {
        if (slaCollector != null) {
            slaCollector.record(name, stepName, tookMillis, success);
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
