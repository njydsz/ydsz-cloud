package com.njydsz.pmis.workflow.listener;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.InitiationFeignClient;
import com.njydsz.pmis.common.feign.NotificationPushClient;
import com.njydsz.pmis.workflow.engine.FlowNotificationHelper;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.service.FlowSubProcessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProjectInitiationFlowListener 单元测试。
 *
 * <p>验证立项流程事件正确联动立项状态（审批中 / 已批准 / 已驳回），
 * 触发 IM 推送，异常时重试且不影响主流程。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ProjectInitiationFlowListener 立项流程监听测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectInitiationFlowListenerTest {

    @Mock
    private FlowNotificationHelper notificationHelper;
    @Mock
    private FlowInstanceMapper instanceMapper;
    @Mock
    private FlowTaskMapper taskMapper;
    @Mock
    private FlowSubProcessService subProcessService;
    @Mock
    private InitiationFeignClient initiationFeignClient;
    @Mock
    private NotificationPushClient notificationPushClient;

    @InjectMocks
    private ProjectInitiationFlowListener listener;

    private FlowInstanceDO instance(Long id, String businessId) {
        FlowInstanceDO inst = new FlowInstanceDO();
        inst.setId(id);
        inst.setBusinessId(businessId);
        inst.setInitiatorId(2001L);
        inst.setFlowName("立项审批流程");
        inst.setTitle("XX 项目立项");
        return inst;
    }

    private FlowTaskDO task(Long id, String assigneeId) {
        FlowTaskDO t = new FlowTaskDO();
        t.setId(id);
        t.setAssigneeId(assigneeId);
        t.setFlowName("立项审批流程");
        t.setTitle("XX 项目立项");
        t.setNodeName("部门审批");
        return t;
    }

    @Test
    @DisplayName("onInstanceStart 应通过 Feign 标记立项审批中")
    void onInstanceStart_shouldMarkProcessing() {
        when(instanceMapper.selectById(1L)).thenReturn(instance(1L, "PMIS_INIT_101"));
        when(initiationFeignClient.markProcessing(101L)).thenReturn(Result.ok());

        listener.onInstanceStart(1L, Map.of("k", "v"));

        verify(initiationFeignClient, times(1)).markProcessing(101L);
    }

    @Test
    @DisplayName("onInstanceStart 业务键不可解析时不调用 Feign")
    void onInstanceStart_shouldSkipWhenBusinessIdUnparsable() {
        when(instanceMapper.selectById(2L)).thenReturn(instance(2L, "UNKNOWN_BIZ"));
        listener.onInstanceStart(2L, null);
        verify(initiationFeignClient, never()).markProcessing(anyLong());
    }

    @Test
    @DisplayName("onInstanceStart 实例不存在时不调用 Feign")
    void onInstanceStart_shouldSkipWhenInstanceMissing() {
        when(instanceMapper.selectById(3L)).thenReturn(null);
        listener.onInstanceStart(3L, null);
        verify(initiationFeignClient, never()).markProcessing(anyLong());
    }

    @Test
    @DisplayName("onTaskCreated 应推送 IM 消息给办理人")
    void onTaskCreated_shouldPushImNotification() {
        when(taskMapper.selectById(100L)).thenReturn(task(100L, "1001"));

        listener.onTaskCreated(100L);

        verify(notificationHelper).notifyTaskAssigned(eq(1001L), anyString(), anyString(),
                eq(100L), anyString(), anyString());
        verify(notificationPushClient, atLeastOnce())
                .pushToUser(eq(1001L), eq("NOTIFICATION"), any());
    }

    @Test
    @DisplayName("onInstanceCompleted 应通过 Feign 标记立项已批准")
    void onInstanceCompleted_shouldMarkApproved() {
        when(instanceMapper.selectById(1L)).thenReturn(instance(1L, "PMIS_INIT_101"));
        when(initiationFeignClient.markApproved(101L)).thenReturn(Result.ok());

        listener.onInstanceCompleted(1L);

        verify(initiationFeignClient).markApproved(101L);
    }

    @Test
    @DisplayName("onInstanceRejected 应通过 Feign 标记立项已驳回并传递原因")
    void onInstanceRejected_shouldMarkRejectedWithReason() {
        when(instanceMapper.selectById(1L)).thenReturn(instance(1L, "PMIS_INIT_101"));
        when(initiationFeignClient.markRejected(eq(101L), anyString())).thenReturn(Result.ok());

        listener.onInstanceRejected(1L, "预算不足");

        verify(initiationFeignClient).markRejected(101L, "预算不足");
    }

    @Test
    @DisplayName("onError 应触发重试且不抛出异常")
    void onError_shouldTriggerRetryAndNotThrow() {
        when(instanceMapper.selectById(1L)).thenReturn(instance(1L, "PMIS_INIT_101"));
        when(initiationFeignClient.markProcessing(101L)).thenReturn(Result.ok());

        assertThatCode(() -> listener.onError(1L, new RuntimeException("flow error")))
                .doesNotThrowAnyException();

        verify(initiationFeignClient, atLeastOnce()).markProcessing(101L);
    }

    @Test
    @DisplayName("onError instanceId 为 null 时安全返回")
    void onError_shouldSafelyReturnWhenInstanceIdNull() {
        assertThatCode(() -> listener.onError(null, new RuntimeException("err")))
                .doesNotThrowAnyException();
        verify(initiationFeignClient, never()).markProcessing(anyLong());
    }

    @Test
    @DisplayName("Feign 调用瞬时失败时应重试至成功")
    void linkageWithRetry_shouldRetryOnTransientFailure() {
        when(instanceMapper.selectById(1L)).thenReturn(instance(1L, "PMIS_INIT_101"));
        // 第一次失败，第二次成功
        when(initiationFeignClient.markProcessing(101L))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(Result.ok());

        listener.onInstanceStart(1L, Map.of());

        verify(initiationFeignClient, times(2)).markProcessing(101L);
    }

    @Test
    @DisplayName("Feign 持续失败时重试指定次数后停止且不抛出")
    void linkageWithRetry_shouldStopAfterMaxAttempts() {
        when(instanceMapper.selectById(1L)).thenReturn(instance(1L, "PMIS_INIT_101"));
        when(initiationFeignClient.markProcessing(101L))
                .thenThrow(new RuntimeException("service down"));

        assertThatCode(() -> listener.onInstanceStart(1L, Map.of()))
                .doesNotThrowAnyException();

        // 重试 3 次
        verify(initiationFeignClient, times(3)).markProcessing(101L);
    }
}
