package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * FlowTaskServiceImpl 单元测试
 *
 * <p>覆盖：createTask / claim / pass / reject / transfer / delegate / cancelByInstance /
 * listPendingByInstance / listTodoByAssignee / listDoneByAssignee / toView。
 */
@DisplayName("FlowTaskServiceImpl 单元测试")
class FlowTaskServiceImplTest {

    private FlowTaskMapper taskMapper;
    private FlowHisTaskMapper hisTaskMapper;
    private FlowInstanceMapper instanceMapper;
    private FlowInstanceServiceImpl instanceService;
    private FlowAdvancer advancer;
    private FlowVariableStrategy variableStrategy;
    private FlowTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        taskMapper = mock(FlowTaskMapper.class);
        hisTaskMapper = mock(FlowHisTaskMapper.class);
        instanceMapper = mock(FlowInstanceMapper.class);
        // 必须用真实 FlowInstanceServiceImpl：pass/reject 内有 instanceof cast
        instanceService = mock(FlowInstanceServiceImpl.class);
        advancer = mock(FlowAdvancer.class);
        variableStrategy = mock(FlowVariableStrategy.class);
        service = new FlowTaskServiceImpl(taskMapper, hisTaskMapper, instanceMapper,
                instanceService, advancer, variableStrategy);
    }

    // ============== createTask ==============

    @Test
    @DisplayName("createTask 实例不存在应抛 NOT_FOUND")
    void testCreateTaskInstanceNotFound() {
        when(instanceMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.createTask(1L, new FlowNodeDO(), Map.of()))
                .isInstanceOf(BizException.class);
        verify(taskMapper, never()).insert((FlowTaskDO) any());
    }

    @Test
    @DisplayName("createTask permissionFlag=user: → USER 类型办理人")
    void testCreateTaskUserAssignee() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(10L);
        ins.setFlowCode("f1");
        ins.setDefinitionId(1L);
        ins.setBusinessType("initiation");
        ins.setBusinessId("100");
        ins.setFlowName("测试");
        ins.setProviderTraceId("trace-1");
        ins.setTenantId(2L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("user:1001"), any())).thenReturn("user:1001");
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(99L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("审批");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("user:1001");

        Long id = service.createTask(10L, node, Map.of());
        assertThat(id).isEqualTo(99L);

        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO saved = captor.getValue();
        assertThat(saved.getInstanceId()).isEqualTo(10L);
        assertThat(saved.getNodeCode()).isEqualTo("t1");
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.USER.name());
        assertThat(saved.getAssigneeId()).isEqualTo("1001");
        assertThat(saved.getTaskStatus()).isEqualTo(FlowTaskStatus.PENDING.name());
        assertThat(saved.getPerformType()).isEqualTo("OR");
        assertThat(saved.getApproveCount()).isEqualTo(1);
        assertThat(saved.getApproveFinished()).isEqualTo(0);
        assertThat(saved.getTenantId()).isEqualTo(2L);
        assertThat(saved.getProviderTraceId()).isEqualTo("trace-1");
    }

    @Test
    @DisplayName("createTask permissionFlag=role:hr → ROLE 办理人")
    void testCreateTaskRoleAssignee() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("role:hr"), any())).thenReturn("role:hr");
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("role:hr");

        service.createTask(10L, node, Map.of());
        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO saved = captor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.ROLE.name());
        assertThat(saved.getAssigneeId()).isEqualTo("hr");
    }

    @Test
    @DisplayName("createTask permissionFlag=dept:10 → DEPT 办理人")
    void testCreateTaskDeptAssignee() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("dept:10"), any())).thenReturn("dept:10");
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("dept:10");

        service.createTask(10L, node, Map.of());
        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(captor.capture());
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO saved = captor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.DEPT.name());
        assertThat(saved.getAssigneeId()).isEqualTo("10");
    }

    @Test
    @DisplayName("createTask permissionFlag=${expression} → SPEL 办理人")
    void testCreateTaskSpelAssignee() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("${initiatorId}"), any())).thenReturn("${initiatorId}");
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("${initiatorId}");

        service.createTask(10L, node, Map.of("initiatorId", 7L));
        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAssigneeType()).isEqualTo(FlowAssigneeType.SPEL.name());
        assertThat(captor.getValue().getAssigneeId()).isEqualTo("${initiatorId}");
    }

    @Test
    @DisplayName("createTask 无 permissionFlag 时默认 INITIATOR（assigneeId = 发起人 ID）")
    void testCreateTaskNoPermissionFlag() {
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setInitiatorId(7L);  // 发起人
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(1L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag(null);

        service.createTask(10L, node, Map.of());
        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO saved = captor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.INITIATOR.name());
        assertThat(saved.getAssigneeId()).isEqualTo("7");  // initiatorId
        assertThat(saved.getAssigneeName()).isEqualTo("INITIATOR");
    }

    // ============== claim ==============

    @Test
    @DisplayName("claim 任务不存在应抛 NOT_FOUND")
    void testClaimNotFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.claim(99L, 100L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("claim 非 PENDING 任务应抛 BAD_REQUEST")
    void testClaimNotPending() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(taskMapper.selectById(1L)).thenReturn(task);
        assertThatThrownBy(() -> service.claim(1L, 100L))
                .isInstanceOf(BizException.class);
        verify(taskMapper, never()).updateById(any(FlowTaskDO.class));
    }

    @Test
    @DisplayName("claim 成功：assignee 改为 userId，状态改为 CLAIMED，签收时间回填")
    void testClaimSuccess() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("100");
        when(taskMapper.selectById(1L)).thenReturn(task);

        service.claim(1L, 999L);
        verify(taskMapper).updateById((FlowTaskDO) any());
        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO updated = captor.getValue();
        assertThat(updated.getAssigneeId()).isEqualTo("999");
        assertThat(updated.getTaskStatus()).isEqualTo(FlowTaskStatus.CLAIMED.name());
        assertThat(updated.getClaimAt()).isNotNull();
    }

    // ============== pass ==============

    @Test
    @DisplayName("pass 已完成的任务应抛 BAD_REQUEST")
    void testPassAlreadyFinished() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(taskMapper.selectById(1L)).thenReturn(task);
        assertThatThrownBy(() -> {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(1L);
            service.pass(dto);
        }).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("pass 任务不存在应抛 NOT_FOUND")
    void testPassNotFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(99L);
            service.pass(dto);
        }).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("pass 成功：完成当前任务 + 推进 + 生成下一批 + 归档历史")
    void testPassSuccess() {
        FlowTaskDO task = baseTask();
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(2));
        ins.setVariable("{\"k\":\"v\"}");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(ins);

        FlowNodeDO next = new FlowNodeDO();
        next.setNodeCode("t2");
        next.setNodeName("下个审批");
        next.setNodeType(FlowNodeType.APPROVAL.getCode());
        when(advancer.advance(any(), eq("t1"), eq("PASS"), eq(null), any()))
                .thenReturn(List.of(next));

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setComment("OK");
        dto.setVariables(Map.of("k2", "v2"));
        service.pass(dto);

        // 1. 当前任务标记完成
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.COMPLETED.name()),
                eq("OK"), any(), any());
        // 2. 归档到历史表
        verify(hisTaskMapper).insert((com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO) any());
        // 3. advancer.advance 被调用
        verify(advancer).advance(any(), eq("t1"), eq("PASS"), eq(null), any());
        // 4. generateTasksForNodes 被调用
        verify(instanceService).generateTasksForNodes(eq(10L), eq(List.of(next)), any());
        // 5. 更新当前节点（非 END）
        verify(instanceMapper).updateStatus(eq(10L), eq("RUNNING"),
                eq("t2"), eq("下个审批"), eq(null), eq(null));
    }

    @Test
    @DisplayName("pass 推进到 END 节点：不更新当前节点（由 generateTasks 内部处理）")
    void testPassToEnd() {
        FlowTaskDO task = baseTask();
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(2));
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(ins);

        FlowNodeDO end = new FlowNodeDO();
        end.setNodeCode("end1");
        end.setNodeType(FlowNodeType.END.getCode());
        when(advancer.advance(any(), anyString(), anyString(), any(), any()))
                .thenReturn(List.of(end));

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        service.pass(dto);

        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.COMPLETED.name()),
                any(), any(), any());
        verify(instanceService).generateTasksForNodes(eq(10L), eq(List.of(end)), any());
        // END 节点：updateStatus 不会再被调用一次（END 时 generateTasksForNodes 内部 complete 流程）
        verify(instanceMapper, never()).updateStatus(anyLong(), anyString(), anyString(),
                anyString(), any(), any());
    }

    // ============== reject ==============

    @Test
    @DisplayName("reject 已完成任务应抛 BAD_REQUEST")
    void testRejectFinished() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(taskMapper.selectById(1L)).thenReturn(task);
        assertThatThrownBy(() -> {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(1L);
            service.reject(dto);
        }).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("reject 找不到退回目标 → 流程终止 + 取消 PENDING 任务")
    void testRejectNoTarget() {
        FlowTaskDO task = baseTask();
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(3));
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(advancer.advance(any(), eq("t1"), eq("REJECT"), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setComment("不同意");
        service.reject(dto);

        // 任务标记 REJECTED + 归档
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.REJECTED.name()),
                eq("不同意"), any(), any());
        verify(hisTaskMapper).insert((com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO) any());
        // 流程进入 REJECTED 终态
        verify(instanceMapper).updateStatus(eq(10L), eq("REJECTED"),
                eq(null), eq(null), any(), any());
        // 取消全部 PENDING
        verify(taskMapper).cancelByInstance(eq(10L), eq(FlowTaskStatus.CANCELLED.name()));
    }

    @Test
    @DisplayName("reject 有退回目标：生成新任务 + 更新当前节点")
    void testRejectWithTarget() {
        FlowTaskDO task = baseTask();
        FlowInstanceDO ins = simpleInstance(10L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(ins);

        FlowNodeDO prev = new FlowNodeDO();
        prev.setNodeCode("s1");
        prev.setNodeName("开始");
        prev.setNodeType(FlowNodeType.START.getCode());
        when(advancer.advance(any(), eq("t1"), eq("REJECT"), eq("s1"), any()))
                .thenReturn(List.of(prev));

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetNodeCode("s1");
        service.reject(dto);

        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.REJECTED.name()),
                any(), any(), any());
        verify(instanceService).generateTasksForNodes(eq(10L), eq(List.of(prev)), any());
        verify(instanceMapper).updateStatus(eq(10L), eq("RUNNING"),
                eq("s1"), eq("开始"), eq(null), eq(null));
    }

    // ============== transfer / delegate ==============

    @Test
    @DisplayName("transfer 缺 targetUserId 抛 BAD_REQUEST")
    void testTransferNoTarget() {
        assertThatThrownBy(() -> {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(1L);
            service.transfer(dto);
        }).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("transfer 成功：assignee 改为目标人，状态 CLAIMED")
    void testTransferSuccess() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("100");
        task.setAssigneeName("原办理人");
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(200L);
        dto.setTargetUserName("新办理人");
        service.transfer(dto);

        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO updated = captor.getValue();
        assertThat(updated.getAssigneeId()).isEqualTo("200");
        assertThat(updated.getAssigneeName()).isEqualTo("新办理人");
        assertThat(updated.getAssignorId()).isEqualTo(100L);
        assertThat(updated.getAssignorName()).isEqualTo("原办理人");
        assertThat(updated.getTaskStatus()).isEqualTo(FlowTaskStatus.CLAIMED.name());
    }

    @Test
    @DisplayName("delegate 复用 transfer 实现")
    void testDelegate() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("100");
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(300L);
        dto.setTargetUserName("被委派人");
        service.delegate(dto);

        verify(taskMapper, times(1)).updateById(any(FlowTaskDO.class));
    }

    @Test
    @DisplayName("transfer 原 assigneeId 非数字时不抛异常（assignorId 为 null）")
    void testTransferNonNumericOriginalAssignee() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("abc");
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(200L);
        service.transfer(dto);

        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        assertThat(captor.getValue().getAssignorId()).isNull();
    }

    // ============== cancelByInstance / list* ==============

    @Test
    @DisplayName("cancelByInstance 委托 mapper")
    void testCancelByInstance() {
        service.cancelByInstance(10L, "CANCELLED");
        verify(taskMapper).cancelByInstance(10L, "CANCELLED");
    }

    @Test
    @DisplayName("listPendingByInstance 委托 mapper")
    void testListPendingByInstance() {
        FlowTaskDO t = new FlowTaskDO();
        t.setId(1L);
        when(taskMapper.selectPendingByInstance(10L)).thenReturn(List.of(t));
        List<FlowTaskDO> result = service.listPendingByInstance(10L);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("listTodoByAssignee tenantId 为 null 时默认 1L")
    void testListTodoByAssigneeDefaultTenant() {
        service.listTodoByAssignee("1001", null);
        verify(taskMapper).selectTodoByAssignee("1001", 1L);
    }

    @Test
    @DisplayName("listTodoByAssignee 使用指定 tenantId")
    void testListTodoByAssigneeCustomTenant() {
        service.listTodoByAssignee("1001", 5L);
        verify(taskMapper).selectTodoByAssignee("1001", 5L);
    }

    @Test
    @DisplayName("listDoneByAssignee tenantId 为 null 时默认 1L")
    void testListDoneByAssigneeDefaultTenant() {
        service.listDoneByAssignee("1001", null);
        verify(taskMapper).selectDoneByAssignee("1001", 1L);
    }

    // ============== toView ==============

    @Test
    @DisplayName("toView 输入 null 返回 null")
    void testToViewNull() {
        assertThat(service.toView(null)).isNull();
    }

    @Test
    @DisplayName("toView 转换所有字段")
    void testToView() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setNodeCode("t1");
        task.setNodeName("审批");
        task.setNodeType(1);
        task.setAssigneeType("USER");
        task.setAssigneeId("1001");
        task.setAssigneeName("张三");
        task.setPerformType("OR");
        task.setTaskStatus("PENDING");
        task.setComment("备注");
        task.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        task.setClaimAt(LocalDateTime.of(2026, 1, 1, 1, 0));
        task.setFinishAt(LocalDateTime.of(2026, 1, 1, 2, 0));
        task.setDurationMs(3600000L);
        task.setDueAt(LocalDateTime.of(2026, 1, 2, 0, 0));

        var view = service.toView(task);
        assertThat(view).isNotNull();
        assertThat(view.getId()).isEqualTo(1L);
        assertThat(view.getNodeCode()).isEqualTo("t1");
        assertThat(view.getAssigneeId()).isEqualTo("1001");
        assertThat(view.getTaskStatus()).isEqualTo("PENDING");
        assertThat(view.getComment()).isEqualTo("备注");
        assertThat(view.getCreateAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(view.getDurationMs()).isEqualTo(3600000L);
    }

    // ============== 工具方法 ==============

    private FlowInstanceDO simpleInstance(Long id) {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(id);
        ins.setFlowCode("f1");
        ins.setDefinitionId(1L);
        ins.setBusinessType("initiation");
        ins.setBusinessId("100");
        ins.setFlowName("测试流程");
        ins.setTitle("标题");
        ins.setProviderTraceId("trace");
        ins.setTenantId(1L);
        ins.setFlowStatus("RUNNING");
        return ins;
    }

    private FlowTaskDO baseTask() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setInstanceId(10L);
        task.setNodeCode("t1");
        task.setNodeName("审批");
        task.setNodeType(FlowNodeType.APPROVAL.getCode());
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("1001");
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setPerformType("OR");
        task.setApproveCount(1);
        task.setApproveFinished(0);
        task.setBusinessType("initiation");
        task.setBusinessId("100");
        task.setFlowName("测试");
        task.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        task.setTenantId(1L);
        return task;
    }
}
