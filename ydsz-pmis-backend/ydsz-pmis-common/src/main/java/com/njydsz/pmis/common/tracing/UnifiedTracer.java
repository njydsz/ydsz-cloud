package com.njydsz.pmis.common.tracing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一追踪器（P2-1 架构优化）。
 *
 * <p>自动发现所有 {@link TraceSpanRecorder} 实现，按模块名分发追踪数据。
 * 替代各模块各自定义的 TraceService / DefaultAgentTracer / MessageTraceService 等。
 *
 * <p>使用方式：
 * <pre>{@code
 * unifiedTracer.record(TraceSpan.builder()
 *     .traceId(traceId)
 *     .module("agent")
 *     .operationName("agent.execute")
 *     .startedAt(start)
 *     .finishedAt(end)
 *     .status("SUCCESS")
 *     .build());
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
public class UnifiedTracer {

    /** 模块 → Span 记录器 映射 */
    private final Map<String, TraceSpanRecorder> recorders;

    /**
     * 构造器，Spring 自动注入所有 TraceSpanRecorder 实现。
     *
     * @param spanRecorders Span 记录器列表
     */
    public UnifiedTracer(List<TraceSpanRecorder> spanRecorders) {
        this.recorders = spanRecorders == null ? Map.of() :
                spanRecorders.stream()
                        .collect(Collectors.toMap(
                                r -> findModule(r),
                                r -> r,
                                (a, b) -> a));
        log.info("[UnifiedTracer] 已加载 {} 个 Span 记录器: {}", recorders.size(), recorders.keySet());
    }

    /**
     * 记录一个追踪 Span。
     *
     * @param span 追踪 Span
     */
    public void record(TraceSpan span) {
        if (span == null || span.getModule() == null) {
            return;
        }
        TraceSpanRecorder recorder = recorders.get(span.getModule());
        if (recorder == null) {
            log.debug("[UnifiedTracer] 无匹配记录器，降级日志: module={} op={}",
                    span.getModule(), span.getOperationName());
            return;
        }
        try {
            recorder.record(span);
        } catch (Exception e) {
            log.warn("[UnifiedTracer] Span 记录失败(不影响主流程): module={} op={} err={}",
                    span.getModule(), span.getOperationName(), e.getMessage());
        }
    }

    /**
     * 批量记录追踪 Span。
     *
     * @param spans Span 列表
     */
    public void recordBatch(Iterable<TraceSpan> spans) {
        if (spans == null) return;
        spans.forEach(this::record);
    }

    /**
     * 尝试从记录器的 supports 方法推断模块名。
     */
    private static String findModule(TraceSpanRecorder recorder) {
        for (String module : List.of("agent", "message", "cronjob", "literule", "workflow", "project")) {
            if (recorder.supports(module)) {
                return module;
            }
        }
        return "default";
    }
}
