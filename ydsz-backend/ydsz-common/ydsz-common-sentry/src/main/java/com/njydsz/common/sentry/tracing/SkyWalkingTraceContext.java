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
 * @since 1.5.0
 */
@Slf4j
public class SkyWalkingTraceContext implements TraceContext {

    public SkyWalkingTraceContext() {
        log.info("[Sentry] SkyWalkingTraceContext 初始化完成");
    }

    @Override
    public String getTraceId() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getSpanId() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return String.valueOf(org.apache.skywalking.apm.toolkit.trace.TraceContext.spanId());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public String getSegmentId() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return org.apache.skywalking.apm.toolkit.trace.TraceContext.segmentId();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isTracing() {
        try {
            // FQN-OK: name conflict with com.njydsz.common.sentry.spi.TraceContext
            return org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void tag(String key, String value) {
        try {
            ActiveSpan.tag(key, value);
        } catch (Exception e) {
            log.debug("[Sentry] SkyWalking tag 注入失败: key={}, err={}", key, e.getMessage());
        }
    }

    @Override
    public String getTracerName() {
        return "skywalking";
    }
}
