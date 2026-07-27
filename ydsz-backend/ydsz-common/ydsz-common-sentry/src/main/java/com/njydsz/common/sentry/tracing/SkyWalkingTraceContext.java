package com.njydsz.common.sentry.tracing;

import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;

import com.njydsz.common.sentry.spi.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * SkyWalking 追踪上下文
 *
 * <p>基于 SkyWalking apm-toolkit-trace 实现，当 SkyWalking agent 存在时自动接入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class SkyWalkingTraceContext implements TraceContext {

    /**
     * 构造函数，初始化 SkyWalking 追踪上下文
     */
    public SkyWalkingTraceContext() {
        log.info("[Sentry] SkyWalkingTraceContext 初始化完成");
    }

    /**
     * 获取当前 TraceId
     *
     * @return TraceId，若 SkyWalking 不可用则返回 null
     */
    @Override
    public String getTraceId() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前 SpanId
     *
     * @return SpanId，若 SkyWalking 不可用则返回 null
     */
    @Override
    public String getSpanId() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return String.valueOf(org.apache.skywalking.apm.toolkit.trace.TraceContext.spanId());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前 SegmentId（SkyWalking 专用概念）
     *
     * @return SegmentId，若 SkyWalking 不可用则返回 null
     */
    @Override
    public String getSegmentId() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return org.apache.skywalking.apm.toolkit.trace.TraceContext.segmentId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断是否在 SkyWalking 追踪链路中
     *
     * @return 是否在追踪链路中
     */
    @Override
    public boolean isTracing() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 注入自定义标签到当前 Span
     *
     * @param key   标签键
     * @param value 标签值
     */
    @Override
    public void tag(String key, String value) {
        try {
            ActiveSpan.tag(key, value);
        } catch (Exception e) {
            log.debug("[Sentry] SkyWalking tag 注入失败: key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 获取追踪系统名称
     *
     * @return 固定返回 "skywalking"
     */
    @Override
    public String getTracerName() {
        return "skywalking";
    }
}
