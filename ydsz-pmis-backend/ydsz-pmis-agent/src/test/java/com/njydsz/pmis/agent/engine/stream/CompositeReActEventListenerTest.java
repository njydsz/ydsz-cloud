package com.njydsz.pmis.agent.engine.stream;

import com.njydsz.pmis.agent.engine.react.ReActDecision;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 复合 ReAct 事件监听器单元测试（P2-3 落地）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>多 listener 遍历调用：每个回调都转发给所有 listener</li>
 *   <li>单个 listener 抛异常不影响其他 listener</li>
 *   <li>size() / listeners() 辅助方法</li>
 *   <li>空构造 / null 构造的安全性</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CompositeReActEventListener 复合监听器")
class CompositeReActEventListenerTest {

    // ==================== 多 listener 遍历 ====================

    @Nested
    @DisplayName("多 listener 遍历转发")
    class MultiListenerForwardTest {

        @Test
        @DisplayName("onStepStart 转发给所有 listener")
        void shouldForwardOnStepStartToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            ReActEventListener l3 = mock(ReActEventListener.class);
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2, l3);

            composite.onStepStart(1);

            verify(l1, times(1)).onStepStart(1);
            verify(l2, times(1)).onStepStart(1);
            verify(l3, times(1)).onStepStart(1);
        }

        @Test
        @DisplayName("onThought 转发给所有 listener")
        void shouldForwardOnThoughtToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            composite.onThought(1, "thought");

            verify(l1, times(1)).onThought(1, "thought");
            verify(l2, times(1)).onThought(1, "thought");
        }

        @Test
        @DisplayName("onAction 转发给所有 listener")
        void shouldForwardOnActionToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            ReActDecision decision = new ReActDecision();
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            composite.onAction(1, decision);

            verify(l1, times(1)).onAction(1, decision);
            verify(l2, times(1)).onAction(1, decision);
        }

        @Test
        @DisplayName("onObservation 转发给所有 listener")
        void shouldForwardOnObservationToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            composite.onObservation(1, "obs");

            verify(l1, times(1)).onObservation(1, "obs");
            verify(l2, times(1)).onObservation(1, "obs");
        }

        @Test
        @DisplayName("onFinalAnswer 转发给所有 listener")
        void shouldForwardOnFinalAnswerToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            composite.onFinalAnswer(2, "final");

            verify(l1, times(1)).onFinalAnswer(2, "final");
            verify(l2, times(1)).onFinalAnswer(2, "final");
        }

        @Test
        @DisplayName("onStepEnd 转发给所有 listener")
        void shouldForwardOnStepEndToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            composite.onStepEnd(1);

            verify(l1, times(1)).onStepEnd(1);
            verify(l2, times(1)).onStepEnd(1);
        }

        @Test
        @DisplayName("onComplete 转发给所有 listener")
        void shouldForwardOnCompleteToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            ReActResult result = ReActResult.success("done", List.of());
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            composite.onComplete(result);

            verify(l1, times(1)).onComplete(result);
            verify(l2, times(1)).onComplete(result);
        }

        @Test
        @DisplayName("onError 转发给所有 listener")
        void shouldForwardOnErrorToAll() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            RuntimeException err = new RuntimeException("err");
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            composite.onError(0, err);

            verify(l1, times(1)).onError(0, err);
            verify(l2, times(1)).onError(0, err);
        }
    }

    // ==================== 异常容错 ====================

    @Nested
    @DisplayName("异常容错：单个 listener 抛异常不影响其他")
    class ExceptionToleranceTest {

        @Test
        @DisplayName("第一个 listener 抛异常，后续 listener 仍被调用")
        void shouldContinueWhenFirstListenerThrows() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            doThrow(new RuntimeException("l1 故障")).when(l1).onStepStart(anyInt());
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            // 不抛异常
            composite.onStepStart(1);

            verify(l2, times(1)).onStepStart(1);
        }

        @Test
        @DisplayName("中间 listener 抛异常，前后 listener 都被调用")
        void shouldContinueWhenMiddleListenerThrows() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            ReActEventListener l3 = mock(ReActEventListener.class);
            doThrow(new RuntimeException("l2 故障")).when(l2).onThought(anyInt(), anyString());
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2, l3);

            composite.onThought(1, "thought");

            verify(l1, times(1)).onThought(1, "thought");
            verify(l3, times(1)).onThought(1, "thought");
        }

        @Test
        @DisplayName("所有 listener 都抛异常，composite 不传播")
        void shouldNotPropagateWhenAllListenersThrow() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            doThrow(new RuntimeException("l1 故障")).when(l1).onError(anyInt(), any());
            doThrow(new RuntimeException("l2 故障")).when(l2).onError(anyInt(), any());
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            // 不抛异常
            composite.onError(0, new RuntimeException("ReAct err"));
        }
    }

    // ==================== 辅助方法 ====================

    @Nested
    @DisplayName("辅助方法 size / listeners")
    class HelperMethodTest {

        @Test
        @DisplayName("size() 返回 listener 数量")
        void shouldReturnSize() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            assertThat(composite.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("listeners() 返回内部数组副本")
        void shouldReturnListenersCopy() {
            ReActEventListener l1 = mock(ReActEventListener.class);
            ReActEventListener l2 = mock(ReActEventListener.class);
            CompositeReActEventListener composite = new CompositeReActEventListener(l1, l2);

            ReActEventListener[] listeners = composite.listeners();
            assertThat(listeners).hasSize(2);
            assertThat(listeners[0]).isSameAs(l1);
            assertThat(listeners[1]).isSameAs(l2);

            // 修改返回的数组不影响内部状态
            listeners[0] = null;
            assertThat(composite.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("空构造 size()=0")
        void shouldReturnZeroSizeWhenEmpty() {
            CompositeReActEventListener composite = new CompositeReActEventListener();

            assertThat(composite.size()).isZero();
            assertThat(composite.listeners()).isEmpty();
        }

        @Test
        @DisplayName("null 构造视为空数组，size()=0")
        void shouldHandleNullListeners() {
            CompositeReActEventListener composite = new CompositeReActEventListener(null);

            assertThat(composite.size()).isZero();
        }

        @Test
        @DisplayName("空构造时回调方法不抛异常")
        void shouldNotThrowWhenEmptyAndCallbackInvoked() {
            CompositeReActEventListener composite = new CompositeReActEventListener();

            composite.onStepStart(1);
            composite.onThought(1, "t");
            composite.onAction(1, new ReActDecision());
            composite.onObservation(1, "o");
            composite.onFinalAnswer(1, "f");
            composite.onStepEnd(1);
            composite.onComplete(ReActResult.success("ok", List.of()));
            composite.onError(0, new RuntimeException("e"));
            // 不抛异常即可
        }
    }
}
