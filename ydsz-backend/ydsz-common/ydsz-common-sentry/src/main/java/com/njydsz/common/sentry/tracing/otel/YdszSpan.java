package com.njydsz.common.sentry.tracing.otel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import lombok.extern.slf4j.Slf4j;

/**
 * YDSZ Span 构建器（业务友好的 OTel API 封装）
 *
 * <p>对 OTel API 的链式 Builder 进行业务向封装，提供：
 * <ul>
 *   <li>统一注入租户 / 用户 / 业务单号 / 灰度标签等 YDSZ 自定义属性</li>
 *   <li>try-with-resources 风格的 Scope 自动关闭</li>
 *   <li>异常自动记录（异常类型、消息、堆栈摘要）</li>
 *   <li>常用预置 Span（DB / HTTP / MQ）</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 业务 Span
 * YdszSpan span = YdszSpan.builder(tracer, "order.create")
 *         .kind(SpanKind.INTERNAL)
 *         .tenantId("acme")
 *         .userId("u-1001")
 *         .module("order")
 *         .action("create")
 *         .tag("orderType", "B2B")
 *         .start();
 *
 * try (Scope ignored = span.scope()) {
 *     // 业务逻辑
 *     orderService.create(req);
 * } catch (Exception e) {
 *     span.recordException(e);
 *     span.error("ORDER_CREATE_FAILED", e.getMessage());
 *     throw e;
 * } finally {
 *     span.end();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class YdszSpan {

    private final Span span;
    private final long startNanos;

    private YdszSpan(Span span) {
        this.span = span;
        this.startNanos = System.nanoTime();
    }

    /**
     * 创建 Builder
     *
     * @param tracer  OTel Tracer（可通过 {@code Sentry.getTracer()} 获取）
     * @param spanName Span 名称
     */
    public static Builder builder(Tracer tracer, String spanName) {
        return new Builder(tracer, spanName);
    }

    /**
     * 创建带命名空间的 Builder
     */
    public static Builder builder(Tracer tracer, String namespace, String operation) {
        return new Builder(tracer, OtelSemConv.spanName(namespace, operation));
    }

    /**
     * 获取底层 OTel Span
     */
    public Span span() {
        return span;
    }

    /**
     * 设置属性
     */
    public YdszSpan setAttribute(String key, String value) {
        if (value != null) {
            span.setAttribute(key, value);
        }
        return this;
    }

    public YdszSpan setAttribute(String key, long value) {
        span.setAttribute(key, value);
        return this;
    }

    public YdszSpan setAttribute(String key, boolean value) {
        span.setAttribute(key, value);
        return this;
    }

    public YdszSpan setAttribute(AttributeKey<Long> key, long value) {
        span.setAttribute(key, value);
        return this;
    }

    public YdszSpan setAttribute(AttributeKey<String> key, String value) {
        if (value != null) {
            span.setAttribute(key, value);
        }
        return this;
    }

    /**
     * 批量设置属性
     */
    public YdszSpan setAttributes(Map<String, Object> attrs) {
        if (attrs != null && !attrs.isEmpty()) {
            Attributes otelAttrs = OtelSemConv.toAttributes(attrs);
            span.setAllAttributes(otelAttrs);
        }
        return this;
    }

    /**
     * 记录异常
     */
    public YdszSpan recordException(Throwable t) {
        if (t != null) {
            span.recordException(t);
            return this;
        }
        return this;
    }

    /**
     * 标记为错误并设置状态
     */
    public YdszSpan error(String errorCode, String message) {
        if (errorCode != null) {
            span.setAttribute(OtelSemConv.YDSZ_ERROR_CODE, errorCode);
        }
        if (message != null) {
            span.setStatus(StatusCode.ERROR, message);
        } else {
            span.setStatus(StatusCode.ERROR);
        }
        return this;
    }

    /**
     * 标记为 OK
     */
    public YdszSpan ok() {
        span.setStatus(StatusCode.OK);
        return this;
    }

    /**
     * 激活 Span 上下文（必须配合 try-with-resources 关闭）
     */
    public Scope scope() {
        return span.makeCurrent();
    }

    /**
     * 获取当前激活 Span
     */
    public static Span current() {
        return Span.current();
    }

    /**
     * 结束 Span（必须调用，否则内存泄漏）
     */
    public void end() {
        try {
            span.end();
        } catch (Exception e) {
            log.debug("[YdszSpan] 结束 Span 失败: {}", e.getMessage());
        }
    }

    /**
     * 获取自构造以来的纳秒数
     */
    public long elapsedNanos() {
        return System.nanoTime() - startNanos;
    }

    /**
     * 获取自构造以来的毫秒数
     */
    public long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(elapsedNanos());
    }

    // ============================================================================
    // Builder
    // ============================================================================

    /**
     * Span 构建器
     */
    public static class Builder {
        private final SpanBuilder builder;

        public Builder(Tracer tracer, String spanName) {
            this.builder = tracer.spanBuilder(spanName);
        }

        public Builder kind(SpanKind kind) {
            builder.setSpanKind(kind);
            return this;
        }

        public Builder parent(Span parent) {
            if (parent != null) {
                builder.setParent(Context.current().with(parent));
            }
            return this;
        }

        public Builder tenantId(String tenantId) {
            return setAttr(OtelSemConv.YDSZ_TENANT_ID, tenantId);
        }

        public Builder userId(String userId) {
            return setAttr(OtelSemConv.YDSZ_USER_ID, userId);
        }

        public Builder businessNo(String businessNo) {
            return setAttr(OtelSemConv.YDSZ_BUSINESS_NO, businessNo);
        }

        public Builder module(String module) {
            return setAttr(OtelSemConv.YDSZ_MODULE, module);
        }

        public Builder action(String action) {
            return setAttr(OtelSemConv.YDSZ_ACTION, action);
        }

        public Builder clientType(String clientType) {
            return setAttr(OtelSemConv.YDSZ_CLIENT_TYPE, clientType);
        }

        public Builder grayTag(String grayTag) {
            return setAttr(OtelSemConv.YDSZ_GRAY_TAG, grayTag);
        }

        public Builder pressureTag(String pressureTag) {
            return setAttr(OtelSemConv.YDSZ_PRESSURE_TAG, pressureTag);
        }

        public Builder tag(String key, String value) {
            if (key != null && value != null && !value.isEmpty()) {
                builder.setAttribute(key, value);
            }
            return this;
        }

        public Builder tag(String key, long value) {
            builder.setAttribute(key, value);
            return this;
        }

        public Builder attributes(Map<String, Object> attrs) {
            if (attrs != null && !attrs.isEmpty()) {
                builder.setAllAttributes(OtelSemConv.toAttributes(attrs));
            }
            return this;
        }

        private Builder setAttr(AttributeKey<String> key, String value) {
            if (value != null && !value.isEmpty()) {
                builder.setAttribute(key, value);
            }
            return this;
        }

        /**
         * 启动 Span
         */
        public YdszSpan start() {
            return new YdszSpan(builder.startSpan());
        }
    }

    // ============================================================================
    // 静态便捷方法
    // ============================================================================

    /**
     * 执行一个有 Span 包裹的操作
     *
     * @param tracer  OTel Tracer
     * @param name    Span 名称
     * @param action  要执行的操作
     * @param <T>     返回类型
     * @return 操作结果
     */
    public static <T> T run(Tracer tracer, String name, java.util.function.Supplier<T> action) {
        YdszSpan span = builder(tracer, name).start();
        try (Scope ignored = span.scope()) {
            T result = action.get();
            span.ok();
            return result;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.error("RUNTIME_ERROR", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 无返回值的便捷方法
     */
    public static void runVoid(Tracer tracer, String name, Runnable action) {
        run(tracer, name, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 批量导出快捷方法：把 Map 转为属性集合
     */
    public static Map<String, Object> attrs() {
        return new HashMap<>();
    }
}
