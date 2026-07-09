package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskTimeoutService 单元测试
 *
 * <p>验证超时/挂起/激活服务的状态校验、状态切换、审计、指标、事件触发。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlowTaskTimeoutService 超时/挂起/激活服务测试")
class FlowTaskTimeoutServiceTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowTaskSupport support;
    @Mock
    private FlowMetrics flowMetrics;

    @InjectMocks
    private FlowTaskTimeoutService service;

    private FlowRunTaskDO pendingTask;
    private FlowRunTaskDO claimedTask;
    private FlowRunTaskDO suspendedTask;

    @BeforeEach
    void setUp() {
        pendingTask = baseTask("T1", FlowTaskStatus.PENDING.name());
        claimedTask = baseTask("T2", FlowTaskStatus.CLAIMED.name());
        suspendedTask = baseTask("T3", FlowTaskStatus.SUSPENDED.name());
    }

    private FlowRunTaskDO baseTask(String id, String status) {
        FlowRunTaskDO t = new FlowRunTaskDO();
        t.setId(id);
        t.setTaskStatus(status);
        t.setInstanceId("I1");
        t.setFlowCode("F1");
        t.setNodeCode("N1");
        // 注：FlowRunTaskDO.setCreatedAt 来自父类 BaseDO（Lombok @Data），保持 null 不影响测试
        return t;
    }

    // ============================== timeoutTask ==============================

    @Test
    @DisplayName("timeoutTask PENDING → TIMEOUT")
    void timeoutTask_pending() {
        // 通过父类引用设置 createdAt（@Data 在 BaseDO 生成的 setter）
        com.njydsz.pmis.common.entity.BaseDO baseRef = pendingTask;
        baseRef.setCreatedAt(LocalDateTime.now().minusHours(2));
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        service.timeoutTask("T1", "已超时");

        verify(taskMapper).completeTask(anyString(), anyString(), any(), any(), any());
        assertEquals(FlowTaskStatus.TIMEOUT.name(), pendingTask.getTaskStatus());
        assertEquals("已超时", pendingTask.getComment());
        assertNotNull(pendingTask.getFinishAt());
        assertNotNull(pendingTask.getDurationMs());
        verify(support).audit(any(), any(), any(), any(), any());
        verify(flowMetrics).incTaskAutoHandled("F1", "N1", "TIMEOUT");
        verify(support).fireEvent(any(), anyString());
        verify(support).publishWorkflowEvent(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("timeoutTask CLAIMED → TIMEOUT")
    void timeoutTask_claimed() {
        when(support.getTaskOrThrow("T2")).thenReturn(claimedTask);

        service.timeoutTask("T2", "已超时");

        assertEquals(FlowTaskStatus.TIMEOUT.name(), claimedTask.getTaskStatus());
    }

    @Test
    @DisplayName("timeoutTask 已完成状态抛 BAD_REQUEST")
    void timeoutTask_invalidStatus() {
        FlowRunTaskDO t = baseTask("T4", FlowTaskStatus.COMPLETED.name());
        when(support.getTaskOrThrow("T4")).thenReturn(t);

        BizException ex = assertThrows(BizException.class, () -> service.timeoutTask("T4", "reason"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ============================== suspendTask ==============================

    @Test
    @DisplayName("suspendTask PENDING → SUSPENDED")
    void suspendTask_pending() {
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        service.suspendTask("T1", "U1", "暂时挂起");

        assertEquals(FlowTaskStatus.SUSPENDED.name(), pendingTask.getTaskStatus());
        assertEquals("暂时挂起", pendingTask.getComment());
        verify(taskMapper).updateById(pendingTask);
        verify(support).audit(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("suspendTask CLAIMED → SUSPENDED")
    void suspendTask_claimed() {
        when(support.getTaskOrThrow("T2")).thenReturn(claimedTask);

        service.suspendTask("T2", "U1", "暂时挂起");

        assertEquals(FlowTaskStatus.SUSPENDED.name(), claimedTask.getTaskStatus());
    }

    @Test
    @DisplayName("suspendTask 已完成状态不可挂起")
    void suspendTask_invalidStatus() {
        FlowRunTaskDO t = baseTask("T4", FlowTaskStatus.COMPLETED.name());
        when(support.getTaskOrThrow("T4")).thenReturn(t);

        BizException ex = assertThrows(BizException.class, () -> service.suspendTask("T4", "U1", "reason"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ============================== activateTask ==============================

    @Test
    @DisplayName("activateTask SUSPENDED → PENDING 并清空签收人")
    void activateTask_suspended() {
        suspendedTask.setAssigneeId("U1");
        suspendedTask.setAssigneeName("user1");
        suspendedTask.setClaimAt(LocalDateTime.now());
        when(support.getTaskOrThrow("T3")).thenReturn(suspendedTask);

        service.activateTask("T3", "U2");

        assertEquals(FlowTaskStatus.PENDING.name(), suspendedTask.getTaskStatus());
        assertEquals(null, suspendedTask.getAssigneeId());
        assertEquals(null, suspendedTask.getAssigneeName());
        assertEquals(null, suspendedTask.getClaimAt());
        verify(taskMapper).updateById(suspendedTask);
        verify(support).audit(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("activateTask 非 SUSPENDED 状态抛 BAD_REQUEST")
    void activateTask_invalidStatus() {
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        BizException ex = assertThrows(BizException.class, () -> service.activateTask("T1", "U1"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ============================== cancelByInstance ==============================

    @Test
    @DisplayName("cancelByInstance 委托给 mapper")
    void cancelByInstance() {
        service.cancelByInstance("I1", FlowTaskStatus.CANCELLED.name());

        verify(taskMapper).cancelByInstance("I1", FlowTaskStatus.CANCELLED.name());
    }
}
