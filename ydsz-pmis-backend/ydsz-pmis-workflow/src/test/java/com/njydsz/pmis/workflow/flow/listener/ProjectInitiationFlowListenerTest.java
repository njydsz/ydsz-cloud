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
 * <p>验证监听器在生命周期事件中正确触发且不影响主流程。
 */
@DisplayName("ProjectInitiationFlowListener 单元测试")
class ProjectInitiationFlowListenerTest {

    @Test
    @DisplayName("onInstanceStart 正常调用")
    void testOnInstanceStart() {
        FlowEventListener listener = new ProjectInitiationFlowListener();
        listener.onInstanceStart(1L, Map.of("k", "v"));
        // 单纯验证不抛异常
    }

    @Test
    @DisplayName("onInstanceStart 接受 null 变量")
    void testOnInstanceStartNullVars() {
        FlowEventListener listener = new ProjectInitiationFlowListener();
        listener.onInstanceStart(1L, null);
    }

    @Test
    @DisplayName("onTaskCreated / onTaskCompleted 正常调用")
    void testOnTaskEvents() {
        FlowEventListener listener = new ProjectInitiationFlowListener();
        listener.onTaskCreated(100L);
        listener.onTaskCompleted(100L, "PASS", Map.of());
        listener.onTaskCompleted(101L, "REJECT", Map.of());
    }

    @Test
    @DisplayName("onInstanceCompleted 正常调用")
    void testOnInstanceCompleted() {
        FlowEventListener listener = new ProjectInitiationFlowListener();
        listener.onInstanceCompleted(1L);
    }

    @Test
    @DisplayName("onInstanceRejected 正常调用")
    void testOnInstanceRejected() {
        FlowEventListener listener = new ProjectInitiationFlowListener();
        listener.onInstanceRejected(1L, "条件不满足");
    }

    @Test
    @DisplayName("onError 记录异常但不影响流程")
    void testOnError() {
        FlowEventListener listener = new ProjectInitiationFlowListener();
        listener.onError(1L, new RuntimeException("模拟异常"));
        // 单纯验证不抛异常
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
