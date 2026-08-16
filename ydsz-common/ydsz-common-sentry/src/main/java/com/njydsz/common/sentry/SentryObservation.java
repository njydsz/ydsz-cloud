package com.njydsz.common.sentry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
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
 * <p>v2.0.0 变更：内部委托从自实现 Holder 改为 Spring 容器中的
 * {@link SentryService} Bean，解决静态门面测试困难、生命周期模糊问题。
 *
 * <p>业务方可选择：
 * <ul>
 *   <li>继续使用 {@link SentryObservation} 静态方法：向后兼容</li>
 *   <li>直接注入 {@link SentryService}：享受 DI 能力与 Mock 测试便利</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see SentryService
 * @see MetricsCollector
 * @see TraceContext
 * @see LogPublisher
 * @see AlertPublisher
 * @see SlaCollector
 */
@Slf4j
@Component
public class SentryObservation implements ApplicationContextAware {

    /** 是否已完成初始化（Spring 上下文已注入） */
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /** Spring 上下文（静态持有，用于静态方法委托） */
    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        applicationContext = ctx;
        INITIALIZED.set(true);
        log.info("[Sentry] SentryObservation 静态门面已通过 Spring 上下文初始化");
    }

    /**
     * 获取 {@link SentryService} Bean。
     *
     * @return SentryService 实例，Spring 上下文不可用时返回 {@code null}
     */
    private static SentryService getService() {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(SentryService.class);
        } catch (BeansException e) {
            log.debug("[Sentry] SentryService Bean 未找到（Spring 上下文未装配 ydsz-common-sentry）");
            return null;
        }
    }

    /**
     * 检查门面是否已完成初始化，未初始化时输出告警日志。
     *
     * @return {@code true} 表示已初始化
     */
    private static boolean checkInitialized() {
        if (INITIALIZED.get()) {
            return true;
        }
        log.warn("[Sentry] SentryObservation 未完成初始化，本次调用将 no-op。" +
                "请检查 Spring 上下文是否正确装配 ydzs-common-sentry 模块");
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
        SentryService service = getService();
        if (service != null) {
            service.count(name, description, tags);
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
        SentryService service = getService();
        if (service != null) {
            service.count(name, description, tags, amount);
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
     * @param <T>         返回值类型
     * @return 操作的返回值
     * @throws Exception 操作执行中的异常
     */
    public static <T> T time(String name, String description, Map<String, String> tags,
                             SentryService.CheckedSupplier<T> operation) throws Exception {
        SentryService service = getService();
        if (service != null) {
            return service.time(name, description, tags, operation);
        }
        checkInitialized();
        return operation.get();
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
        SentryService service = getService();
        if (service != null) {
            service.time(name, description, tags, operation);
        } else {
            checkInitialized();
            operation.run();
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
        SentryService service = getService();
        if (service != null) {
            service.gauge(name, description, tags, value);
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
        SentryService service = getService();
        return service != null ? service.traceId() : null;
    }

    /**
     * 获取当前 SpanId。
     *
     * @return 当前 SpanId，未在追踪链路中时返回 {@code null}
     */
    public static String spanId() {
        SentryService service = getService();
        return service != null ? service.spanId() : null;
    }

    /**
     * 判断当前是否在追踪链路中。
     *
     * @return {@code true} 表示在追踪链路中
     */
    public static boolean isTracing() {
        SentryService service = getService();
        return service != null && service.isTracing();
    }

    /**
     * 向当前 Span 注入标签。
     *
     * @param key   标签键
     * @param value 标签值
     */
    public static void tag(String key, String value) {
        SentryService service = getService();
        if (service != null) {
            service.tag(key, value);
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
        SentryService service = getService();
        return service != null && service.alert(event);
    }

    // ==================== SLA ====================

    /**
     * 注册 SLA 定义。
     *
     * @param definition SLA 定义
     */
    public static void registerSla(SlaDefinition definition) {
        SentryService service = getService();
        if (service != null) {
            service.registerSla(definition);
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
        SentryService service = getService();
        if (service != null) {
            service.recordSla(name, stepName, tookMillis, success);
        } else {
            checkInitialized();
        }
    }
}
