package com.njydsz.common.core.constant;

/**
 * 链路追踪常量定义。
 *
 * <p>统一管理 TraceId 相关的 HTTP 请求头名称和 MDC key，
 * 全项目应引用此类中的常量，避免各模块各自硬编码。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TraceConstants {

    private TraceConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * TraceId HTTP 请求头名称。
     *
     * <p>用于全链路请求追踪，贯穿网关、服务间调用、日志记录等场景。
     * 若请求未携带，由服务端自动生成并写入响应头。</p>
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * TraceId 在 SLF4J MDC 中的 key 名称。
     *
     * <p>日志框架通过此 key 从 MDC 中提取 traceId 注入日志输出格式，
     * 实现日志与链路追踪的关联。</p>
     */
    public static final String MDC_TRACE_ID_KEY = "traceId";
}
