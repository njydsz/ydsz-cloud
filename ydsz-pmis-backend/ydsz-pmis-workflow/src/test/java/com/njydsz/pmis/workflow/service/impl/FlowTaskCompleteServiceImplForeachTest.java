package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowAssigneeResolver;
import com.njydsz.pmis.workflow.engine.FlowServiceNodeExecutor;
import com.njydsz.pmis.workflow.engine.FlowUrgeLimiter;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.entity.FlowUserDO;
import com.njydsz.pmis.workflow.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowDelegateLogMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowUserMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.FlowDelegateAuthService;
import com.njydsz.pmis.workflow.service.FlowEventSubscriptionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowSlaService;
import com.njydsz.pmis.workflow.service.FlowTodoCountPushService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskCompleteServiceImpl 单元测试 — GAP-P2-10 FOREACH 循环节点
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>createTask FOREACH 分支：集合 3 元素 → 创建 3 条独立 task + 3 条 FlowUserDO</li>
 *   <li>createTask FOREACH 集合为空 + AUTO_PASS → 自动完成 + 推进</li>
 *   <li>doForeachPass 部分完成（pending > 0）→ 不推进</li>
 *   <li>doForeachPass 全部完成（pending = 0）→ 推进到下一节点</li>
 *   <li>FOREACH task 字段完整性：performType=FOREACH_PARALLEL / iterVar / approveCount=1</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
class FlowTaskCompleteServiceImplForeachTest {

    @Mock
    private FlowTaskMapper taskMapper;
    @Mock
    private FlowInstanceMapper instanceMapper;
    @Mock
    private FlowHisTaskMapper hisTaskMapper;
    @Mock
    private FlowInstanceService instanceService;
    @Mock
    private FlowAdvancer advancer;
    @Mock
    private FlowVariableStrategy variableStrategy;
    @Mock
    private FlowUserMapper userMapper;
    @Mock
    private FlowNodeMapper nodeMapper;
    @Mock
    private FlowAssigneeResolver assigneeResolver;
    @Mock
    private FlowDelegateAuthService delegateAuthService;
    @Mock
    private FlowDelegateLogMapper delegateLogMapper;
    @Mock
    private FlowSlaService slaService;
    @Mock
    private FlowTodoCountPushService todoCountPushService;
    @Mock
    private FlowMetrics flowMetrics;
    @Mock
    private FlowUrgeLimiter urgeLimiter;
    @Mock
    private FlowTaskSupport support;
    @Mock
    private FlowServiceNodeExecutor serviceNodeExecutor;
    @Mock
    private FlowEventSubscriptionService eventSubscriptionService;

    @InjectMocks
    private FlowTaskCompleteServiceImpl service;

    private static final Long INSTANCE_ID = 3001L;
    private static final Long DEFINITION_ID = 400L;
    private static final Long TENANT_ID = 1L;
    private static final String NODE_CODE = "node_foreach_1";
    private static final String NODE_NAME = "循环节点1";
    private static final Long OPERATOR_ID = 100L;

    // ==================== createTask FOREACH 分支 ====================

