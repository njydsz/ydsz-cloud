package com.njydsz.pmis.common.feign.trace;

import com.njydsz.pmis.common.util.id.TracerUtils;
import com.njydsz.pmis.common.util.string.StringUtils;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 链路追踪请求拦截器。
 *
 * <p>自动为 Feign 请求注入链路追踪相关请求头，实现微服务调用链追踪。
 *
 * <p><b>注入的请求头：</b>
 * <ul>
 *   <li>{@code X-Trace-Id} - 追踪唯一标识</li>
 *   <li>{@code X-Span-Id} - Span 唯一标识</li>
 *   <li>{@code X-Parent-Span-Id} - 父 Span 标识</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * 1. 通过 @EnableYdszFeign 启用时自动生效
 * 2. 配合 FeignTraceHandler 实现完整的追踪能力
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see FeignTraceHandler
 */
public class TraceRequestInterceptor implements RequestInterceptor {

    /** 追踪 ID 请求头名称 */
    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    /** Span ID 请求头名称 */
    private static final String HEADER_SPAN_ID = "X-Span-Id";
    /** 父 Span ID 请求头名称 */
    private static final String HEADER_PARENT_SPAN_ID = "X-Parent-Span-Id";

    /** 链路追踪处理器 */
    private final FeignTraceHandler traceHandler;

    /**
     * 使用默认追踪处理器构造拦截器。
     */
    public TraceRequestInterceptor() {
        this(new DefaultTraceHandler());
    }

    public TraceRequestInterceptor(FeignTraceHandler traceHandler) {
        this.traceHandler = traceHandler != null ? traceHandler : new DefaultTraceHandler();
    }

    /**
     * 为 Feign 请求注入链路追踪相关请求头。
     *
     * <p>自动解析或生成 traceId 和 spanId，并注入到请求头中，
     * 同时通过 {@link FeignTraceHandler} 记录调用开始事件。
     *
     * @param requestTemplate Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        String traceId = resolveTraceId(requestTemplate);
        String spanId = TracerUtils.generateSpanId();
        String parentSpanId = resolveParentSpanId(requestTemplate);

        if (StringUtils.isEmpty(traceId)) {
            traceId = TracerUtils.generateTraceId();
        }

        requestTemplate.header(HEADER_TRACE_ID, traceId);
        requestTemplate.header(HEADER_SPAN_ID, spanId);

        if (StringUtils.isNotEmpty(parentSpanId)) {
            requestTemplate.header(HEADER_PARENT_SPAN_ID, parentSpanId);
        }

        if (traceHandler != null && traceHandler.isEnabled()) {
            FeignTraceHandler.TraceContext context = new FeignTraceHandler.TraceContext();
            context.setTraceId(traceId);
            context.setSpanId(spanId);
            context.setParentSpanId(parentSpanId);
            context.setUrl(requestTemplate.url());
            context.setHttpMethod(requestTemplate.method());
            traceHandler.onRequestStart(context);
        }
    }

    /**
     * 解析追踪 ID，优先从请求头获取，其次从追踪处理器获取当前上下文的 traceId。
     *
     * @param requestTemplate Feign 请求模板
     * @return 追踪 ID，无法获取时返回 null
     */
    private String resolveTraceId(RequestTemplate requestTemplate) {
        String traceId = requestTemplate.headers().get(HEADER_TRACE_ID) != null
                ? requestTemplate.headers().get(HEADER_TRACE_ID).iterator().next()
                : null;
        if (StringUtils.isNotEmpty(traceId)) {
            return traceId;
        }
        if (traceHandler != null) {
            return traceHandler.getCurrentTraceId();
        }
        return null;
    }

    /**
     * 解析父 Span ID，优先从请求头获取当前 Span ID 作为父 Span ID，其次从追踪处理器获取。
     *
     * @param requestTemplate Feign 请求模板
     * @return 父 Span ID，无法获取时返回 null
     */
    private String resolveParentSpanId(RequestTemplate requestTemplate) {
        String parentSpanId = requestTemplate.headers().get(HEADER_SPAN_ID) != null
                ? requestTemplate.headers().get(HEADER_SPAN_ID).iterator().next()
                : null;
        if (StringUtils.isNotEmpty(parentSpanId)) {
            return parentSpanId;
        }
        if (traceHandler != null) {
            return traceHandler.getCurrentSpanId();
        }
        return null;
    }

    /**
     * 默认追踪处理器。
     * <p>
     * 提供基础的链路追踪能力，生成 TraceId 和 SpanId。
     */
    private static class DefaultTraceHandler implements FeignTraceHandler {

        @Override
        public String getName() {
            return "default";
        }

        @Override
        public String getCurrentTraceId() {
            return TracerUtils.getTraceId();
        }

        @Override
        public String getCurrentSpanId() {
            return TracerUtils.getSpanId();
        }
    }
}
