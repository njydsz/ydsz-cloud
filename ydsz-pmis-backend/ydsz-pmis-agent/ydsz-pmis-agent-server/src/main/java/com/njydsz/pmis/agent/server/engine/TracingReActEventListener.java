package com.njydsz.pmis.agent.server.engine.trace;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.agent.server.engine.react.ReActDecision;
import com.njydsz.pmis.agent.server.engine.react.ReActResult;
import com.njydsz.pmis.agent.server.engine.stream.ReActEventListener;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracing 事件监听器（P2-3 落地）。
 *
 * <p>实现 {@link ReActEventListener}，将 ReAct 循环的关键节点转换为 span 落库。
 * 通过复合 {@link AgentTracer} 实现零侵入接入，不修改 ReActLoop 核心代码。
 *
 * <p>典型用法：在 AgentServiceImpl.executeStream 中作为复合 listener 传入：
 * <pre>
 * TraceContext traceCtx = tracer.startAgent(ctx);
 * ReActEventListener tracingListener = new TracingReActEventListener(tracer, traceCtx);
 * // 执行 ReAct...
 * tracer.endAgent(traceCtx, JSON.toJSONString(result), result.isSuccess());
 * </pre>
 *
 * <p>所有回调都 try-catch 住，避免监听器异常中断主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@Slf4j
public class TracingReActEventListener implements ReActEventListener {

    private final AgentTracer tracer;
    private final TraceContext traceCtx;

    public TracingReActEventListener(AgentTracer tracer, TraceContext traceCtx) {
        this.tracer = tracer;
        this.traceCtx = traceCtx;
    }

    @Override
    public void onStepStart(int stepIndex) {
        try {
            if (traceCtx != null) {
                traceCtx.markStepStart();
            }
            tracer.span(traceCtx, AgentSpanName.STEP_START, stepIndex, null, null);
        } catch (Exception e) {
            log.warn("[TracingListener] onStepStart 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    public void onThought(int stepIndex, String thought) {
        try {
            tracer.span(traceCtx, AgentSpanName.LLM_THOUGHT, stepIndex,
                    null, safeJson("thought", thought));
        } catch (Exception e) {
            log.warn("[TracingListener] onThought 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    public void onAction(int stepIndex, ReActDecision decision) {
        try {
            tracer.span(traceCtx, AgentSpanName.LLM_ACTION, stepIndex,
                    null, decision == null ? null : JSON.toJSONString(decision));
        } catch (Exception e) {
            log.warn("[TracingListener] onAction 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    public void onObservation(int stepIndex, String observation) {
        try {
            tracer.span(traceCtx, AgentSpanName.TOOL_OBSERVATION, stepIndex,
                    null, safeJson("observation", observation));
        } catch (Exception e) {
            log.warn("[TracingListener] onObservation 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    public void onFinalAnswer(int stepIndex, String finalAnswer) {
        try {
            tracer.span(traceCtx, AgentSpanName.FINAL_ANSWER, stepIndex,
                    null, safeJson("finalAnswer", finalAnswer));
        } catch (Exception e) {
            log.warn("[TracingListener] onFinalAnswer 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    public void onStepEnd(int stepIndex) {
        try {
            tracer.span(traceCtx, AgentSpanName.STEP_END, stepIndex, null, null);
        } catch (Exception e) {
            log.warn("[TracingListener] onStepEnd 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    @Override
    public void onComplete(ReActResult result) {
        // 由 AgentServiceImpl.endAgent 负责落 AGENT_END，避免重复
    }

    @Override
    public void onError(int stepIndex, Throwable error) {
        try {
            tracer.error(traceCtx, error);
        } catch (Exception e) {
            log.warn("[TracingListener] onError 异常: step={} err={}",
                    stepIndex, e.getMessage());
        }
    }

    /** 安全 JSON 序列化（包装为 key-value 形式） */
    private static String safeJson(String key, String value) {
        if (value == null) {
            return null;
        }
        try {
            return JSON.toJSONString(java.util.Map.of(key, value));
        } catch (Exception e) {
            return "{\"" + key + "\":\"" + value + "\"}";
        }
    }
}
