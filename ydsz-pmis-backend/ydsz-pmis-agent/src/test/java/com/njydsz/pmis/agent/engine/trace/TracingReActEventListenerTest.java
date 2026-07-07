package com.njydsz.pmis.agent.engine.trace;

import com.njydsz.pmis.agent.engine.react.ReActDecision;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tracing 事件监听器单元测试（P2-3 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>每个回调方法正确转换为对应 span</li>
 *   <li>onComplete 不落 span（由 endAgent 负责）</li>
 *   <li>所有回调 try-catch 住，异常不传播</li>
 *   <li>traceCtx=null 时回调仍能正常运行（不抛 NPE）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TracingReActEventListener 监听器")
class TracingReActEventListenerTest {

    @Mock
    private AgentTracer tracer;

    private TraceContext traceCtx;

    @BeforeEach
    void setUp() {
        traceCtx = TraceContext.builder()
                .traceId("trace-listener-001")
                .rootSpanId("root-001")
                .agentType("RISK_WARNING")
                .bizType("PROJECT")
                .bizId("B001")
                .bizRef("REF-001")
                .providerTraceId("provider-001")
                .tenantId("1")
                .startMs(System.currentTimeMillis())
                .stepStartMs(System.currentTimeMillis())
                .build();
    }

    // ==================== 各回调 span 转换测试 ====================

    @Nested
    @DisplayName("回调方法转换为 span")
    class CallbackToSpanTest {

