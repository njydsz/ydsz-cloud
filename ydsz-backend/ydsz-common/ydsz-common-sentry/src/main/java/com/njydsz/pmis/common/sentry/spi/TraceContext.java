package com.njydsz.common.sentry.spi;

/**
 * 追踪上下文 SPI
 *
 * <p>统一追踪上下文抽象，底层可切换 SkyWalking / 自实现 TraceId。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
public interface TraceContext {

    /**
     * 获取当前 TraceId
     */
    String getTraceId();

    /**
     * 获取当前 SpanId
     */
    String getSpanId();

    /**
     * 获取当前 SegmentId（SkyWalking 专用）
     */
    default String getSegmentId() {
        return null;
    }

    /**
     * 判断是否在追踪链路中
     */
    boolean isTracing();

    /**
     * 注入自定义标签到当前 Span
     *
     * @param key   标签键
     * @param value 标签值
     */
    void tag(String key, String value);

    /**
     * 获取追踪系统名称
     */
    String getTracerName();
}
