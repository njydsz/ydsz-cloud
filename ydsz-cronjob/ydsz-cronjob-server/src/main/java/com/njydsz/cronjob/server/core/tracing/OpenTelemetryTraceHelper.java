package com.njydsz.cronjob.server.core.tracing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.util.id.TracerUtils;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.semconv.SchemaUrls;

/**
 * OpenTelemetry 链路追踪集成辅助类（P1-2：标准化链路追踪）。
 *
 * <p>当 OpenTelemetry SDK 存在于 classpath 时自动启用，提供标准化 Span 创建与标签注入能力。
 * 不存在时优雅降级到 {@link TraceIntegrationHelper} 的 MDC 模式，不影响现有功能。
 *
 * <h3>追踪链路</h3>
 *
 * <pre>
 * [JobScanner] scan-scheduler
 *   └─ [TaskDispatcher] dispatch-task
 *      └─ [JobHandler] execute-job
 *         └─ [Callback] complete-job
 * </pre>
 *
 * <h3>Span 命名约定</h3>
 *
 * <ul>
 *   <li>{@code cronjob.scan} — 扫描周期</li>
 *   <li>{@code cronjob.dispatch} — 任务派发</li>
 *   <li>{@code cronjob.execute} — 任务执行</li>
 *   <li>{@code cronjob.complete} — 任务完成回调</li>
 * </ul>
 *
 * <h3>标签约定（OpenTelemetry Semantic Conventions）</h3>
 *
 * <ul>
 *   <li>{@code job.key} — 任务唯一标识</li>
 *   <li>{@code job.trigger} — 触发类型</li>
 *   <li>{@code job.status} — 执行结果</li>
 *   <li>{@code scheduling.delay_ms} — 调度延迟（实际触发时间 - 计划触发时间）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "io.opentelemetry.api.OpenTelemetry")
public class OpenTelemetryTraceHelper {

    /** OpenTelemetry 实例（可选注入，未配置时降级到 MDC） */
    private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

    /** 降级用的 MDC 追踪辅助 */
    private final TraceIntegrationHelper mdcTraceHelper;

    /** OTel Tracer（懒加载） */
    private volatile Tracer tracer;

    /** OTel 是否可用 */
    private volatile boolean otelAvailable = false;

    /** 当前活跃的 Span（ThreadLocal 保护） */
    private static final ThreadLocal<Span> CURRENT_SPAN = new ThreadLocal<>();

    /** 当前 Scope（用于关闭） */
    private static final ThreadLocal<Scope> CURRENT_SCOPE = new ThreadLocal<>();

    /**
     * 构造 OpenTelemetry 追踪辅助器。
     *
     * @param openTelemetryProvider OpenTelemetry 实例提供者（可选）
     * @param mdcTraceHelper MDC 降级追踪辅助
     */
    public OpenTelemetryTraceHelper(
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            TraceIntegrationHelper mdcTraceHelper) {
        this.openTelemetryProvider = openTelemetryProvider;
        this.mdcTraceHelper = mdcTraceHelper;
    }

    /**
     * 初始化 Tracer。
     *
     * <p>尝试获取 OpenTelemetry 实例，成功则创建 Tracer；失败则标记为不可用并降级到 MDC。
     */
    @PostConstruct
    public void init() {
        try {
            OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
            if (openTelemetry != null) {
                this.tracer =
                        openTracer(
                                openTelemetry,
                                "com.njydsz.cronjob",
                                getClass().getPackage().getImplementationVersion());
                this.otelAvailable = true;
                log.info("[OpenTelemetry] Tracer 初始化完成: instrumentationScopeName=com.njydsz.cronjob");
            } else {
                log.info("[OpenTelemetry] OpenTelemetry 实例不可用，降级到 MDC 追踪模式");
            }
        } catch (Exception e) {
            log.warn("[OpenTelemetry] Tracer 初始化失败，降级到 MDC 追踪模式: {}", e.getMessage());
        }
    }

    /**
     * 创建 OTel Tracer（内联调用，避免包可见性问题）。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @param scopeName 作用域名称
     * @param scopeVersion 作用域版本
     * @return Tracer 实例
     */
    private static Tracer openTracer(OpenTelemetry openTelemetry, String scopeName, String scopeVersion) {
        if (scopeVersion != null && !scopeVersion.isEmpty()) {
            return openTelemetry.getTracer(scopeName, scopeVersion);
        }
        return openTelemetry.getTracer(scopeName);
    }

    /**
     * 判断 OpenTelemetry 是否可用。
     *
     * @return {@code true} 表示 OTel 可用，可使用 {@link #startSpan(String)} 创建 Span
     */
    public boolean isAvailable() {
        return otelAvailable && tracer != null;
    }

    /**
     * 创建并开始一个 Span。
     *
     * <p>在当前 Context 下创建子 Span，自动注入 traceId 到 MDC（通过 MDC 桥接）。
     *
     * @param spanName Span 名称（如 {@code cronjob.scan}、{@code cronjob.dispatch}）
     * @return 已开始的 Span；如果 OTel 不可用返回 null
     */
    public Span startSpan(String spanName) {
        return startSpan(spanName, SpanKind.INTERNAL);
    }

    /**
     * 创建并开始一个指定类型的 Span。
     *
     * @param spanName Span 名称
     * @param kind Span 类型（INTERNAL / CLIENT / SERVER / PRODUCER / CONSUMER）
     * @return 已开始的 Span；如果 OTel 不可用返回 null
     */
    public Span startSpan(String spanName, SpanKind kind) {
        if (!isAvailable()) {
            return null;
        }
        try {
            SpanBuilder spanBuilder = tracer.spanBuilder(spanName).setSpanKind(kind);
            Span span = spanBuilder.startSpan();
            CURRENT_SPAN.set(span);
            Scope scope = Context.current().with(span).makeCurrent();
            CURRENT_SCOPE.set(scope);

            // 桥接 MDC（保证日志输出的 traceId 与 OTel Span 一致）
            String traceId = span.getSpanContext().getTraceId();
            TracerUtils.setTraceId(traceId);

            return span;
        } catch (Exception e) {
            log.debug("[OpenTelemetry] 创建 Span 失败: spanName={} reason={}", spanName, e.getMessage());
            return null;
        }
    }

    /**
     * 为任务派发创建 Span（预置任务标签）。
     *
     * @param jobKey 任务 KEY
     * @param triggerType 触发类型
     * @param shardIndex 分片索引（-1 表示非分片）
     * @return 已开始的 Span
     */
    public Span startDispatchSpan(String jobKey, String triggerType, int shardIndex) {
        Span span = startSpan("cronjob.dispatch", SpanKind.INTERNAL);
        if (span != null) {
            span.setAttribute("job.key", jobKey != null ? jobKey : "unknown");
            span.setAttribute("job.trigger", triggerType != null ? triggerType : "UNKNOWN");
            span.setAttribute("job.shard", shardIndex);
        }
        return span;
    }

    /**
     * 为扫描周期创建 Span。
     *
     * @param batchSize 扫描批次大小
     * @return 已开始的 Span
     */
    public Span startScanSpan(int batchSize) {
        Span span = startSpan("cronjob.scan", SpanKind.INTERNAL);
        if (span != null) {
            span.setAttribute("scheduler.batch_size", batchSize);
        }
        return span;
    }

    /**
     * 为任务执行创建 Span。
     *
     * @param jobKey 任务 KEY
     * @param jobType 任务类型（HTTP/GLUE/SCRIPT/BEAN）
     * @return 已开始的 Span
     */
    public Span startExecutionSpan(String jobKey, String jobType) {
        Span span = startSpan("cronjob.execute", SpanKind.INTERNAL);
        if (span != null) {
            span.setAttribute("job.key", jobKey != null ? jobKey : "unknown");
            span.setAttribute("job.type", jobType != null ? jobType : "UNKNOWN");
        }
        return span;
    }

    /**
     * 为任务完成创建 Span。
     *
     * @param jobKey 任务 KEY
     * @param success 是否成功
     * @param durationMs 执行耗时（毫秒）
     * @return 已开始的 Span
     */
    public Span startCompletionSpan(String jobKey, boolean success, long durationMs) {
        Span span = startSpan("cronjob.complete", SpanKind.INTERNAL);
        if (span != null) {
            span.setAttribute("job.key", jobKey != null ? jobKey : "unknown");
            span.setAttribute("job.status", success ? "SUCCESS" : "FAILED");
            span.setAttribute("job.duration_ms", durationMs);
        }
        return span;
    }

    /**
     * 为当前 Span 添加属性（标签）。
     *
     * @param key 属性名
     * @param value 属性值
     */
    public void setAttribute(String key, String value) {
        Span span = CURRENT_SPAN.get();
        if (span != null) {
            span.setAttribute(key, value);
        }
    }

    /**
     * 为当前 Span 添加整数属性。
     *
     * @param key 属性名
     * @param value 属性值
     */
    public void setAttribute(String key, long value) {
        Span span = CURRENT_SPAN.get();
        if (span != null) {
            span.setAttribute(key, value);
        }
    }

    /**
     * 记录当前 Span 的异常信息。
     *
     * @param throwable 异常对象
     */
    public void recordException(Throwable throwable) {
        Span span = CURRENT_SPAN.get();
        if (span != null) {
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR, throwable.getMessage());
        }
    }

    /**
     * 设置当前 Span 状态。
     *
     * @param status 状态码（OK / ERROR）
     * @param description 状态描述
     */
    public void setStatus(StatusCode status, String description) {
        Span span = CURRENT_SPAN.get();
        if (span != null) {
            span.setStatus(status, description);
        }
    }

    /**
     * 结束当前 Span。
     *
     * <p>自动清理 Scope、ThreadLocal、MDC 中的 traceId 桥接。 如果无活跃 Span，方法静默返回。
     */
    public void endSpan() {
        try {
            Span span = CURRENT_SPAN.get();
            if (span != null) {
                span.end();
            }
            Scope scope = CURRENT_SCOPE.get();
            if (scope != null) {
                scope.close();
            }
        } catch (Exception e) {
            log.debug("[OpenTelemetry] 结束 Span 异常: {}", e.getMessage());
        } finally {
            CURRENT_SPAN.remove();
            CURRENT_SCOPE.remove();
            // 清理 MDC 桥接（避免 traceId 泄漏到后续任务）
            TracerUtils.clear();
            mdcTraceHelper.clearJobTags();
        }
    }

    /**
     * 结束 Span 并记录执行结果。
     *
     * @param success 是否成功
     * @param durationMs 执行耗时（毫秒）
     * @param errorMessage 错误信息（成功时传 null）
     */
    public void endSpanWithResult(boolean success, long durationMs, String errorMessage) {
        Span span = CURRENT_SPAN.get();
        if (span != null) {
            span.setAttribute("job.status", success ? "SUCCESS" : "FAILED");
            span.setAttribute("job.duration_ms", durationMs);
            if (!success && errorMessage != null) {
                span.setAttribute("job.error", errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
                span.setStatus(StatusCode.ERROR, errorMessage);
            } else {
                span.setStatus(StatusCode.OK);
            }
        }
        endSpan();
    }

    /**
     * 获取当前 Span 的 W3C traceparent 头字符串。
     *
     * <p>用于跨服务调用时透传链路上下文。
     *
     * @return W3C traceparent 字符串；无活跃 Span 时返回空字符串
     */
    public String getCurrentTraceParent() {
        Span span = CURRENT_SPAN.get();
        if (span != null && span.getSpanContext().isValid()) {
            return "00-"
                    + span.getSpanContext().getTraceId()
                    + "-"
                    + span.getSpanContext().getSpanId()
                    + "-"
                    + (span.getSpanContext().isSampled() ? "01" : "00");
        }
        // 降级到 TracerUtils
        return TracerUtils.getCurrentTraceParent();
    }

    /**
     * 从 W3C traceparent 头恢复链路上下文。
     *
     * <p>在接收跨服务调用时调用，将上游 traceId 注入到当前线程。
     *
     * @param traceparent W3C traceparent 字符串
     * @return {@code true} 表示注入成功
     */
    public boolean injectTraceParent(String traceparent) {
        return TracerUtils.injectTraceparent(traceparent);
    }

    /**
     * 构建任务标签 Map（兼容 MDC 模式）。
     *
     * <p>当 OTel 不可用时，使用 {@link TraceIntegrationHelper} 的 MDC 模式。
     *
     * @param jobKey 任务 KEY
     * @param triggerType 触发类型
     * @param shardIndex 分片索引
     * @return 标签 Map
     */
    public Map<String, String> buildJobTags(String jobKey, String triggerType, int shardIndex) {
        return mdcTraceHelper.buildJobTags(jobKey, triggerType, shardIndex);
    }

    /**
     * 记录任务执行完成（兼容 MDC 模式）。
     *
     * @param jobKey 任务 KEY
     * @param triggerType 触发类型
     * @param success 是否成功
     * @param durationMs 执行耗时
     * @param errorMessage 错误信息
     */
    public void recordJobCompletion(
            String jobKey, String triggerType, boolean success, long durationMs, String errorMessage) {
        mdcTraceHelper.recordJobCompletion(jobKey, triggerType, success, durationMs, errorMessage);
    }

    /**
     * 清理任务标签（兼容 MDC 模式）。
     */
    public void clearJobTags() {
        mdcTraceHelper.clearJobTags();
    }
}
