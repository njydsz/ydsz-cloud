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
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowDelegateLogMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskCompleteServiceImpl 单元测试 — GAP-P2-9 自由流跳转
 *
 * <p>覆盖 {@link FlowTaskCompleteServiceImpl#jump} 方法的核心场景：
 * <ul>
 *   <li>基础校验：任务已完成/目标节点为空/实例不存在/目标节点不存在</li>
 *   <li>GAP-P2-9 节点级 freeJump 白名单校验：
 *       action=JUMP 且目标节点 ext.freeJump 未开启时拒绝跳转</li>
 *   <li>向后兼容：action != JUMP（管理员强制跳转）即使 freeJump 未开启也放行</li>
 *   <li>显式办理人：targetAssignees 非空时透传给 createTask</li>
 *   <li>完整流程：freeJump=true 时完成当前任务 + 创建目标任务 + 触发事件</li>
 * </ul>
 *
 * <p>完整流程测试使用 Mockito {@link spy} 桩住 {@code createTask} 避免触发复杂的办理人解析逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
class FlowTaskCompleteServiceImplTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
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

    private static final Long TASK_ID = 700L;
    private static final Long INSTANCE_ID = 2001L;
    private static final Long DEFINITION_ID = 300L;
    private static final Long TENANT_ID = 1L;
    private static final String SOURCE_NODE = "node_approval_1";
    private static final String TARGET_NODE = "node_approval_2";
    private static final String TARGET_NODE_NAME = "审批节点2";
    private static final Long OPERATOR_ID = 100L;

    // ==================== 基础校验 ====================

    @Test
    @DisplayName("jump - 任务已完成时抛 BAD_REQUEST")
    void jumpShouldThrowWhenTaskFinished() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.COMPLETED);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, null);

        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });

        verify(nodeMapper, never()).selectByCode(anyLong(), anyString());
    }

    @Test
    @DisplayName("jump - targetNodeCode 为空时抛 BAD_REQUEST")
    void jumpShouldThrowWhenTargetNodeCodeEmpty() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);

        FlowTaskOperateDTO dto = buildJumpDto(null, null);

        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });

        verify(instanceMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("jump - 实例不存在时抛 NOT_FOUND")
    void jumpShouldThrowWhenInstanceNotFound() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(null);

        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, null);

        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });

        verify(nodeMapper, never()).selectByCode(anyLong(), anyString());
    }

    @Test
    @DisplayName("jump - 目标节点不存在时抛 NOT_FOUND")
    void jumpShouldThrowWhenTargetNodeNotFound() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(buildInstance());
        when(nodeMapper.selectByCode(DEFINITION_ID, TARGET_NODE)).thenReturn(null);

        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, null);

        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });
    }

    // ==================== GAP-P2-9 节点级 freeJump 白名单校验 ====================

    @Test
    @DisplayName("GAP-P2-9 jump - action=JUMP 且目标节点未开启 freeJump 时抛 BAD_REQUEST")
    void jumpShouldThrowWhenFreeJumpNotEnabledAndActionIsJump() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(buildInstance());
        // 目标节点 ext 无 freeJump 配置
        when(nodeMapper.selectByCode(DEFINITION_ID, TARGET_NODE))
                .thenReturn(buildTargetNode(null));

        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, null);
        dto.setAction("JUMP");

        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                    assertThat(biz.getMessage()).contains(TARGET_NODE);
                });

        // 不应执行任何跳转副作用
        verify(taskMapper, never()).completeTask(anyLong(), anyString(), anyString(),
                any(), any());
        verify(taskMapper, never()).cancelByInstance(anyLong(), anyString());
    }

    @Test
    @DisplayName("GAP-P2-9 jump - action=JUMP 且 ext.freeJump=false 时抛 BAD_REQUEST")
    void jumpShouldThrowWhenFreeJumpExplicitlyFalse() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(buildInstance());
        when(nodeMapper.selectByCode(DEFINITION_ID, TARGET_NODE))
                .thenReturn(buildTargetNode("{\"freeJump\":false}"));

        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, null);
        dto.setAction("JUMP");

        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });
    }

    @Test
    @DisplayName("GAP-P2-9 jump - ext.freeJump=\"true\"（字符串）时允许跳转")
    void jumpShouldPassWhenFreeJumpIsStringTrue() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(buildInstance());
        when(nodeMapper.selectByCode(DEFINITION_ID, TARGET_NODE))
                .thenReturn(buildTargetNode("{\"freeJump\":\"true\"}"));

        // 用 spy 桩住 createTask 避免触发真实办理人解析逻辑
        FlowTaskCompleteServiceImpl spy = spy(service);
        doReturn(800L).when(spy).createTask(eq(INSTANCE_ID), any(FlowNodeDO.class),
                any(), any());

        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, null);
        dto.setAction("JUMP");

        spy.jump(dto);

        verify(taskMapper).completeTask(eq(TASK_ID), eq(FlowTaskStatus.COMPLETED.name()),
                anyString(), any(), any());
        verify(taskMapper).cancelByInstance(INSTANCE_ID, FlowTaskStatus.CANCELLED.name());
        verify(support).audit(any(FlowRunTaskDO.class), eq("JUMP"), eq(OPERATOR_ID),
                eq(null), anyString());
    }

    @Test
    @DisplayName("GAP-P2-9 jump - ext.freeJump=true（布尔）时允许跳转 + 透传显式办理人")
    void jumpShouldPassWhenFreeJumpIsBooleanTrueAndPassThroughAssignees() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(buildInstance());
        when(nodeMapper.selectByCode(DEFINITION_ID, TARGET_NODE))
                .thenReturn(buildTargetNode("{\"freeJump\":true}"));

        FlowTaskCompleteServiceImpl spy = spy(service);
        doReturn(801L).when(spy).createTask(eq(INSTANCE_ID), any(FlowNodeDO.class),
                any(), any());

        List<String> explicitAssignees = List.of("1001", "1002");
        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, explicitAssignees);
        dto.setAction("JUMP");

        spy.jump(dto);

        // 验证 createTask 被调用且 explicitAssignees 透传
        verify(spy).createTask(eq(INSTANCE_ID), any(FlowNodeDO.class), any(),
                org.mockito.ArgumentMatchers.argThat(
                        arg -> arg != null && arg.size() == 2
                                && "1001".equals(arg.get(0))
                                && "1002".equals(arg.get(1))));
        verify(support).publishWorkflowEvent(eq("TASK_JUMPED"), eq(INSTANCE_ID), eq(TASK_ID));
    }

    @Test
    @DisplayName("GAP-P2-9 向后兼容 - action != JUMP（管理员强制跳转）即使 freeJump 未开启也放行")
    void jumpShouldPassWhenActionNotJumpWithoutFreeJumpWhitelist() {
        FlowRunTaskDO task = buildTask(FlowTaskStatus.PENDING);
        when(support.getTaskOrThrow(TASK_ID)).thenReturn(task);
        when(instanceMapper.selectById(INSTANCE_ID)).thenReturn(buildInstance());
        // 目标节点未开启 freeJump
        when(nodeMapper.selectByCode(DEFINITION_ID, TARGET_NODE))
                .thenReturn(buildTargetNode(null));

        FlowTaskCompleteServiceImpl spy = spy(service);
        doReturn(802L).when(spy).createTask(eq(INSTANCE_ID), any(FlowNodeDO.class),
                any(), any());

        // action 为 null（向后兼容 P2-25 管理员强制跳转场景）
        FlowTaskOperateDTO dto = buildJumpDto(TARGET_NODE, null);

        spy.jump(dto);

        verify(taskMapper).completeTask(eq(TASK_ID), eq(FlowTaskStatus.COMPLETED.name()),
                anyString(), any(), any());
        verify(support).audit(any(FlowRunTaskDO.class), eq("JUMP"), eq(OPERATOR_ID),
                eq(null), anyString());
    }

    // ==================== 辅助方法 ====================

    private FlowRunTaskDO buildTask(FlowTaskStatus status) {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId(TASK_ID);
        task.setInstanceId(INSTANCE_ID);
        task.setDefinitionId(DEFINITION_ID);
        task.setNodeCode(SOURCE_NODE);
        task.setTaskStatus(status.name());
        task.setTenantId(TENANT_ID);
        return task;
    }

    private FlowInstanceDO buildInstance() {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(INSTANCE_ID);
        instance.setDefinitionId(DEFINITION_ID);
        instance.setFlowCode("leave_flow");
        instance.setFlowName("请假流程");
        instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        instance.setTenantId(TENANT_ID);
        return instance;
    }

    private FlowNodeDO buildTargetNode(String extJson) {
        FlowNodeDO node = new FlowNodeDO();
        node.setId(400L);
        node.setDefinitionId(DEFINITION_ID);
        node.setNodeCode(TARGET_NODE);
        node.setNodeName(TARGET_NODE_NAME);
        node.setExt(extJson);
        return node;
    }

    private FlowTaskOperateDTO buildJumpDto(String targetNodeCode, List<String> targetAssignees) {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(TASK_ID);
        dto.setUserId(OPERATOR_ID);
        dto.setUserName("操作人");
        dto.setTargetNodeCode(targetNodeCode);
        dto.setTargetAssignees(targetAssignees);
        dto.setComment("自由流跳转测试");
        return dto;
    }
}
