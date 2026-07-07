package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-1: 任务级挂起/激活（suspendTask / activateTask）单元测试。
 *
 * <p>聚焦测试 {@link FlowTaskCompleteServiceImpl#suspendTask} 和
 * {@link FlowTaskCompleteServiceImpl#activateTask} 的状态校验、字段更新、审计日志逻辑。
 *
 * <p>构造方式：FlowTaskCompleteServiceImpl 字段较多（20+），但 suspend/activate 仅依赖
 * {@code support}（FlowTaskSupport）和 {@code taskMapper}（FlowRunTaskMapper）。
 * Mockito {@link InjectMocks} 通过最大参数构造函数注入，匹配类型的 @Mock 被注入，
 * 其余未提供 @Mock 的依赖注入为 null（suspend/activate 不会访问它们）。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@DisplayName("P2-1 任务级挂起/激活测试")
@ExtendWith(MockitoExtension.class)
class FlowTaskSuspendActivateTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowTaskSupport support;

    @InjectMocks
    private FlowTaskCompleteServiceImpl service;

    private FlowRunTaskDO buildTask(String status) {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("task-1");
        task.setInstanceId("inst-1");
        task.setFlowCode("FLOW_TEST");
        task.setNodeCode("node-approve");
        task.setTaskStatus(status);
        task.setAssigneeId("user-1");
        task.setAssigneeName("张三");
        task.setClaimAt(LocalDateTime.now().minusHours(1));
        task.setTenantId("1");
        return task;
    }

    // ============================== 挂起（suspendTask）测试 ==============================

    @Nested
    @DisplayName("挂起任务 suspendTask")
    class SuspendTaskTest {

        @Test
        @DisplayName("任务不存在 → 抛 NOT_FOUND")
        void taskNotFound_throwsNotFound() {
            when(support.getTaskOrThrow("task-1"))
                    .thenThrow(new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_6541ab08", "task-1"));

            BizException ex = assertThrows(BizException.class,
                    () -> service.suspendTask("task-1", "user-2", "暂停审批"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("任务状态为 COMPLETED → 抛 BAD_REQUEST（msg_d0e1f2a3）")
        void completedStatus_throwsBadRequest() {
            when(support.getTaskOrThrow("task-1")).thenReturn(buildTask(FlowTaskStatus.COMPLETED.name()));

            BizException ex = assertThrows(BizException.class,
                    () -> service.suspendTask("task-1", "user-2", null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_d0e1f2a3", ex.getErrorMessage());
        }

        @Test
        @DisplayName("任务状态为 SUSPENDED（重复挂起）→ 抛 BAD_REQUEST")
        void suspendedStatus_throwsBadRequest() {
            when(support.getTaskOrThrow("task-1")).thenReturn(buildTask(FlowTaskStatus.SUSPENDED.name()));

            BizException ex = assertThrows(BizException.class,
                    () -> service.suspendTask("task-1", "user-2", null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("任务状态为 REJECTED → 抛 BAD_REQUEST")
        void rejectedStatus_throwsBadRequest() {
            when(support.getTaskOrThrow("task-1")).thenReturn(buildTask(FlowTaskStatus.REJECTED.name()));

            assertThrows(BizException.class,
                    () -> service.suspendTask("task-1", "user-2", null));
        }

        @Test
        @DisplayName("PENDING → 挂起成功，状态更新为 SUSPENDED")
        void pendingStatus_suspendsSuccessfully() {
            FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING.name());
            when(support.getTaskOrThrow("task-1")).thenReturn(task);
            doNothing().when(support).audit(any(), eq("SUSPEND"), eq("user-2"), eq(null), eq("等待材料"));

            service.suspendTask("task-1", "user-2", "等待材料");

            ArgumentCaptor<FlowRunTaskDO> captor = ArgumentCaptor.forClass(FlowRunTaskDO.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals(FlowTaskStatus.SUSPENDED.name(), captor.getValue().getTaskStatus());
            assertEquals("等待材料", captor.getValue().getComment());
            assertNotNull(captor.getValue().getUpdatedAt());
        }

        @Test
        @DisplayName("CLAIMED → 挂起成功（已签收任务也可挂起）")
        void claimedStatus_suspendsSuccessfully() {
            FlowRunTaskDO task = buildTask(FlowTaskStatus.CLAIMED.name());
            when(support.getTaskOrThrow("task-1")).thenReturn(task);
            doNothing().when(support).audit(any(), eq("SUSPEND"), eq("user-2"), eq(null), eq(null));

            service.suspendTask("task-1", "user-2", null);

            ArgumentCaptor<FlowRunTaskDO> captor = ArgumentCaptor.forClass(FlowRunTaskDO.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals(FlowTaskStatus.SUSPENDED.name(), captor.getValue().getTaskStatus());
        }

        @Test
        @DisplayName("挂起后写审计日志 action=SUSPEND")
        void writesAuditLogOnSuspend() {
            FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING.name());
            when(support.getTaskOrThrow("task-1")).thenReturn(task);

            service.suspendTask("task-1", "user-2", "等待补充材料");

            verify(support).audit(task, "SUSPEND", "user-2", null, "等待补充材料");
        }

        @Test
        @DisplayName("reason=null → comment 设为 null，审计日志正常写入")
        void nullReason_auditLogStillWritten() {
            FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING.name());
            when(support.getTaskOrThrow("task-1")).thenReturn(task);

            service.suspendTask("task-1", "user-2", null);

            verify(support).audit(task, "SUSPEND", "user-2", null, null);
        }
    }

    // ============================== 激活（activateTask）测试 ==============================

    @Nested
    @DisplayName("激活任务 activateTask")
    class ActivateTaskTest {

        @Test
        @DisplayName("任务不存在 → 抛 NOT_FOUND")
        void taskNotFound_throwsNotFound() {
            when(support.getTaskOrThrow("task-1"))
                    .thenThrow(new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_6541ab08", "task-1"));

            BizException ex = assertThrows(BizException.class,
                    () -> service.activateTask("task-1", "user-2"));
            assertEquals(BizErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("任务状态为 PENDING → 抛 BAD_REQUEST（msg_e1f2a3b4）")
        void pendingStatus_throwsBadRequest() {
            when(support.getTaskOrThrow("task-1")).thenReturn(buildTask(FlowTaskStatus.PENDING.name()));

            BizException ex = assertThrows(BizException.class,
                    () -> service.activateTask("task-1", "user-2"));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_e1f2a3b4", ex.getErrorMessage());
        }

        @Test
        @DisplayName("任务状态为 COMPLETED → 抛 BAD_REQUEST")
        void completedStatus_throwsBadRequest() {
            when(support.getTaskOrThrow("task-1")).thenReturn(buildTask(FlowTaskStatus.COMPLETED.name()));

            assertThrows(BizException.class,
                    () -> service.activateTask("task-1", "user-2"));
        }

        @Test
        @DisplayName("任务状态为 FROZEN → 抛 BAD_REQUEST（FROZEN 需用实例级激活）")
        void frozenStatus_throwsBadRequest() {
            when(support.getTaskOrThrow("task-1")).thenReturn(buildTask(FlowTaskStatus.FROZEN.name()));

            assertThrows(BizException.class,
                    () -> service.activateTask("task-1", "user-2"));
        }

        @Test
        @DisplayName("SUSPENDED → 激活成功，状态更新为 PENDING")
        void suspendedStatus_activatesSuccessfully() {
            FlowRunTaskDO task = buildTask(FlowTaskStatus.SUSPENDED.name());
            when(support.getTaskOrThrow("task-1")).thenReturn(task);
            doNothing().when(support).audit(any(), eq("ACTIVATE"), eq("user-2"), eq(null), eq(null));

            service.activateTask("task-1", "user-2");

            ArgumentCaptor<FlowRunTaskDO> captor = ArgumentCaptor.forClass(FlowRunTaskDO.class);
            verify(taskMapper).updateById(captor.capture());
            assertEquals(FlowTaskStatus.PENDING.name(), captor.getValue().getTaskStatus());
        }

        @Test
        @DisplayName("激活后清空签收人（assigneeId/assigneeName/claimAt）")
        void clearsAssigneeOnActivate() {
            FlowRunTaskDO task = buildTask(FlowTaskStatus.SUSPENDED.name());
            // 挂起前已被签收：assigneeId/assigneeName/claimAt 都有值
            assertNotNull(task.getAssigneeId());
            assertNotNull(task.getAssigneeName());
            assertNotNull(task.getClaimAt());
            when(support.getTaskOrThrow("task-1")).thenReturn(task);

            service.activateTask("task-1", "user-2");

            ArgumentCaptor<FlowRunTaskDO> captor = ArgumentCaptor.forClass(FlowRunTaskDO.class);
            verify(taskMapper).updateById(captor.capture());
            FlowRunTaskDO updated = captor.getValue();
            assertEquals(FlowTaskStatus.PENDING.name(), updated.getTaskStatus());
            assertNull(updated.getAssigneeId(), "激活后 assigneeId 应清空");
            assertNull(updated.getAssigneeName(), "激活后 assigneeName 应清空");
            assertNull(updated.getClaimAt(), "激活后 claimAt 应清空");
            assertNotNull(updated.getUpdatedAt(), "updatedAt 应被刷新");
        }

        @Test
        @DisplayName("激活后写审计日志 action=ACTIVATE")
        void writesAuditLogOnActivate() {
            FlowRunTaskDO task = buildTask(FlowTaskStatus.SUSPENDED.name());
            when(support.getTaskOrThrow("task-1")).thenReturn(task);

            service.activateTask("task-1", "user-2");

            verify(support).audit(task, "ACTIVATE", "user-2", null, null);
        }
    }
}
