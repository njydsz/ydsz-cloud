package com.njydsz.common.feign.trace;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * SkyWalking 链路追踪处理器。
 *
 * <p>当 SkyWalking agent 在 classpath 中时，通过反射调用 SkyWalking API
 * 获取当前上下文的 traceId 和 spanId，实现与 SkyWalking 的无缝集成。
 *
 * <p>同时支持 W3C TraceContext 标准（{@code traceparent} 头），
 * 当 SkyWalking 不可用时降级为自定义 {@code X-Trace-Id} 头。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SkyWalkingTraceHandler implements FeignTraceHandler {

    private static final Logger log = LoggerFactory.getLogger(SkyWalkingTraceHandler.class);

    /** SkyWalking ContextManager 类名（字符串引用，避免硬依赖） */
    private static final String SW_CONTEXT_MANAGER = "org.apache.skywalking.apm.agent.core.context.ContextManager";

    private final MethodHandle getTraceIdMethod;
    private final MethodHandle getGlobalTraceIdMethod;
    private final boolean available;

    /**
     * 构造 SkyWalking 追踪处理器。
     *
     * <p>通过反射探测 SkyWalking 是否可用，不可用时降级为 TracerUtils。
     */
    public SkyWalkingTraceHandler() {
        MethodHandle traceIdMH = null;
        MethodHandle globalTraceIdMH = null;
        boolean swAvailable = false;

        try {
            Class<?> contextManagerClass = Class.forName(SW_CONTEXT_MANAGER);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            traceIdMH = lookup.findStatic(contextManagerClass, "getGlobalTraceId",
                    MethodType.methodType(String.class));
            globalTraceIdMH = lookup.findStatic(contextManagerClass, "getSegmentId",
                    MethodType.methodType(String.class));
            swAvailable = true;
            log.info("[SkyWalkingTraceHandler] SkyWalking agent 检测到，链路追踪将使用 SkyWalking");
        } catch (Throwable e) {
            log.debug("[SkyWalkingTraceHandler] SkyWalking agent 不可用，降级为 TracerUtils");
        }

        this.getTraceIdMethod = traceIdMH;
        this.getGlobalTraceIdMethod = globalTraceIdMH;
        this.available = swAvailable;
    }

    /**
     * 获取追踪器名称。
     *
     * @return 追踪器名称（"skywalking"）
     */
    @Override
    public String getName() {
        return "skywalking";
    }

    /**
     * 判断 SkyWalking agent 是否可用。
     *
     * @return true=SkyWalking agent 已在 classpath 中且初始化成功
     */
    @Override
    public boolean isEnabled() {
        return available;
    }

    /**
     * 获取当前追踪上下文中的 traceId。
     *
     * <p>优先从 SkyWalking ContextManager 获取，不可用时降级为 TracerUtils。
     *
     * @return 追踪 ID
     */
    @Override
    public String getCurrentTraceId() {
        if (available && getTraceIdMethod != null) {
            try {
                String traceId = (String) getTraceIdMethod.invoke();
                if (StringUtils.isNotEmpty(traceId)) {
                    return traceId;
                }
            } catch (Throwable e) {
                log.debug("获取 SkyWalking traceId 失败", e);
            }
        }
        return TracerUtils.getTraceId();
    }

    /**
     * 获取当前追踪上下文中的 spanId。
     *
     * <p>优先从 SkyWalking ContextManager 获取，不可用时降级为 TracerUtils。
     *
     * @return Span ID
     */
    @Override
    public String getCurrentSpanId() {
        if (available && getGlobalTraceIdMethod != null) {
            try {
                String spanId = (String) getGlobalTraceIdMethod.invoke();
                if (StringUtils.isNotEmpty(spanId)) {
                    return spanId;
                }
            } catch (Throwable e) {
                log.debug("获取 SkyWalking spanId 失败", e);
            }
        }
        return TracerUtils.getSpanId();
    }

    @Override
    public void onRequestStart(TraceContext context) {
        context.setStartTime(System.currentTimeMillis());
        if (log.isDebugEnabled()) {
            log.debug("[SkyWalkingTraceHandler] Feign 请求开始, traceId={}, spanId={}, url={}",
                    context.getTraceId(), context.getSpanId(), context.getUrl());
        }
    }

    @Override
    public void onRequestSuccess(TraceContext context) {
        context.setEndTime(System.currentTimeMillis());
        if (log.isDebugEnabled()) {
            log.debug("[SkyWalkingTraceHandler] Feign 请求成功, traceId={}, duration={}ms",
                    context.getTraceId(), context.getElapsedTime());
        }
    }

    @Override
    public void onRequestFailure(TraceContext context, Throwable throwable) {
        context.setEndTime(System.currentTimeMillis());
        log.warn("[SkyWalkingTraceHandler] Feign 请求失败, traceId={}, duration={}ms, error={}",
                context.getTraceId(), context.getElapsedTime(), throwable.getMessage());
    }
}
