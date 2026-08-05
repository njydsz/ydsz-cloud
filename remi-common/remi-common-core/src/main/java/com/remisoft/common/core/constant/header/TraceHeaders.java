package com.remisoft.common.core.constant.header;

/**
 * 链路追踪相关 HTTP 请求头常量
 *
 * <p>定义全链路追踪、分布式追踪系统对接所需的 header，
 * * 包括 W3C Trace Context 标准协议和内部 MDC 键名。
 *
 * <p>对应模块：remi-common-base（TraceFilter）、remi-common-feign（透传）
 *
 * @author remi-team
 * @since 1.8.0
 */
public final class TraceHeaders {

    private TraceHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 请求追踪 ID HTTP 头
     *
     * <p>用于全链路请求追踪，贯穿网关、服务间调用、日志记录等场景。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * TraceId 在 SLF4J MDC 中的 key 名称
     *
     * <p>日志框架通过此 key 从 MDC 中提取 traceId 注入日志输出格式。
     */
    public static final String MDC_TRACE_ID_KEY = "traceId";

    /**
     * RequestId 在 SLF4J MDC 中的 key 名称
     *
     * <p>日志框架通过此 key 从 MDC 中提取 requestId 注入日志输出格式。
     * requestId 用于标识单次入口请求，区别于贯通多个服务的 traceId。
     */
    public static final String MDC_REQUEST_ID_KEY = "requestId";

    /**
     * W3C Trace Context 标准的 traceparent header 名称
     *
     * <p>格式：{@code 00-{traceId}-{spanId}-01}，用于对接 SkyWalking/Jaeger/Zipkin
     * 等主流分布式链路追踪系统。
     *
     * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
     */
    public static final String W3C_TRACEPARENT = "traceparent";

    /**
     * W3C Trace Context 标准的 tracestate header 名称
     *
     * <p>用于传递供应商特定的追踪上下文信息。
     *
     * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
     */
    public static final String W3C_TRACESTATE = "tracestate";

    /**
     * W3C traceparent 的版本字段（固定为 00）
     */
    public static final String W3C_VERSION = "00";

    /**
     * W3C traced Flags 的采样位（01 = 已采样）
     */
    public static final String W3C_FLAGS_SAMPLED = "01";

    /**
     * W3C traced Flags 的采样位（00 = 未采样）
     */
    public static final String W3C_FLAGS_NOT_SAMPLED = "00";
}
