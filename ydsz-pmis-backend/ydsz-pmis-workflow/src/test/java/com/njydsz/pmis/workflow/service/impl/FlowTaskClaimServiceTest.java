package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.impl.instance.FlowTaskAuditService;
import com.njydsz.pmis.workflow.service.impl.instance.FlowTaskClaimService;
import com.njydsz.pmis.workflow.service.impl.instance.FlowTaskSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskClaimService 单元测试
 *
 * <p>验证签收服务的核心行为：PENDING 状态校验、状态切换为 CLAIMED、审计日志、Prometheus 指标。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlowTaskClaimService 签收服务测试")
class FlowTaskClaimServiceTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowTaskSupport support;
    @Mock
    private FlowTaskAuditService auditService;
    @Mock
    private FlowMetrics flowMetrics;

    @InjectMocks
    private FlowTaskClaimService service;

    private FlowRunTaskDO pendingTask;

    @BeforeEach
    void setUp() {
        pendingTask = new FlowRunTaskDO();
        pendingTask.setId("T1");
        pendingTask.setTaskStatus(FlowTaskStatus.PENDING.name());
        pendingTask.setFlowCode("F1");
        pendingTask.setNodeCode("N1");
    }

    @Test
    @DisplayName("签收成功: PENDING → CLAIMED, 写入 claimAt/assigneeId")
    void claim_success() {
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        service.claim("T1", "U1");

        assertEquals(FlowTaskStatus.CLAIMED.name(), pendingTask.getTaskStatus());
        assertEquals("U1", pendingTask.getAssigneeId());
        assertNotNull(pendingTask.getClaimAt());
        verify(taskMapper).updateById(pendingTask);
        verify(support).audit(any(), any(), any(), any(), any());
        verify(auditService).logDelegateOperation(any(), any(), any());
    }

    @Test
    @DisplayName("签收 P2-3: 累计 Prometheus 签收指标")
    void claim_prometheus() {
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        service.claim("T1", "U1");

        verify(flowMetrics).incTaskClaimed("F1", "N1");
    }

    @Test
    @DisplayName("非 PENDING 状态抛 BAD_REQUEST 异常")
    void claim_invalidStatus() {
        pendingTask.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        BizException ex = assertThrows(BizException.class, () -> service.claim("T1", "U1"));
        assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已完成状态不可签收")
    void claim_completed() {
        pendingTask.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        assertThrows(BizException.class, () -> service.claim("T1", "U1"));
    }

    @Test
    @DisplayName("任务不存在抛 NOT_FOUND 异常（由 support 抛出）")
    void claim_taskNotFound() {
        when(support.getTaskOrThrow("T1"))
                .thenThrow(new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_6541ab08", "T1"));

        BizException ex = assertThrows(BizException.class, () -> service.claim("T1", "U1"));
        assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("userId 为 String 类型时正确写入 assigneeId")
    void claim_stringUserId() {
        when(support.getTaskOrThrow("T1")).thenReturn(pendingTask);

        service.claim("T1", "10086");

        assertEquals("10086", pendingTask.getAssigneeId());
    }
}
