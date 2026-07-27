package com.njydsz.common.sentry.tracing.otel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import lombok.extern.slf4j.Slf4j;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
/**
 * YDSZ OpenTelemetry 全局访问点 + 上下文传播工具
 *
 * <p>提供：
 * <ul>
 *   <li>统一的 Tracer 获取（支持命名空间隔离）</li>
 *   <li>W3C TraceContext + B3 + Baggage 多协议上下文传播</li>
 *   <li>HTTP / Feign / 自定义载体的 Propagation 工具</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>{@code
 * Tracer tracer = YdszOpenTelemetry.tracer("ydsz-order");
 *
 * // 注入到下游请求头
 * YdszOpenTelemetry.inject(Map.of(), (carrier, key, value) -> carrier.put(key, value));
 *
 * // 从上游请求头提取
 * YdszOpenTelemetry.extract(headers, Map::get);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class YdszOpenTelemetry {

    /** 默认 Tracer 名称 */
    public static final String DEFAULT_INSTRUMENTATION_NAME = "ydsz";

    /** 标准 HTTP 头名称 */
    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_TRACESTATE = "tracestate";
    public static final String HEADER_B3_TRACEID = "X-B3-TraceId";
    public static final String HEADER_B3_SPANID = "X-B3-SpanId";
    public static final String HEADER_B3_SAMPLED = "X-B3-Sampled";
    public static final String HEADER_B3_PARENTSPANID = "X-B3-ParentSpanId";
    public static final String HEADER_TRACEPARENT_BAGGAGE = "baggage";

    private static final Map<String, Tracer> TRACER_CACHE = new ConcurrentHashMap<>();

    private YdszOpenTelemetry() {
        throw new UnsupportedOperationException("YdszOpenTelemetry is a utility class");
    }

    // ============================================================================
    // Tracer 获取
    // ============================================================================

    /**
     * 获取全局 OpenTelemetry 实例
     */
    public static OpenTelemetry openTelemetry() {
        OpenTelemetry otel = GlobalOpenTelemetry.get();
        if (otel == null) {
            // SDK 未初始化时，OTel 默认返回 NoOp 实现，不抛异常
            return OpenTelemetry.noop();
        }
        return otel;
    }

    /**
     * 获取指定命名空间的 Tracer（自动缓存）
     *
     * @param instrumentationName 名称（一般填服务/模块名）
     */
    public static Tracer tracer(String instrumentationName) {
        if (instrumentationName == null || instrumentationName.isEmpty()) {
            instrumentationName = DEFAULT_INSTRUMENTATION_NAME;
        }
        return TRACER_CACHE.computeIfAbsent(instrumentationName,
                name -> openTelemetry().getTracer(name));
    }

    /**
     * 获取默认 Tracer
     */
    public static Tracer tracer() {
        return tracer(DEFAULT_INSTRUMENTATION_NAME);
    }

    /**
     * 检测 OpenTelemetry SDK 是否可用
     */
    public static boolean isAvailable() {
        return GlobalOpenTelemetry.get() != null;
    }

    // ============================================================================
    // 上下文传播
    // ============================================================================

    /**
     * 将当前 Span 上下文注入到载体
     *
     * @param carrier 目标载体（如 Map / HttpHeaders）
     * @param setter  载体写入回调
     * @param <C>     载体类型
     */
    public static <C> void inject(C carrier, TextMapSetter<C> setter) {
        try {
            TextMapPropagator propagator = openTelemetry().getPropagators().getTextMapPropagator();
            Context context = io.opentelemetry.context.Context.current();
            propagator.inject(context, carrier, setter);
        } catch (Exception e) {
            log.debug("[YdszOpenTelemetry] inject 失败: {}", e.getMessage());
        }
    }

    /**
     * 从载体中提取 Span 上下文
     *
     * @param carrier 源载体
     * @param getter  载体读取回调
     * @param <C>     载体类型
     * @return 提取出的 OTel Context
     */
    public static <C> Context extract(C carrier, TextMapGetter<C> getter) {
        try {
            TextMapPropagator propagator = openTelemetry().getPropagators().getTextMapPropagator();
            return propagator.extract(io.opentelemetry.context.Context.current(), carrier, getter);
        } catch (Exception e) {
            log.debug("[YdszOpenTelemetry] extract 失败: {}", e.getMessage());
            return io.opentelemetry.context.Context.current();
        }
    }

    /**
     * 在指定 Context 中执行操作
     */
    public static <T> T withContext(Context context, Supplier<T> action) {
        try (Scope ignored = context.makeCurrent()) {
            return action.get();
        }
    }

    /**
     * 在指定 Context 中执行无返回值操作
     */
    public static void withContextVoid(Context context, Runnable action) {
        withContext(context, () -> {
            action.run();
            return null;
        });
    }

    // ============================================================================
    // 通用 TextMapSetter / Getter 适配器
    // ============================================================================

    /**
     * 通用 Map Setter
     */
    public static final TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> {
                if (carrier != null && key != null) {
                    carrier.put(key, value);
                }
            };

    /**
     * 通用 Map Getter
     */
    public static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier == null ? java.util.Collections.emptyList() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };
}
