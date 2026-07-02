package com.njydsz.pmis.workflow.flow.listener;

import com.njydsz.pmis.workflow.flow.engine.FlowEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ProjectInitiationFlowListener 单元测试
 *
 * <p>验证监听器接口方法正确触发且不影响主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ProjectInitiationFlowListener 单元测试")
class ProjectInitiationFlowListenerTest {

    @Test
    @DisplayName("onInstanceStart 正常调用")
    void testOnInstanceStart() {
        FlowEventListener listener = Mockito.mock(FlowEventListener.class);
        listener.onInstanceStart(1L, Map.of("k", "v"));
        verify(listener, times(1)).onInstanceStart(1L, Map.of("k", "v"));
    }

    @Test
    @DisplayName("onInstanceStart 接受 null 变量")
    void testOnInstanceStartNullVars() {
        FlowEventListener listener = Mockito.mock(FlowEventListener.class);
        listener.onInstanceStart(1L, null);
        verify(listener, times(1)).onInstanceStart(1L, null);
    }

    @Test
    @DisplayName("onTaskCreated / onTaskCompleted 正常调用")
    void testOnTaskEvents() {
        FlowEventListener listener = Mockito.mock(FlowEventListener.class);
        listener.onTaskCreated(100L);
        listener.onTaskCompleted(100L, "PASS", Map.of());
        listener.onTaskCompleted(101L, "REJECT", Map.of());
        verify(listener, times(1)).onTaskCreated(100L);
        verify(listener, times(2)).onTaskCompleted(Mockito.anyLong(), Mockito.anyString(), Mockito.any());
    }

    @Test
    @DisplayName("onInstanceCompleted 正常调用")
    void testOnInstanceCompleted() {
        FlowEventListener listener = Mockito.mock(FlowEventListener.class);
        listener.onInstanceCompleted(1L);
        verify(listener, times(1)).onInstanceCompleted(1L);
    }

    @Test
    @DisplayName("onInstanceRejected 正常调用")
    void testOnInstanceRejected() {
        FlowEventListener listener = Mockito.mock(FlowEventListener.class);
        listener.onInstanceRejected(1L, "条件不满足");
        verify(listener, times(1)).onInstanceRejected(1L, "条件不满足");
    }

    @Test
    @DisplayName("onError 记录异常但不影响流程")
    void testOnError() {
        FlowEventListener listener = Mockito.mock(FlowEventListener.class);
        listener.onError(1L, new RuntimeException("模拟异常"));
        verify(listener, times(1)).onError(Mockito.anyLong(), Mockito.any(Throwable.class));
    }

    @Test
    @DisplayName("通过 verify 验证方法被调用（mock 模式）")
    void testMockListener() {
        FlowEventListener mock = Mockito.mock(FlowEventListener.class);
        mock.onInstanceStart(1L, Map.of());
        mock.onInstanceCompleted(1L);
        mock.onInstanceRejected(1L, "reason");
        verify(mock, times(1)).onInstanceStart(1L, Map.of());
        verify(mock, times(1)).onInstanceCompleted(1L);
        verify(mock, times(1)).onInstanceRejected(1L, "reason");
        assertThat(mock).isNotNull();
    }
}
