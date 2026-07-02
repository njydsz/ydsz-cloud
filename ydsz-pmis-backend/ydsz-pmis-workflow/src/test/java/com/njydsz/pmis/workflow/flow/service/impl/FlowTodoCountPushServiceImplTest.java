package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.NotificationClient;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.service.impl.FlowTodoCountPushServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTodoCountPushServiceImpl 单元测试
 *
 * <p>P1-7: WebSocket 待办数推送
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@DisplayName("FlowTodoCountPushServiceImpl 单元测试")
class FlowTodoCountPushServiceImplTest {

    private FlowTaskMapper taskMapper;
    private NotificationClient notificationClient;
    private FlowTodoCountPushServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(FlowTaskMapper.class);
        notificationClient = mock(NotificationClient.class);
        service = new FlowTodoCountPushServiceImpl(taskMapper, notificationClient);
    }

    // ============================== pushTodoCount ==============================

    @Test
    @DisplayName("pushTodoCount null userId 跳过")
    void testPushTodoCountNullUserId() {
        service.pushTodoCount(null);
        verify(taskMapper, never()).countTodoByAssignee(anyString(), any());
        verify(notificationClient, never()).pushRealtime(any(), anyString(), any());
    }

    @Test
    @DisplayName("pushTodoCount 正常调用 Feign 接口")
    void testPushTodoCountNormal() {
        when(taskMapper.countTodoByAssignee(eq("1001"), any())).thenReturn(5L);
        when(notificationClient.pushRealtime(anyLong(), anyString(), any()))
                .thenReturn(Result.ok(Collections.emptyMap()));

        service.pushTodoCount(1001L);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(notificationClient, times(1)).pushRealtime(eq(1001L), typeCaptor.capture(),
                payloadCaptor.capture());
        assertThat(typeCaptor.getValue()).isEqualTo("TODO_COUNT");
        assertThat(payloadCaptor.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("userId", 1001L);
        assertThat(payload).containsEntry("todoCount", 5L);
        assertThat(payload).containsKey("timestamp");
    }

    @Test
    @DisplayName("pushTodoCount Feign 异常被 try-catch 吞掉")
    void testPushTodoCountFeignException() {
        when(taskMapper.countTodoByAssignee(eq("1001"), any())).thenReturn(5L);
        when(notificationClient.pushRealtime(anyLong(), anyString(), any()))
                .thenThrow(new RuntimeException("notification down"));

        // 不应抛异常
        service.pushTodoCountSafe(1001L);
        verify(notificationClient, times(1)).pushRealtime(anyLong(), anyString(), any());
    }

    // ============================== pushTaskAssigned ==============================

    @Test
    @DisplayName("pushTaskAssigned null task 跳过")
    void testPushTaskAssignedNull() {
        service.pushTaskAssigned(null);
        verify(notificationClient, never()).pushRealtime(any(), anyString(), any());
    }

    @Test
    @DisplayName("pushTaskAssigned assigneeId 非数字跳过")
    void testPushTaskAssignedNonNumeric() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setAssigneeId("INITIATOR");
        service.pushTaskAssigned(task);
        verify(notificationClient, never()).pushRealtime(any(), anyString(), any());
    }

    @Test
    @DisplayName("pushTaskAssigned 正常推送 + 自动触发待办数推送")
    void testPushTaskAssignedNormal() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(100L);
        task.setAssigneeId("1001");
        task.setAssigneeName("张三");
        task.setTitle("测试任务");
        task.setFlowName("测试流程");
        task.setNodeName("审批");
        task.setBusinessType("initiation");
        task.setBusinessId("200");
        task.setDueAt(LocalDateTime.of(2026, 7, 10, 9, 0, 0));
        when(taskMapper.countTodoByAssignee(eq("1001"), any())).thenReturn(3L);
        when(notificationClient.pushRealtime(anyLong(), anyString(), any()))
                .thenReturn(Result.ok(Collections.emptyMap()));

        service.pushTaskAssigned(task);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationClient, times(2)).pushRealtime(eq(1001L), typeCaptor.capture(), any());
        var types = typeCaptor.getAllValues();
        assertThat(types).contains("TASK_ASSIGNED", "TODO_COUNT");
    }

    // ============================== pushTaskCompleted ==============================

    @Test
    @DisplayName("pushTaskCompleted null task 跳过")
    void testPushTaskCompletedNull() {
        service.pushTaskCompleted(null, 1001L);
        verify(notificationClient, never()).pushRealtime(any(), anyString(), any());
    }

    @Test
    @DisplayName("pushTaskCompleted null operator 跳过推送")
    void testPushTaskCompletedNullOperator() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(100L);
        task.setInstanceId(1L);
        task.setFlowName("流程");
        task.setNodeName("节点");
        service.pushTaskCompleted(task, null);
        verify(notificationClient, never()).pushRealtime(any(), anyString(), any());
    }

    @Test
    @DisplayName("pushTaskCompleted 正常推送 TASK_COMPLETED + TODO_COUNT")
    void testPushTaskCompletedNormal() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(100L);
        task.setInstanceId(1L);
        task.setFlowName("流程");
        task.setNodeName("节点");
        when(taskMapper.countTodoByAssignee(eq("1001"), any())).thenReturn(2L);
        when(notificationClient.pushRealtime(anyLong(), anyString(), any()))
                .thenReturn(Result.ok(Collections.emptyMap()));

        service.pushTaskCompleted(task, 1001L);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationClient, times(2)).pushRealtime(eq(1001L), typeCaptor.capture(), any());
        var types = typeCaptor.getAllValues();
        assertThat(types).contains("TASK_COMPLETED", "TODO_COUNT");
    }

    // ============================== pushTaskRejected ==============================

    @Test
    @DisplayName("pushTaskRejected 正常推送 TASK_REJECTED + 携带 reason")
    void testPushTaskRejectedNormal() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(200L);
        task.setInstanceId(2L);
        task.setFlowName("流程");
        task.setNodeName("节点");
        when(taskMapper.countTodoByAssignee(eq("1001"), any())).thenReturn(1L);
        when(notificationClient.pushRealtime(anyLong(), anyString(), any()))
                .thenReturn(Result.ok(Collections.emptyMap()));

        service.pushTaskRejected(task, 1001L, "信息不全");
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationClient, times(2)).pushRealtime(eq(1001L), typeCaptor.capture(),
                payloadCaptor.capture());
        var types = typeCaptor.getAllValues();
        assertThat(types).contains("TASK_REJECTED", "TODO_COUNT");

        // 第一次推送应是 TASK_REJECTED，payload 含 reason
        var payloads = payloadCaptor.getAllValues();
        boolean hasReason = payloads.stream()
                .filter(p -> p instanceof Map)
                .anyMatch(p -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) p;
                    return "信息不全".equals(m.get("reason"));
                });
        assertThat(hasReason).isTrue();
    }

    @Test
    @DisplayName("pushTaskRejected null operator 跳过推送")
    void testPushTaskRejectedNullOperator() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(200L);
        service.pushTaskRejected(task, null, "原因");
        verify(notificationClient, never()).pushRealtime(any(), anyString(), any());
    }
}