    @Test
    @DisplayName("GAP-P2-10 createTask FOREACH - 集合 3 元素创建 3 条独立 task + 3 条 FlowUserDO")
    void createForeachTaskShouldCreateIndependentTasksForEachElement() {
        FlowInstanceDO instance = buildInstance();
        FlowNodeDO node = buildForeachNode(
                "{\"collection\":\"${assignees}\",\"elementVariable\":\"assignee\"}");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        // expandAssignees 读取 ext.collection → 变量 assignees → 展开
        Map<String, Object> variables = Map.of("assignees", List.of("1001", "1002", "1003"));

        service.createTask(INSTANCE_ID, node, variables);

        // 验证插入 3 条 task（每个集合元素一条独立 task）
        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper, times(3)).insert(taskCaptor.capture());
        List<FlowTaskDO> insertedTasks = taskCaptor.getAllValues();
        assertThat(insertedTasks).hasSize(3);
        // 验证每条 task 字段完整性
        for (FlowTaskDO insertedTask : insertedTasks) {
            assertThat(insertedTask.getPerformType()).isEqualTo(FlowPerformType.FOREACH_PARALLEL.name());
            assertThat(insertedTask.getApproveCount()).isEqualTo(1);
            assertThat(insertedTask.getApproveFinished()).isZero();
            assertThat(insertedTask.getTaskStatus()).isEqualTo(FlowTaskStatus.PENDING.name());
            assertThat(insertedTask.getAssigneeType()).isEqualTo(FlowAssigneeType.USER.name());
            assertThat(insertedTask.getIterVar()).isIn("1001", "1002", "1003");
            assertThat(insertedTask.getNodeType()).isEqualTo(FlowNodeType.FOREACH.getCode());
        }
        // 验证 iterVar 覆盖全部 3 个元素
        assertThat(insertedTasks).extracting(FlowTaskDO::getIterVar)
                .containsExactlyInAnyOrder("1001", "1002", "1003");
        // 验证写入 3 条 FlowUserDO
        verify(userMapper, times(3)).insert(any(FlowUserDO.class));
    }

    @Test
    @DisplayName("GAP-P2-10 createTask FOREACH - 集合为空 + AUTO_PASS 自动完成 + 推进")
    void createForeachTaskShouldAutoPassWhenCollectionEmpty() {
        FlowInstanceDO instance = buildInstance();
        FlowNodeDO node = buildForeachNode("{\"emptyStrategy\":\"AUTO_PASS\"}");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        // advanceAfterAutoPass 内部调用 advancer.advance — 桩住返回空列表（→ instanceService.complete）
        when(advancer.advance(eq(instance), eq(NODE_CODE), eq("PASS"), eq(null), any()))
                .thenReturn(Collections.emptyList());

        service.createTask(INSTANCE_ID, node, Collections.emptyMap());

        // 验证插入 1 条自动完成的 task
        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO autoTask = taskCaptor.getValue();
        assertThat(autoTask.getTaskStatus()).isEqualTo(FlowTaskStatus.COMPLETED.name());
        assertThat(autoTask.getAssigneeName()).isEqualTo("SYSTEM_AUTO_PASS");

        // 验证推进到下一节点（advanceAfterAutoPass 调用）
        verify(advancer).advance(eq(instance), eq(NODE_CODE), eq("PASS"), eq(null), any());
        verify(support).audit(any(FlowTaskDO.class), eq("FOREACH_AUTO_PASS"),
                eq(0L), eq(null), anyString());
    }

    @Test
    @DisplayName("GAP-P2-10 createTask FOREACH - 集合为空 + 无 AUTO_PASS 策略回退到管理员")
    void createForeachTaskShouldFallbackToAdminWhenCollectionEmptyAndNoAutoPass() {
        FlowInstanceDO instance = buildInstance();
        FlowNodeDO node = buildForeachNode("{\"emptyStrategy\":\"TRANSFER_ADMIN\"}");
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);

        service.createTask(INSTANCE_ID, node, Collections.emptyMap());

        // 验证插入 1 条 task（默认管理员 ID="1"）
        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO task = taskCaptor.getValue();
        assertThat(task.getAssigneeId()).isEqualTo("1");
        assertThat(task.getTaskStatus()).isEqualTo(FlowTaskStatus.PENDING.name());
        // 不应调用 advanceAfterAutoPass
        verify(advancer, never()).advance(any(), anyString(), anyString(), any(), any());
    }

    // ==================== doForeachPass 完成聚合 ====================

    @Test
    @DisplayName("GAP-P2-10 doForeachPass 部分完成 - pending > 0 不推进")
    void foreachPassShouldNotAdvanceWhenPendingGreaterThanZero() {
        FlowTaskDO task = buildForeachTask(700L, "1001");
        FlowInstanceDO instance = buildInstance();
        when(support.getTaskOrThrow(700L)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(taskMapper.countPendingByNode(INSTANCE_ID, NODE_CODE)).thenReturn(2); // 还有 2 条待办
        // resolveCompletionCondition 查 node，返回 null（无 completionCondition 配置）
        when(nodeMapper.selectByCode(DEFINITION_ID, NODE_CODE)).thenReturn(buildForeachNode(null));

        service.pass(buildPassDto(task));

        // 验证当前 task 完成
        verify(taskMapper).completeTask(eq(700L), eq(FlowTaskStatus.COMPLETED.name()),
                anyString(), any(), any());
        // 验证未推进
        verify(advancer, never()).advance(any(), anyString(), anyString(), any(), any());
        verify(support).audit(any(FlowTaskDO.class), eq("FOREACH_PASS"),
                eq(OPERATOR_ID), eq(null), anyString(), any());
    }

    @Test
    @DisplayName("GAP-P2-10 doForeachPass 全部完成 - pending = 0 推进到下一节点")
    void foreachPassShouldAdvanceWhenAllCompleted() {
        FlowTaskDO task = buildForeachTask(701L, "1003");
        FlowInstanceDO instance = buildInstance();
        when(support.getTaskOrThrow(701L)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        when(taskMapper.countPendingByNode(INSTANCE_ID, NODE_CODE)).thenReturn(0); // 全部完成
        when(nodeMapper.selectByCode(DEFINITION_ID, NODE_CODE)).thenReturn(buildForeachNode(null));
        List<FlowNodeDO> nextNodes = List.of(buildForeachNode(null));
        when(advancer.advance(eq(instance), eq(NODE_CODE), eq("PASS"), eq(null), any()))
                .thenReturn(nextNodes);

        service.pass(buildPassDto(task));

        // 验证推进
        verify(advancer).advance(eq(instance), eq(NODE_CODE), eq("PASS"), eq(null), any());
        verify(instanceService).generateTasksForNodes(eq(INSTANCE_ID), eq(nextNodes), any());
        verify(support).audit(any(FlowTaskDO.class), eq("FOREACH_PASS_ALL"),
                eq(OPERATOR_ID), eq(null), anyString(), any());
    }

    @Test
    @DisplayName("GAP-P2-10 doForeachPass completionCondition 提前完成 - 跳过剩余 PENDING")
    void foreachPassShouldSkipRemainingWhenCompletionConditionMet() {
        FlowTaskDO task = buildForeachTask(702L, "1001");
        FlowInstanceDO instance = buildInstance();
        when(support.getTaskOrThrow(702L)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(instance);
        // 配置 completionCondition: nrOfCompletedInstances >= 1（第 1 个完成即推进）
        FlowNodeDO node = buildForeachNode("{\"completionCondition\":\"nrOfCompletedInstances >= 1\"}");
        when(nodeMapper.selectByCode(DEFINITION_ID, NODE_CODE)).thenReturn(node);
        // 还有 2 条 PENDING
        when(taskMapper.countPendingByNode(INSTANCE_ID, NODE_CODE)).thenReturn(2);
        // selectCount 返回总数 3
        when(taskMapper.selectCount(any())).thenReturn(3L);
        // variableStrategy.evaluate 返回 true（条件满足）
        when(variableStrategy.evaluate(anyString(), any())).thenReturn(true);
        List<FlowNodeDO> nextNodes = List.of(buildForeachNode(null));
        when(advancer.advance(eq(instance), eq(NODE_CODE), eq("PASS"), eq(null), any()))
                .thenReturn(nextNodes);

        service.pass(buildPassDto(task));

        // 验证跳过剩余 PENDING task
        verify(taskMapper).skipByNode(INSTANCE_ID, NODE_CODE, FlowTaskStatus.SKIPPED.name());
        // 验证推进
        verify(advancer).advance(eq(instance), eq(NODE_CODE), eq("PASS"), eq(null), any());
        verify(support).audit(any(FlowTaskDO.class), eq("FOREACH_PASS_ALL"),
                eq(OPERATOR_ID), eq(null), anyString(), any());
    }

    @Test
    @DisplayName("GAP-P2-10 doForeachPass 任务已完成时抛 BAD_REQUEST")
    void foreachPassShouldThrowWhenTaskFinished() {
        FlowTaskDO task = buildForeachTask(703L, "1001");
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(support.getTaskOrThrow(703L)).thenReturn(task);

        assertThatThrownBy(() -> service.pass(buildPassDto(task)))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });

        verify(taskMapper, never()).completeTask(anyLong(), anyString(), anyString(),
                any(), any());
    }

    // ==================== 辅助方法 ====================

    private FlowInstanceDO buildInstance() {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(INSTANCE_ID);
        instance.setDefinitionId(DEFINITION_ID);
        instance.setFlowCode("foreach_flow");
        instance.setFlowName("循环流程");
        instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        instance.setTenantId(TENANT_ID);
        return instance;
    }

    private FlowNodeDO buildForeachNode(String extJson) {
        FlowNodeDO node = new FlowNodeDO();
        node.setId(500L);
        node.setDefinitionId(DEFINITION_ID);
        node.setNodeCode(NODE_CODE);
        node.setNodeName(NODE_NAME);
        node.setNodeType(FlowNodeType.FOREACH.getCode());
        node.setExt(extJson);
        return node;
    }

    private FlowTaskDO buildForeachTask(Long taskId, String iterVar) {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(taskId);
        task.setInstanceId(INSTANCE_ID);
        task.setDefinitionId(DEFINITION_ID);
        task.setNodeCode(NODE_CODE);
        task.setNodeName(NODE_NAME);
        task.setNodeType(FlowNodeType.FOREACH.getCode());
        task.setPerformType(FlowPerformType.FOREACH_PARALLEL.name());
        task.setApproveCount(1);
        task.setApproveFinished(0);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId(iterVar);
        task.setAssigneeName("USER:" + iterVar);
        task.setTenantId(TENANT_ID);
        task.setIterVar(iterVar);
        return task;
    }

    private FlowTaskOperateDTO buildPassDto(FlowTaskDO task) {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(task.getId());
        dto.setUserId(OPERATOR_ID);
        dto.setUserName("操作人");
        dto.setComment("FOREACH 通过测试");
        return dto;
    }
}
