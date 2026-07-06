package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.entity.FlowUserDO;
import com.njydsz.pmis.workflow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.enums.FlowSignType;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskSignServiceImpl 单元测试
 *
 * <p>GAP-P0-3: 覆盖加签/减签/追加处理人的核心场景，重点验证并加签（PARALLEL）语义。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>并加签 - 成功插入 PARALLEL 类型审批人 + 切换 performType 为 PARALLEL</li>
 *   <li>并加签 - 任务已完成时抛 BAD_REQUEST</li>
 *   <li>并加签 - targetUserId 为 null 时抛 BAD_REQUEST</li>
 *   <li>前加签 - 设置 signType=BEFORE</li>
 *   <li>后加签 - 设置 signType=AFTER + 切换 SEQUENTIAL</li>
 *   <li>追加处理人 - 字段完整 + signType=ADD</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@ExtendWith(MockitoExtension.class)
class FlowTaskSignServiceImplTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowUserMapper userMapper;
    @Mock
    private FlowTaskSupport support;

    @InjectMocks
    private FlowTaskSignServiceImpl signService;

    private static final Long TASK_ID = 500L;
    private static final Long INSTANCE_ID = 1001L;
    private static final Long TENANT_ID = 1L;
    private static final String NODE_CODE = "node_approval_1";
    private static final String TRACE_ID = "trace-abc-123";

    // ==================== GAP-P0-3: 并加签 ====================

    @Test
    @DisplayName("并加签 - 成功插入 PARALLEL 类型审批人 + 切换 performType 为 PARALLEL")
    void countersignParallelShouldInsertParallelUserAndSwitchPerformType() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING, FlowPerformType.OR, 1, 0);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildDto(999L, "张三");

        signService.countersignParallel(dto);

        // 验证插入的 FlowUserDO 字段
        ArgumentCaptor<FlowUserDO> userCaptor = ArgumentCaptor.forClass(FlowUserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        FlowUserDO insertedUser = userCaptor.getValue();
        assertThat(insertedUser.getTaskId()).isEqualTo(TASK_ID);
        assertThat(insertedUser.getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(insertedUser.getNodeCode()).isEqualTo(NODE_CODE);
        assertThat(insertedUser.getUserId()).isEqualTo("999");
        assertThat(insertedUser.getUserName()).isEqualTo("张三");
        assertThat(insertedUser.getProcessed()).isZero();
        assertThat(insertedUser.getWeight()).isEqualTo(1);
        assertThat(insertedUser.getSignType()).isEqualTo(FlowSignType.PARALLEL.name());
        assertThat(insertedUser.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(insertedUser.getProviderTraceId()).isEqualTo(TRACE_ID);

        // 验证 performType 切换为 PARALLEL + approveCount +1
        assertThat(task.getPerformType()).isEqualTo(FlowPerformType.PARALLEL.name());
        assertThat(task.getApproveCount()).isEqualTo(2);

        // 验证审计 + 事件
        verify(support).audit(task, "COUNTERSIGN_PARALLEL", dto.getUserId(), 999L, dto.getComment());
        verify(support).fireEvent(any(), eq(TASK_ID));
        verify(support).publishWorkflowEvent("TASK_COUNTERSIGNED", INSTANCE_ID, TASK_ID);
    }

    @Test
    @DisplayName("并加签 - 任务已完成时抛 BAD_REQUEST")
    void countersignParallelShouldThrowWhenTaskFinished() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.COMPLETED, FlowPerformType.OR, 1, 1);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildDto(999L, "张三");

        assertThatThrownBy(() -> signService.countersignParallel(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });

        verify(userMapper, never()).insert(any(FlowUserDO.class));
        verify(taskMapper, never()).updateById(any(FlowRunTaskDO.class));
    }

    @Test
    @DisplayName("并加签 - targetUserId 为 null 时抛 BAD_REQUEST")
    void countersignParallelShouldThrowWhenTargetUserIdNull() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING, FlowPerformType.OR, 1, 0);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildDto(null, null);

        assertThatThrownBy(() -> signService.countersignParallel(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });

        verify(userMapper, never()).insert(any(FlowUserDO.class));
    }

    // ==================== 前加签 signType 验证 ====================

    @Test
    @DisplayName("前加签 - 设置 signType=BEFORE")
    void countersignBeforeShouldSetSignTypeBefore() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING, FlowPerformType.OR, 1, 0);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildDto(888L, "李四");

        signService.countersignBefore(dto);

        ArgumentCaptor<FlowUserDO> userCaptor = ArgumentCaptor.forClass(FlowUserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getSignType()).isEqualTo(FlowSignType.BEFORE.name());
        // 前加签不切换 performType
        assertThat(task.getPerformType()).isEqualTo(FlowPerformType.OR.name());
        assertThat(task.getApproveCount()).isEqualTo(2);
    }

    // ==================== 后加签 signType 验证 ====================

    @Test
    @DisplayName("后加签 - 设置 signType=AFTER + 切换 SEQUENTIAL")
    void countersignAfterShouldSetSignTypeAfterAndSwitchSequential() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING, FlowPerformType.OR, 1, 0);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildDto(777L, "王五");

        signService.countersignAfter(dto);

        ArgumentCaptor<FlowUserDO> userCaptor = ArgumentCaptor.forClass(FlowUserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getSignType()).isEqualTo(FlowSignType.AFTER.name());
        // 后加签切换为 SEQUENTIAL
        assertThat(task.getPerformType()).isEqualTo(FlowPerformType.SEQUENTIAL.name());
        assertThat(task.getApproveCount()).isEqualTo(2);
    }

    // ==================== 追加处理人字段完整性验证（GAP-P0-3 bug 修复） ====================

    @Test
    @DisplayName("追加处理人 - 字段完整 + signType=ADD（修复前 instanceId/nodeCode/userType 等字段缺失）")
    void addApproverShouldSetAllFieldsAndSignTypeAdd() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING, FlowPerformType.PARALLEL, 2, 0);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildDto(666L, "赵六");

        signService.addApprover(dto);

        ArgumentCaptor<FlowUserDO> userCaptor = ArgumentCaptor.forClass(FlowUserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        FlowUserDO insertedUser = userCaptor.getValue();
        // GAP-P0-3 bug 修复：验证所有必要字段都已设置
        assertThat(insertedUser.getTaskId()).isEqualTo(TASK_ID);
        assertThat(insertedUser.getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(insertedUser.getNodeCode()).isEqualTo(NODE_CODE);
        assertThat(insertedUser.getUserType()).isEqualTo("USER");
        assertThat(insertedUser.getUserId()).isEqualTo("666");
        assertThat(insertedUser.getUserName()).isEqualTo("赵六");
        assertThat(insertedUser.getProcessed()).isZero();
        assertThat(insertedUser.getWeight()).isEqualTo(1);
        assertThat(insertedUser.getSignType()).isEqualTo(FlowSignType.ADD.name());
        assertThat(insertedUser.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(insertedUser.getProviderTraceId()).isEqualTo(TRACE_ID);

        // 追加处理人不切换 performType（保持 PARALLEL）
        assertThat(task.getPerformType()).isEqualTo(FlowPerformType.PARALLEL.name());
        assertThat(task.getApproveCount()).isEqualTo(3);
    }

    // ==================== 辅助方法 ====================

    private FlowRunTaskDO buildTask(FlowTaskStatus status, FlowPerformType performType,
                                  Integer approveCount, Integer approveFinished) {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId(TASK_ID);
        task.setInstanceId(INSTANCE_ID);
        task.setNodeCode(NODE_CODE);
        task.setTaskStatus(status.name());
        task.setPerformType(performType.name());
        task.setApproveCount(approveCount);
        task.setApproveFinished(approveFinished);
        task.setTenantId(TENANT_ID);
        task.setProviderTraceId(TRACE_ID);
        return task;
    }

    private FlowTaskOperateDTO buildDto(Long targetUserId, String targetUserName) {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(TASK_ID);
        dto.setUserId(100L);
        dto.setUserName("操作人");
        dto.setTargetUserId(targetUserId);
        dto.setTargetUserName(targetUserName);
        dto.setComment("加签测试");
        return dto;
    }

    /** 简化 verify(support).fireEvent(any(), eq(TASK_ID)) 的静态导入补充 */
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
