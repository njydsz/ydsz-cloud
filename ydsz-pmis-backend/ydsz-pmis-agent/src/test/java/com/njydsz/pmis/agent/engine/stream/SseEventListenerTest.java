package com.njydsz.pmis.agent.engine.stream;

import com.njydsz.pmis.agent.engine.react.ReActDecision;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link SseEventListener} 单元测试（P2-1 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>各回调方法对应 SSE 事件类型 + 载荷正确</li>
 *   <li>onComplete 触发 DONE 事件并 complete emitter</li>
 *   <li>onError 触发 ERROR 事件</li>
 *   <li>IOException 时标记客户端断开，后续推送跳过</li>
 *   <li>null 入参的安全处理</li>
 * </ul>
 *
 * @author ydsy-pmis-team
 * @since 1.0.0 (P2-1)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SseEventListener 测试")
class SseEventListenerTest {

    @Mock
    private SseEmitter emitter;

    private SseEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new SseEventListener(emitter);
    }

    @Nested
    @DisplayName("正常推送场景")
    class NormalPushTest {

        @Test
        @DisplayName("onStepStart 推送 STEP_START 事件")
        void shouldPushStepStartEvent() throws Exception {
            listener.onStepStart(1);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onThought 推送 THOUGHT 事件，载荷含 thought 字段")
        void shouldPushThoughtEvent() throws Exception {
            listener.onThought(1, "需要校验 XML");
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onThought thought=null 时载荷为空字符串")
        void shouldHandleNullThought() throws Exception {
            listener.onThought(1, null);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onAction 推送 ACTION 事件，载荷含 action + parameters")
        void shouldPushActionEvent() throws Exception {
            ReActDecision decision = new ReActDecision();
            decision.setAction("bpmn_validate");
            decision.setParameters(Map.of("bpmnXml", "<xml/>"));

            listener.onAction(1, decision);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onObservation 推送 OBSERVATION 事件")
        void shouldPushObservationEvent() throws Exception {
            listener.onObservation(1, "校验通过");
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onFinalAnswer 推送 FINAL_ANSWER 事件")
        void shouldPushFinalAnswerEvent() throws Exception {
            listener.onFinalAnswer(2, "<bpmn:definitions/>");
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onStepEnd 推送 STEP_END 事件")
        void shouldPushStepEndEvent() throws Exception {
            listener.onStepEnd(1);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onComplete(success) 推送 DONE 事件，并 complete emitter")
        void shouldPushDoneEventOnSuccess() throws Exception {
            ReActResult result = ReActResult.success("ok", List.of());
            listener.onComplete(result);
            // 推送 DONE + complete 调用
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        @DisplayName("onComplete(failure) 也推送 DONE 事件（success=false）")
        void shouldPushDoneEventOnFailure() throws Exception {
            ReActResult result = ReActResult.failure("LLM 失败", List.of());
            listener.onComplete(result);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter).complete();
        }

        @Test
        @DisplayName("onError 推送 ERROR 事件")
        void shouldPushErrorEvent() throws Exception {
            Throwable err = new RuntimeException("网络异常");
            listener.onError(0, err);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("onError error=null 时 reason 为 'unknown'")
        void shouldHandleNullError() throws Exception {
            listener.onError(0, null);
            verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }
    }

    @Nested
    @DisplayName("客户端断开场景")
    class ClientDisconnectTest {

        @Test
        @DisplayName("send 抛 IOException 后标记断开，后续推送跳过")
        void shouldMarkDisconnectedOnIOException() throws Exception {
            // 第一次 send 抛 IOException
            doThrow(new IOException("client closed"))
                    .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            listener.onStepStart(1);
            assertThat(listener.isClientDisconnected()).isTrue();

            // 后续推送应该跳过（不再调用 send）
            listener.onThought(1, "test");
            listener.onFinalAnswer(1, "answer");

            // send 只被调用一次（第一次抛异常）
            verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        }

        @Test
        @DisplayName("emitter=null 时所有推送跳过，不抛异常")
        void shouldHandleNullEmitter() {
            SseEventListener nullListener = new SseEventListener(null);
            // 不应抛异常
            nullListener.onStepStart(1);
            nullListener.onThought(1, "test");
            nullListener.onComplete(ReActResult.success("ok", List.of()));
            assertThat(nullListener.isClientDisconnected()).isFalse();
        }

        @Test
        @DisplayName("断开后 onComplete 仍调用 emitter.complete()")
        void shouldCompleteEmitterEvenIfDisconnected() throws Exception {
            doThrow(new IOException("closed"))
                    .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

            listener.onStepStart(1); // 标记断开
            listener.onComplete(ReActResult.success("ok", List.of()));

            // complete 仍被调用
            verify(emitter).complete();
        }
    }

    @Nested
    @DisplayName("完整 ReAct 流程推送")
    class FullReActFlowTest {

        @Test
        @DisplayName("完整的单步成功流程推送 6 个事件（STEP_START + THOUGHT + ACTION + FINAL_ANSWER + STEP_END + DONE）")
        void shouldPushAllEventsForSingleStepSuccess() throws Exception {
            // 模拟完整的单步 ReAct 流程
            listener.onStepStart(1);
            listener.onThought(1, "已知答案");
            ReActDecision decision = new ReActDecision();
            decision.setThought("已知答案");
            decision.setAction("final_answer");
            decision.setFinalAnswer("最终答案");
            listener.onAction(1, decision);
            listener.onFinalAnswer(1, "最终答案");
            listener.onStepEnd(1);
            listener.onComplete(ReActResult.success("最终答案", List.of()));

            // 6 个事件 + 1 个 complete
            verify(emitter, atLeast(6)).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter, times(1)).complete();
        }

        @Test
        @DisplayName("完整的多步成功流程（2 步）推送至少 9 个事件")
        void shouldPushAllEventsForMultiStepSuccess() throws Exception {
            // 第 1 步：调用工具
            listener.onStepStart(1);
            listener.onThought(1, "调用工具");
            ReActDecision d1 = new ReActDecision();
            d1.setAction("query");
            d1.setParameters(Map.of());
            listener.onAction(1, d1);
            listener.onObservation(1, "结果");
            listener.onStepEnd(1);

            // 第 2 步：最终答案
            listener.onStepStart(2);
            listener.onThought(2, "已得到答案");
            ReActDecision d2 = new ReActDecision();
            d2.setAction("final_answer");
            d2.setFinalAnswer("最终答案");
            listener.onAction(2, d2);
            listener.onFinalAnswer(2, "最终答案");
            listener.onStepEnd(2);
            listener.onComplete(ReActResult.success("最终答案", List.of()));

            // 2 步 × 5 事件 + DONE = 11 个事件
            verify(emitter, atLeast(11)).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter, times(1)).complete();
        }

        @Test
        @DisplayName("失败流程推送 ERROR + DONE 事件")
        void shouldPushErrorAndDoneEventsForFailure() throws Exception {
            ReActResult failure = ReActResult.failure("LLM 异常", List.of());
            listener.onComplete(failure);

            // 推送 DONE 事件（success=false）
            verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
            verify(emitter, times(1)).complete();
        }
    }
}