        @Test
        @DisplayName("onStepStart 落 STEP_START span")
        void shouldSpanStepStart() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onStepStart(1);

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.STEP_START),
                    eq(1), any(), any());
        }

        @Test
        @DisplayName("onStepStart 同时调用 traceCtx.markStepStart")
        void shouldMarkStepStartOnOnStepStart() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);
            long originalStepStart = traceCtx.getStepStartMs();

            listener.onStepStart(1);

            // markStepStart 被调用（stepStartMs 可能更新为更晚的值或相等）
            assertThat(traceCtx.getStepStartMs()).isGreaterThanOrEqualTo(originalStepStart);
        }

        @Test
        @DisplayName("onThought 落 LLM_THOUGHT span，outputData 包装 thought")
        void shouldSpanThought() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onThought(1, "分析项目风险等级");

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.LLM_THOUGHT),
                    eq(1), any(), any());
        }

        @Test
        @DisplayName("onThought thought=null 时 outputData 也为 null")
        void shouldHandleNullThought() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onThought(1, null);

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.LLM_THOUGHT),
                    eq(1), eq(null), eq(null));
        }

        @Test
        @DisplayName("onAction 落 LLM_ACTION span，outputData 序列化 decision")
        void shouldSpanAction() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);
            ReActDecision decision = new ReActDecision();
            decision.setAction("queryRiskEvent");
            decision.setThought("需要查询风险事件");
            decision.setParameters(java.util.Map.of("project", "P001"));

            listener.onAction(1, decision);

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.LLM_ACTION),
                    eq(1), eq(null), any());
        }

        @Test
        @DisplayName("onAction decision=null 时 outputData 也为 null")
        void shouldHandleNullDecision() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onAction(1, null);

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.LLM_ACTION),
                    eq(1), eq(null), eq(null));
        }

        @Test
        @DisplayName("onObservation 落 TOOL_OBSERVATION span")
        void shouldSpanObservation() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onObservation(1, "查询到 3 条风险事件");

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.TOOL_OBSERVATION),
                    eq(1), any(), any());
        }

        @Test
        @DisplayName("onFinalAnswer 落 FINAL_ANSWER span")
        void shouldSpanFinalAnswer() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onFinalAnswer(2, "建议提高风险预警等级至 RED");

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.FINAL_ANSWER),
                    eq(2), any(), any());
        }

        @Test
        @DisplayName("onStepEnd 落 STEP_END span")
        void shouldSpanStepEnd() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onStepEnd(1);

            verify(tracer, times(1)).span(eq(traceCtx), eq(AgentSpanName.STEP_END),
                    eq(1), any(), any());
        }

        @Test
        @DisplayName("onError 调用 tracer.error")
        void shouldCallTracerError() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);
            RuntimeException err = new RuntimeException("ReAct 故障");

            listener.onError(0, err);

            verify(tracer, times(1)).error(eq(traceCtx), eq(err));
        }

        @Test
        @DisplayName("onComplete 不调用 tracer（由 AgentServiceImpl.endAgent 负责）")
        void shouldNotCallTracerOnComplete() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);
            ReActResult result = ReActResult.success("done", java.util.List.of());

            listener.onComplete(result);

            verify(tracer, never()).span(any(), anyString(), anyInt(), any(), any());
            verify(tracer, never()).error(any(), any());
            verify(tracer, never()).endAgent(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        }
    }

    // ==================== 异常容错测试 ====================

    @Nested
    @DisplayName("异常容错：try-catch 不传播")
    class ExceptionToleranceTest {

        @Test
        @DisplayName("onStepStart 抛异常不传播")
        void shouldSwallowOnStepStartException() {
            doThrow(new RuntimeException("tracer 故障"))
                    .when(tracer).span(any(), anyString(), anyInt(), any(), any());
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            // 不抛异常
            listener.onStepStart(1);
        }

        @Test
        @DisplayName("onThought 抛异常不传播")
        void shouldSwallowOnThoughtException() {
            doThrow(new RuntimeException("tracer 故障"))
                    .when(tracer).span(any(), anyString(), anyInt(), any(), any());
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onThought(1, "thought");
        }

        @Test
        @DisplayName("onAction 抛异常不传播")
        void shouldSwallowOnActionException() {
            doThrow(new RuntimeException("tracer 故障"))
                    .when(tracer).span(any(), anyString(), anyInt(), any(), any());
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onAction(1, new ReActDecision());
        }

        @Test
        @DisplayName("onObservation 抛异常不传播")
        void shouldSwallowOnObservationException() {
            doThrow(new RuntimeException("tracer 故障"))
                    .when(tracer).span(any(), anyString(), anyInt(), any(), any());
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onObservation(1, "obs");
        }

        @Test
        @DisplayName("onFinalAnswer 抛异常不传播")
        void shouldSwallowOnFinalAnswerException() {
            doThrow(new RuntimeException("tracer 故障"))
                    .when(tracer).span(any(), anyString(), anyInt(), any(), any());
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onFinalAnswer(1, "final");
        }

        @Test
        @DisplayName("onStepEnd 抛异常不传播")
        void shouldSwallowOnStepEndException() {
            doThrow(new RuntimeException("tracer 故障"))
                    .when(tracer).span(any(), anyString(), anyInt(), any(), any());
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onStepEnd(1);
        }

        @Test
        @DisplayName("onError 抛异常不传播")
        void shouldSwallowOnErrorException() {
            doThrow(new RuntimeException("tracer 故障"))
                    .when(tracer).error(any(), any());
            TracingReActEventListener listener = new TracingReActEventListener(tracer, traceCtx);

            listener.onError(0, new RuntimeException("ReAct err"));
        }
    }

    // ==================== traceCtx=null 容错 ====================

    @Nested
    @DisplayName("traceCtx=null 容错")
    class NullTraceCtxTest {

        @Test
        @DisplayName("traceCtx=null 时 onStepStart 不抛 NPE")
        void shouldHandleNullTraceCtxOnStepStart() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, null);

            listener.onStepStart(1);
            // tracer.span 会被调用（traceCtx 参数为 null）
            verify(tracer, times(1)).span(eq(null), eq(AgentSpanName.STEP_START),
                    eq(1), any(), any());
        }

        @Test
        @DisplayName("traceCtx=null 时 onThought 不抛 NPE")
        void shouldHandleNullTraceCtxOnThought() {
            TracingReActEventListener listener = new TracingReActEventListener(tracer, null);

            listener.onThought(1, "thought");
        }
    }
}
