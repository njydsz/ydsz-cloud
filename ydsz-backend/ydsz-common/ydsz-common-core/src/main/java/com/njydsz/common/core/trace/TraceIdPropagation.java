package com.njydsz.common.core.trace;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;

import com.njydsz.common.core.constant.HeaderConstants;

/**
 * TraceId 传播工具类（纯 JDK 实现，无框架依赖）。
 *
 * <p>为上层各 HTTP 客户端（RestTemplate / WebClient / OkHttp 等）提供统一的
 * TraceId 请求头生成能力，实现服务间调用的链路追踪贯穿。
 * 本类不依赖任何 HTTP 框架，上层客户端拦截器只需调用
 * {@link #traceHeaders()} 获取请求头并注入即可。</p>
 *
 * <p>同时提供符合 W3C Trace Context 标准的 {@code traceparent} header 支持。</p>
 *
 * <p><b>使用示例（RestTemplate 拦截器）：</b></p>
 * <pre>{@code
 * public class TraceIdClientInterceptor implements ClientHttpRequestInterceptor {
 *     @Override
 *     public ClientHttpResponse intercept(HttpRequest request, byte[] body,
 *             ClientHttpRequestExecution execution) throws IOException {
 *         TraceIdPropagation.traceHeaders()
 *                 .forEach(request.getHeaders()::set);
 *         return execution.execute(request, body);
 *     }
 * }
 * }</pre>
 *
 * <p><b>获取优先级：</b>
 * <ol>
 *   <li>当前线程 MDC 中的 traceId（由 {@code TraceFilter} 等入口过滤器写入）</li>
 *   <li>无则返回空 Map（由上层决定是否调用 {@link TraceIdGenerator#generate()} 兜底）</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see HeaderConstants
 * @see TraceIdGenerator
 */
public final class TraceIdPropagation {

    private TraceIdPropagation() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成包含 {@code X-Trace-Id} 和 W3C {@code traceparent} 的完整传播请求头。
     *
     * <p>同时注入两种 header，兼容老版 {@code X-Trace-Id} 透传和
     * 新版 W3C Trace Context 标准（如 SkyWalking / Jaeger）。</p>
     *
     * @return 请求头 Map（不可变，包含 X-Trace-Id 和 traceparent）
     * @since 1.5.0
     */
    public static Map<String, String> traceHeaders() {
        String traceId = currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            return Collections.emptyMap();
        }
        String spanId = TraceIdGenerator.generateSpanId();
        Map<String, String> headers = new HashMap<>(2);
        headers.put(HeaderConstants.TRACE_ID_HEADER, traceId);
        headers.put(HeaderConstants.W3C_TRACEPARENT, TraceIdGenerator.traceparentHeader(traceId, spanId));
        return Collections.unmodifiableMap(headers);
    }

    /**
     * 生成包含 {@code X-Trace-Id} 和 W3C {@code traceparent} 的完整传播请求头（缺失时自动生成）。
     *
     * @return 请求头 Map（不可变，包含 X-Trace-Id 和 traceparent）
     * @since 1.5.0
     */
    public static Map<String, String> traceHeadersOrCreate() {
        String traceId = currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generateTraceId();
        }
        String spanId = TraceIdGenerator.generateSpanId();
        Map<String, String> headers = new HashMap<>(2);
        headers.put(HeaderConstants.TRACE_ID_HEADER, traceId);
        headers.put(HeaderConstants.W3C_TRACEPARENT, TraceIdGenerator.traceparentHeader(traceId, spanId));
        return Collections.unmodifiableMap(headers);
    }

    /**
     * 获取当前线程的 traceId。
     *
     * @return 当前 MDC 中的 traceId；不存在时返回 null
     */
    public static String currentTraceId() {
        return MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
    }

    /**
     * 获取当前线程的 traceId（缺失时自动生成并写入 MDC）。
     *
     * @return 当前 traceId（保证非空）
     */
    public static String currentTraceIdOrCreate() {
        String traceId = currentTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIdGenerator.generateTraceId();
            MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, traceId);
        }
        return traceId;
    }
}
