package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowAssigneeResolver;
import com.njydsz.pmis.workflow.flow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowUserDO;
import com.njydsz.pmis.workflow.flow.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskServiceImpl 单元测试
 *
 * <p>1.1.0 覆盖：createTask / claim / pass (含会签) / reject / transfer / delegate (含委派回归) /
 * countersignBefore / countersignAfter / urge / cancelByInstance / listPendingByInstance /
 * listTodoByAssignee / listTodoByUser / listDoneByAssignee (历史表) / toView。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FlowTaskServiceImpl 单元测试")
class FlowTaskServiceImplTest {

    private FlowTaskMapper taskMapper;
    private FlowHisTaskMapper hisTaskMapper;
    private FlowInstanceMapper instanceMapper;
    private FlowInstanceServiceImpl instanceService;
    private FlowAdvancer advancer;
    private FlowVariableStrategy variableStrategy;
    private FlowUserMapper userMapper;
    private FlowAuditLogMapper auditLogMapper;
    private FlowNodeMapper nodeMapper;
    private FlowAssigneeResolver assigneeResolver;
    private List<FlowEventListener> eventListeners;
    private ApplicationEventPublisher eventPublisher;
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
        userMapper = mock(FlowUserMapper.class);
        auditLogMapper = mock(FlowAuditLogMapper.class);
        nodeMapper = mock(FlowNodeMapper.class);
        assigneeResolver = mock(FlowAssigneeResolver.class);
        eventListeners = new ArrayList<>();
        // P2-35: 注入 ApplicationEventPublisher mock
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new FlowTaskServiceImpl(taskMapper, hisTaskMapper, instanceMapper,
                instanceService, advancer, variableStrategy,
                userMapper, auditLogMapper, nodeMapper, assigneeResolver, eventListeners,
                eventPublisher);
    }

    // ============== createTask ==============

    @Test
    @DisplayName("getById P2-20: 委托给 taskMapper.selectById")
    void testGetTaskDetail() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setNodeCode("t1");
        task.setNodeName("审批");
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskDO result = service.getById(1L);
        assertThat(result).isSameAs(task);
        verify(taskMapper).selectById(1L);
    }

    @Test
    @DisplayName("getById P2-20: taskId 为 null 时返回 null")
    void testGetTaskDetailNullId() {
        assertThat(service.getById(null)).isNull();
        verify(taskMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("getById P2-20: 任务不存在返回 null")
    void testGetTaskDetailNotFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThat(service.getById(99L)).isNull();
    }

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
        // P2-15: user: 前缀在 expandAssignees 中直接展开，不走 SPI / fallback
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
        // P2-15: user: 直接展开，不走 fallback，不调用 updateById
        verify(taskMapper, never()).updateById((FlowTaskDO) any());
        verify(userMapper, times(1)).insert((FlowUserDO) any());
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
        when(assigneeResolver.expandUsers(eq("role:hr"), any())).thenReturn(Collections.emptyList());
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
        when(assigneeResolver.expandUsers(eq("dept:10"), any())).thenReturn(Collections.emptyList());
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
        when(assigneeResolver.expandUsers(eq("${initiatorId}"), any())).thenReturn(Collections.emptyList());
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

    @Test
    @DisplayName("createTask P0-4: ROLE 展开 → 多用户写入 pmis_flow_user")
    void testCreateTaskRoleExpanded() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("role:hr"), any())).thenReturn("role:hr");
        // 模拟 resolver 展开 role:hr → [1001L, 1002L, 1003L]
        when(assigneeResolver.expandUsers(eq("role:hr"), any()))
                .thenReturn(List.of(1001L, 1002L, 1003L));
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(88L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("会签审批");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("role:hr");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.USER.name());
        assertThat(saved.getAssigneeId()).isEqualTo("1001");  // 第一个用户
        assertThat(saved.getApproveCount()).isEqualTo(3);  // 总人数
        // 应该写入 3 条 pmis_flow_user
        verify(userMapper, times(3)).insert((FlowUserDO) any());
    }

    @Test
    @DisplayName("createTask P2-15: user:1,user:2,user:3 多人逗号分隔 → 3 个用户写入 pmis_flow_user")
    void testCreateTaskMultipleCandidateUsers() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        // resolveAssignee 原样返回（无变量替换）
        when(variableStrategy.resolveAssignee(eq("user:1,user:2,user:3"), any()))
                .thenReturn("user:1,user:2,user:3");
        // user: 前缀不需要 SPI 展开
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(89L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("多人会签");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("user:1,user:2,user:3");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.USER.name());
        assertThat(saved.getAssigneeId()).isEqualTo("1");  // 第一个用户
        assertThat(saved.getApproveCount()).isEqualTo(3);
        verify(userMapper, times(3)).insert((FlowUserDO) any());
    }

    @Test
    @DisplayName("createTask P2-15: user:1,role:hr 混合 → user 直接展开 + role SPI 展开，去重合并")
    void testCreateTaskMixedCandidateUsersAndRoles() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("user:1,role:hr"), any()))
                .thenReturn("user:1,role:hr");
        // role:hr 展开 → [1L, 2L]（注意 1L 与 user:1 重复，应被去重）
        when(assigneeResolver.expandUsers(eq("role:hr"), any()))
                .thenReturn(List.of(1L, 2L));
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(90L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("混合会签");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("user:1,role:hr");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        assertThat(saved.getAssigneeId()).isEqualTo("1");  // user:1 第一个
        // 去重后：user:1 + role:hr→[1,2] = {1,2} 共 2 个用户
        assertThat(saved.getApproveCount()).isEqualTo(2);
        verify(userMapper, times(2)).insert((FlowUserDO) any());
    }

    @Test
    @DisplayName("createTask P2-15: 逗号分隔 permissionFlag 但无法展开 → fallback 取第一段")
    void testCreateTaskCommaSeparatedFallback() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        // P2-15: user: 前缀会被直接展开，因此用 role: 前缀 + SPI 返回空来测试真正的 fallback 路径
        when(variableStrategy.resolveAssignee(eq("role:unknown1,role:unknown2"), any()))
                .thenReturn("role:unknown1,role:unknown2");
        when(assigneeResolver.expandUsers(any(), any()))
                .thenReturn(java.util.Collections.emptyList());
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(91L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("Fallback");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("role:unknown1,role:unknown2");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        // fallback 路径：取第一段 role:unknown1 作为主办理人
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.ROLE.name());
        assertThat(saved.getAssigneeId()).isEqualTo("unknown1");
        // approveCount 默认 1（fallback 路径不展开）
        assertThat(saved.getApproveCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("createTask P2-19: leader:1001 通过 SPI 展开为多人")
    void testCreateTaskLeaderExpanded() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("leader:1001"), any()))
                .thenReturn("leader:1001");
        // leader:1001 → 直属上级为 [2001L]
        when(assigneeResolver.expandUsers(eq("leader:1001"), any()))
                .thenReturn(List.of(2001L));
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(92L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("上级审批");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("leader:1001");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.USER.name());
        assertThat(saved.getAssigneeId()).isEqualTo("2001");  // 展开后的上级 userId
        assertThat(saved.getApproveCount()).isEqualTo(1);
        verify(userMapper, times(1)).insert((FlowUserDO) any());
    }

    @Test
    @DisplayName("createTask P2-19: position:PM 通过 SPI 展开为多人会签")
    void testCreateTaskPositionExpanded() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("position:PM"), any()))
                .thenReturn("position:PM");
        // position:PM → 岗位为 PM 的所有用户 [3001L, 3002L]
        when(assigneeResolver.expandUsers(eq("position:PM"), any()))
                .thenReturn(List.of(3001L, 3002L));
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(93L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("PM 会签");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("position:PM");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.USER.name());
        assertThat(saved.getAssigneeId()).isEqualTo("3001");  // 第一个 PM
        assertThat(saved.getApproveCount()).isEqualTo(2);  // 两个 PM
        verify(userMapper, times(2)).insert((FlowUserDO) any());
    }

    @Test
    @DisplayName("createTask P2-19: leader:1001 SPI 不展开 → fallback assigneeType=LEADER")
    void testCreateTaskLeaderSingleFallback() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("leader:1001"), any()))
                .thenReturn("leader:1001");
        // SPI 不展开（resolver 返回空），走 fallback 单人路径
        when(assigneeResolver.expandUsers(eq("leader:1001"), any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(94L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("上级审批");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("leader:1001");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        verify(taskMapper).updateById(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        // fallback 路径：保留 leader: 类型，assigneeId 为 1001
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.LEADER.name());
        assertThat(saved.getAssigneeId()).isEqualTo("1001");
    }

    @Test
    @DisplayName("createTask P2-19: position:PM SPI 不展开 → fallback assigneeType=POSITION")
    void testCreateTaskPositionSingleFallback() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("position:PM"), any()))
                .thenReturn("position:PM");
        when(assigneeResolver.expandUsers(eq("position:PM"), any()))
                .thenReturn(Collections.emptyList());
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(95L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("岗位审批");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("position:PM");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        verify(taskMapper).updateById(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.POSITION.name());
        assertThat(saved.getAssigneeId()).isEqualTo("PM");
    }

    @Test
    @DisplayName("createTask P2-19: leader:/position:/user: 混合展开 + 去重")
    void testCreateTaskMixedLeaderPositionUser() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("leader:1001,position:PM,user:5001"), any()))
                .thenReturn("leader:1001,position:PM,user:5001");
        // leader:1001 → [5001L]（与 user:5001 重复）
        when(assigneeResolver.expandUsers(eq("leader:1001"), any()))
                .thenReturn(List.of(5001L));
        // position:PM → [6001L, 6002L]
        when(assigneeResolver.expandUsers(eq("position:PM"), any()))
                .thenReturn(List.of(6001L, 6002L));
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(96L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("混合找人会签");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("leader:1001,position:PM,user:5001");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        // 去重合并：user:5001 + leader:1001→[5001]（去重） + position:PM→[6001,6002] = {5001,6001,6002}
        assertThat(saved.getAssigneeId()).isEqualTo("5001");  // user:5001 第一个
        assertThat(saved.getApproveCount()).isEqualTo(3);  // 去重后 3 人
        verify(userMapper, times(3)).insert((FlowUserDO) any());
    }

    @Test
    @DisplayName("createTask P2-38: self_select:approvers 不在展开阶段处理 → fallback SELF_SELECT")
    void testCreateTaskSelfSelect() {
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("self_select:approvers"), any()))
                .thenReturn("self_select:approvers");
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(97L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("发起人自选审批人");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("self_select:approvers");

        service.createTask(10L, node, Map.of());

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        verify(taskMapper).updateById(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        // P2-38: self_select: 走 fallback，assigneeType=SELF_SELECT，assigneeId=变量名 approvers
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.SELF_SELECT.name());
        assertThat(saved.getAssigneeId()).isEqualTo("approvers");
        // 不应该写入 pmis_flow_user（未展开）
        verify(userMapper, never()).insert((FlowUserDO) any());
    }

    @Test
    @DisplayName("createTask P2-39: multi_leader:3 通过 SPI expandMultiLeader 展开为多级上级")
    void testCreateTaskMultiLeaderExpanded() {
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setInitiatorId(1001L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(variableStrategy.resolveAssignee(eq("multi_leader:3"), any()))
                .thenReturn("multi_leader:3");
        // multi_leader:3 从发起人 1001 开始展开 3 级上级 → [2001L, 2002L, 2003L]
        when(assigneeResolver.expandMultiLeader(eq(1001L), eq(3), any()))
                .thenReturn(List.of(2001L, 2002L, 2003L));
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(98L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("连续多级主管审批");
        node.setNodeType(FlowNodeType.APPROVAL.getCode());
        node.setPermissionFlag("multi_leader:3");

        service.createTask(10L, node, Map.of("initiatorId", 1001L));

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        FlowTaskDO saved = taskCaptor.getValue();
        // P2-39: multi_leader 展开后 assigneeType=USER，assigneeId=第一个上级 2001
        assertThat(saved.getAssigneeType()).isEqualTo(FlowAssigneeType.USER.name());
        assertThat(saved.getAssigneeId()).isEqualTo("2001");
        assertThat(saved.getApproveCount()).isEqualTo(3);  // 3 级上级
        // 应该写入 3 条 pmis_flow_user
        verify(userMapper, times(3)).insert((FlowUserDO) any());
        // 验证 expandMultiLeader 被正确调用
        verify(assigneeResolver).expandMultiLeader(eq(1001L), eq(3), any());
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
        verify(taskMapper, atLeastOnce()).updateById((FlowTaskDO) any());
        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper, atLeastOnce()).updateById(captor.capture());
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

    @Test
    @DisplayName("pass P1-10: 委派回归 — 被委派人通过后任务回到原办理人")
    void testPassDelegateReturn() {
        FlowTaskDO task = baseTask();
        task.setTaskStatus(FlowTaskStatus.DELEGATED.name());
        task.setAssignorId(500L);
        task.setAssignorName("原办理人");
        task.setAssigneeId("600");
        task.setAssigneeName("被委派人");
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setUserId(600L);
        dto.setComment("委派人已处理");
        service.pass(dto);

        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO updated = captor.getValue();
        assertThat(updated.getAssigneeId()).isEqualTo("500");  // 回到原办理人
        assertThat(updated.getAssigneeName()).isEqualTo("原办理人");
        assertThat(updated.getAssignorId()).isNull();  // 清空 assignor
        assertThat(updated.getTaskStatus()).isEqualTo(FlowTaskStatus.CLAIMED.name());
        // 不应该推进流程
        verify(advancer, never()).advance(any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("pass P0-1: 并行会签 — 全部通过才推进")
    void testPassParallelNotAllFinished() {
        FlowTaskDO task = baseTask();
        task.setPerformType("PARALLEL");
        task.setApproveCount(3);
        task.setApproveFinished(0);  // 仅 1 人通过
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(simpleInstance(10L));

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setUserId(1001L);
        dto.setComment("同意");
        service.pass(dto);

        // 应该更新计数但不推进
        verify(taskMapper).updateApproveFinished(eq(1L), eq(1));
        verify(advancer, never()).advance(any(), anyString(), anyString(), any(), any());
        verify(taskMapper, never()).completeTask(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("pass P0-1: 并行会签 — 全部通过后推进")
    void testPassParallelAllFinished() {
        FlowTaskDO task = baseTask();
        task.setPerformType("PARALLEL");
        task.setApproveCount(2);
        task.setApproveFinished(1);  // 已 1 人通过，再 1 人 = 2，达到阈值
        when(taskMapper.selectById(1L)).thenReturn(task);
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(advancer.advance(any(), anyString(), anyString(), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setUserId(1002L);
        service.pass(dto);

        // 应该跳过剩余 + 完成 + 推进
        verify(taskMapper).skipByNode(eq(10L), eq("t1"), eq(FlowTaskStatus.SKIPPED.name()));
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.COMPLETED.name()),
                any(), any(), any());
        verify(advancer).advance(any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("pass P1-12: 票签 — 达到阈值即推进")
    void testPassVoteThreshold() {
        FlowTaskDO task = baseTask();
        task.setPerformType("VOTE");
        task.setApproveCount(5);
        task.setApproveFinished(2);  // 已 2 人通过，再 1 人 = 3，达到阈值 (5/2+1=3)
        when(taskMapper.selectById(1L)).thenReturn(task);
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(advancer.advance(any(), anyString(), anyString(), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setUserId(1003L);
        service.pass(dto);

        verify(taskMapper).skipByNode(eq(10L), eq("t1"), eq(FlowTaskStatus.SKIPPED.name()));
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.COMPLETED.name()),
                any(), any(), any());
    }

    @Test
    @DisplayName("pass P1-12: 顺序会签 — 切换到下一个办理人")
    void testPassSequentialSwitchNext() {
        FlowTaskDO task = baseTask();
        task.setPerformType("SEQUENTIAL");
        task.setApproveCount(3);
        task.setApproveFinished(0);
        when(taskMapper.selectById(1L)).thenReturn(task);
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);

        FlowUserDO nextUser = new FlowUserDO();
        nextUser.setUserId("1002");
        nextUser.setUserName("李四");
        when(userMapper.selectUnprocessedByInstanceAndNode(eq(10L), eq("t1")))
                .thenReturn(List.of(nextUser));

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setUserId(1001L);
        service.pass(dto);

        verify(taskMapper).updateAssignee(eq(1L), eq("1002"), eq("李四"),
                eq(FlowAssigneeType.USER.name()));
        verify(advancer, never()).advance(any(), anyString(), anyString(), any(), any());
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
    @DisplayName("reject P1-11: 有退回目标 — 生成新任务 + 更新当前节点")
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
    @DisplayName("delegate P1-10: 委派 — 保存原办理人 + 状态改为 DELEGATED")
    void testDelegate() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("100");
        task.setAssigneeName("原办理人");
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(300L);
        dto.setTargetUserName("被委派人");
        service.delegate(dto);

        ArgumentCaptor<FlowTaskDO> captor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        FlowTaskDO updated = captor.getValue();
        assertThat(updated.getAssigneeId()).isEqualTo("300");
        assertThat(updated.getAssigneeName()).isEqualTo("被委派人");
        assertThat(updated.getAssignorId()).isEqualTo(100L);
        assertThat(updated.getAssignorName()).isEqualTo("原办理人");
        assertThat(updated.getTaskStatus()).isEqualTo(FlowTaskStatus.DELEGATED.name());
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

    // ============== P1-7: 加签 ==============

    @Test
    @DisplayName("countersignBefore 已完成任务应抛 BAD_REQUEST")
    void testCountersignBeforeFinished() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(taskMapper.selectById(1L)).thenReturn(task);
        assertThatThrownBy(() -> {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(1L);
            service.countersignBefore(dto);
        }).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("countersignBefore 成功：写入 pmis_flow_user + approveCount+1")
    void testCountersignBeforeSuccess() {
        FlowTaskDO task = baseTask();
        task.setApproveCount(2);
        task.setApproveFinished(0);
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(888L);
        dto.setTargetUserName("加签人");
        service.countersignBefore(dto);

        ArgumentCaptor<FlowUserDO> userCaptor = ArgumentCaptor.forClass(FlowUserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        FlowUserDO saved = userCaptor.getValue();
        assertThat(saved.getTaskId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo("888");
        assertThat(saved.getUserName()).isEqualTo("加签人");

        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getApproveCount()).isEqualTo(3);  // 2+1
    }

    @Test
    @DisplayName("countersignAfter P2-29: 后加签 — 新增审批人 + 切换为顺序会签")
    void testCountersignAfter() {
        FlowTaskDO task = baseTask();
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(999L);
        dto.setTargetUserName("后加签人");
        service.countersignAfter(dto);

        verify(userMapper).insert((FlowUserDO) any());
        // P2-29: 后加签会切换 performType 为 SEQUENTIAL 并 approveCount+1
        verify(taskMapper).updateById((FlowTaskDO) any());
    }

    @Test
    @DisplayName("countersignAfter P2-29: 后加签真实实现 — 切换为顺序会签 + approveCount+1")
    void testCountersignAfter_RealImplementation() {
        FlowTaskDO task = baseTask();
        task.setPerformType("OR");
        task.setApproveCount(1);
        task.setApproveFinished(0);
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(999L);
        dto.setTargetUserName("后加签人");
        service.countersignAfter(dto);

        // 1. 新增审批人写入 pmis_flow_user
        ArgumentCaptor<FlowUserDO> userCaptor = ArgumentCaptor.forClass(FlowUserDO.class);
        verify(userMapper).insert(userCaptor.capture());
        FlowUserDO savedUser = userCaptor.getValue();
        assertThat(savedUser.getTaskId()).isEqualTo(1L);
        assertThat(savedUser.getUserId()).isEqualTo("999");
        assertThat(savedUser.getUserName()).isEqualTo("后加签人");
        assertThat(savedUser.getProcessed()).isEqualTo(0);

        // 2. 任务切换为顺序会签 + approveCount+1
        ArgumentCaptor<FlowTaskDO> taskCaptor = ArgumentCaptor.forClass(FlowTaskDO.class);
        verify(taskMapper).updateById(taskCaptor.capture());
        FlowTaskDO updated = taskCaptor.getValue();
        assertThat(updated.getPerformType()).isEqualTo("SEQUENTIAL");
        assertThat(updated.getApproveCount()).isEqualTo(2);  // 1+1
    }

    @Test
    @DisplayName("countersignAfter P2-29: 后加签后当前人 pass 会切换到加签人而非直接推进")
    void testCountersignAfter_PassSwitchesToCountersignUser() {
        // 模拟后加签后的任务状态：SEQUENTIAL, approveCount=2, approveFinished=0
        FlowTaskDO task = baseTask();
        task.setPerformType("SEQUENTIAL");
        task.setApproveCount(2);
        task.setApproveFinished(0);
        when(taskMapper.selectById(1L)).thenReturn(task);
        FlowInstanceDO ins = simpleInstance(10L);
        when(instanceMapper.selectById(10L)).thenReturn(ins);

        // 加签人在 pmis_flow_user 中未处理
        FlowUserDO countersignUser = new FlowUserDO();
        countersignUser.setUserId("999");
        countersignUser.setUserName("后加签人");
        when(userMapper.selectUnprocessedByInstanceAndNode(eq(10L), eq("t1")))
                .thenReturn(List.of(countersignUser));

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setUserId(1001L);
        dto.setComment("同意");
        service.pass(dto);

        // 当前人 pass 后：approveFinished+1（1 < 2），切换到加签人，不推进
        verify(taskMapper).updateApproveFinished(eq(1L), eq(1));
        verify(taskMapper).updateAssignee(eq(1L), eq("999"), eq("后加签人"),
                eq(FlowAssigneeType.USER.name()));
        // 不应该完成当前任务或推进
        verify(taskMapper, never()).completeTask(anyLong(), anyString(), any(), any(), any());
        verify(advancer, never()).advance(any(), anyString(), anyString(), any(), any());
    }

    // ============== P1-9: 催办 ==============

    @Test
    @DisplayName("urge 返回当前 PENDING 任务的办理人 ID 列表")
    void testUrge() {
        FlowTaskDO t1 = new FlowTaskDO();
        t1.setId(1L);
        t1.setAssigneeId("1001");
        FlowTaskDO t2 = new FlowTaskDO();
        t2.setId(2L);
        t2.setAssigneeId("1002");
        when(taskMapper.selectPendingByInstance(10L)).thenReturn(List.of(t1, t2));

        List<String> urged = service.urge(10L, 7L, "请尽快处理");
        assertThat(urged).containsExactly("1001", "1002");
        // 每个任务都应该写审计
        verify(auditLogMapper, times(2)).insert((com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO) any());
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
    @DisplayName("listTodoByUser P0-4: 多维度匹配（直接 + flow_user + ROLE + DEPT）")
    void testListTodoByUser() {
        FlowTaskDO direct = new FlowTaskDO();
        direct.setId(1L);
        direct.setTaskStatus(FlowTaskStatus.PENDING.name());
        when(taskMapper.selectTodoByAssignee(eq("1001"), eq(1L)))
                .thenReturn(List.of(direct))
                .thenReturn(Collections.emptyList());  // 第二次（ROLE 匹配）返回空
        when(userMapper.selectTaskIdsByUser(eq("1001"), eq(1L)))
                .thenReturn(Collections.emptyList());

        List<FlowTaskDO> result = service.listTodoByUser(1001L,
                List.of("hr"), List.of("10"), 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listDoneByAssignee P0-3: 走历史表 FlowHisTaskMapper")
    void testListDoneByAssigneeFromHistory() {
        FlowHisTaskDO his = new FlowHisTaskDO();
        his.setTaskId(99L);
        his.setAssigneeId("1001");
        his.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(hisTaskMapper.selectDoneByAssignee(eq("1001"), eq(1L)))
                .thenReturn(List.of(his));

        List<FlowTaskDO> result = service.listDoneByAssignee("1001", null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(99L);
        assertThat(result.get(0).getTaskStatus()).isEqualTo(FlowTaskStatus.COMPLETED.name());
        // 不应该查 task 表
        verify(taskMapper, never()).selectDoneByAssignee(anyString(), anyLong());
    }

    // ============== P2-17: 真分页 ==============

    @Test
    @DisplayName("listTodoByAssigneePage P2-17: tenantId 为 null 时取默认 1L，offset=0")
    void testListTodoByAssigneePageDefaultTenant() {
        FlowTaskDO t = new FlowTaskDO();
        t.setId(1L);
        t.setAssigneeId("1001");
        when(taskMapper.selectTodoByAssigneePage(eq("1001"), eq(1L), eq(0), eq(20)))
                .thenReturn(List.of(t));
        when(taskMapper.countTodoByAssignee(eq("1001"), eq(1L))).thenReturn(1L);

        var page = service.listTodoByAssigneePage("1001", null, 1, 20);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
        verify(taskMapper).selectTodoByAssigneePage("1001", 1L, 0, 20);
        verify(taskMapper).countTodoByAssignee("1001", 1L);
    }

    @Test
    @DisplayName("listTodoByAssigneePage P2-17: 自定义 tenantId，offset=(page-1)*size")
    void testListTodoByAssigneePageCustomTenant() {
        when(taskMapper.selectTodoByAssigneePage(eq("1001"), eq(5L), eq(20), eq(10)))
                .thenReturn(Collections.emptyList());
        when(taskMapper.countTodoByAssignee(eq("1001"), eq(5L))).thenReturn(25L);

        var page = service.listTodoByAssigneePage("1001", 5L, 3, 10);
        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isEqualTo(25L);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(10);
        // 总页数 = (25 + 10 - 1) / 10 = 3
        assertThat(page.getPages()).isEqualTo(3L);
        verify(taskMapper).selectTodoByAssigneePage("1001", 5L, 20, 10);
    }

    @Test
    @DisplayName("listTodoByAssigneePage P2-17: 非法 page/size 兜底（page<1→1, size<=0→20）")
    void testListTodoByAssigneePageInvalidPaging() {
        when(taskMapper.selectTodoByAssigneePage(any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(taskMapper.countTodoByAssignee(any(), any())).thenReturn(0L);

        var page = service.listTodoByAssigneePage("1001", 1L, -1, 0);
        // page<1 → safePage=1, size<=0 → safeSize=20, offset=0
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
        verify(taskMapper).selectTodoByAssigneePage("1001", 1L, 0, 20);
    }

    @Test
    @DisplayName("listDoneByAssigneePage P2-17: 走历史表，真分页 + 字段映射")
    void testListDoneByAssigneePageFromHistory() {
        FlowHisTaskDO his = new FlowHisTaskDO();
        his.setTaskId(88L);
        his.setInstanceId(10L);
        his.setFlowCode("f1");
        his.setNodeCode("t1");
        his.setNodeName("审批");
        his.setAssigneeId("1001");
        his.setAssigneeName("张三");
        his.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        his.setComment("同意");
        when(hisTaskMapper.selectDoneByAssigneePage(eq("1001"), eq(1L), eq(0), eq(10)))
                .thenReturn(List.of(his));
        when(hisTaskMapper.countDoneByAssignee(eq("1001"), eq(1L))).thenReturn(1L);

        var page = service.listDoneByAssigneePage("1001", null, 1, 10);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(1L);
        FlowTaskDO converted = page.getList().get(0);
        assertThat(converted.getId()).isEqualTo(88L);
        assertThat(converted.getInstanceId()).isEqualTo(10L);
        assertThat(converted.getFlowCode()).isEqualTo("f1");
        assertThat(converted.getNodeCode()).isEqualTo("t1");
        assertThat(converted.getNodeName()).isEqualTo("审批");
        assertThat(converted.getAssigneeId()).isEqualTo("1001");
        assertThat(converted.getAssigneeName()).isEqualTo("张三");
        assertThat(converted.getTaskStatus()).isEqualTo(FlowTaskStatus.COMPLETED.name());
        assertThat(converted.getComment()).isEqualTo("同意");
        verify(hisTaskMapper).selectDoneByAssigneePage("1001", 1L, 0, 10);
        verify(hisTaskMapper).countDoneByAssignee("1001", 1L);
    }

    @Test
    @DisplayName("listDoneByAssigneePage P2-17: offset=(page-1)*size 正确计算")
    void testListDoneByAssigneePageOffset() {
        when(hisTaskMapper.selectDoneByAssigneePage(eq("1001"), eq(2L), eq(30), eq(15)))
                .thenReturn(Collections.emptyList());
        when(hisTaskMapper.countDoneByAssignee(eq("1001"), eq(2L))).thenReturn(50L);

        var page = service.listDoneByAssigneePage("1001", 2L, 3, 15);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getSize()).isEqualTo(15);
        // 总页数 = (50 + 15 - 1) / 15 = 4
        assertThat(page.getPages()).isEqualTo(4L);
        verify(hisTaskMapper).selectDoneByAssigneePage("1001", 2L, 30, 15);
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

    // ============== P2-25: 自由跳转 ==============

    @Test
    @DisplayName("jump P2-25: 自由跳转 — 完成当前任务 + 取消其他 PENDING + 创建目标节点任务")
    void testJump_Success() {
        FlowTaskDO task = baseTask();
        task.setDefinitionId(1L);
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(2));
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(ins);

        FlowNodeDO targetNode = new FlowNodeDO();
        targetNode.setNodeCode("t2");
        targetNode.setNodeName("目标节点");
        targetNode.setNodeType(FlowNodeType.APPROVAL.getCode());
        targetNode.setPermissionFlag("user:1002");
        when(nodeMapper.selectByCode(eq(1L), eq("t2"))).thenReturn(targetNode);
        when(variableStrategy.resolveAssignee(eq("user:1002"), any())).thenReturn("user:1002");
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowTaskDO) inv.getArgument(0)).setId(99L);
            return 1;
        }).when(taskMapper).insert((FlowTaskDO) any());

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setUserId(1001L);
        dto.setTargetNodeCode("t2");
        dto.setComment("管理员跳转");
        service.jump(dto);

        // 1. 完成当前任务（COMPLETED）
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.COMPLETED.name()),
                eq("管理员跳转"), any(), any());
        // 2. 归档到历史表
        verify(hisTaskMapper).insert((com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO) any());
        // 3. 取消同实例其他 PENDING 任务
        verify(taskMapper).cancelByInstance(eq(10L), eq(FlowTaskStatus.CANCELLED.name()));
        // 4. 更新实例当前节点为目标节点
        verify(instanceMapper).updateStatus(eq(10L), eq("RUNNING"),
                eq("t2"), eq("目标节点"), eq(null), eq(null));
        // 5. 在目标节点创建新任务（taskMapper.insert 至少被调用一次）
        verify(taskMapper, atLeastOnce()).insert((FlowTaskDO) any());
    }

    @Test
    @DisplayName("jump P2-25: 目标节点不存在应抛 NOT_FOUND")
    void testJump_TargetNodeNotFound() {
        FlowTaskDO task = baseTask();
        task.setDefinitionId(1L);
        FlowInstanceDO ins = simpleInstance(10L);
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(nodeMapper.selectByCode(eq(1L), eq("unknown"))).thenReturn(null);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetNodeCode("unknown");
        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("目标节点不存在");
        // 不应该完成当前任务
        verify(taskMapper, never()).completeTask(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("jump P2-25: 已完成任务不可跳转")
    void testJump_AlreadyFinished() {
        FlowTaskDO task = baseTask();
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetNodeCode("t2");
        assertThatThrownBy(() -> service.jump(dto))
                .isInstanceOf(BizException.class);
        verify(taskMapper, never()).completeTask(anyLong(), anyString(), any(), any(), any());
    }

    // ============== P2-26: 批量审批 ==============

    @Test
    @DisplayName("batchPass P2-26: 全部成功 — 逐个调用 pass")
    void testBatchPass_AllSuccess() {
        FlowTaskDO task1 = baseTask();
        task1.setId(1L);
        FlowTaskDO task2 = baseTask();
        task2.setId(2L);
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(2));

        when(taskMapper.selectById(1L)).thenReturn(task1);
        when(taskMapper.selectById(2L)).thenReturn(task2);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(advancer.advance(any(), anyString(), eq("PASS"), any(), any()))
                .thenReturn(Collections.emptyList());

        service.batchPass(List.of(1L, 2L), 1001L, "批量同意");

        // 应该对每个任务调用 completeTask
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.COMPLETED.name()),
                eq("批量同意"), any(), any());
        verify(taskMapper).completeTask(eq(2L), eq(FlowTaskStatus.COMPLETED.name()),
                eq("批量同意"), any(), any());
    }

    @Test
    @DisplayName("batchPass P2-26: 部分失败 — 抛异常，后续任务不处理")
    void testBatchPass_PartialFailRollback() {
        FlowTaskDO task1 = baseTask();
        task1.setId(1L);
        FlowTaskDO task2 = baseTask();
        task2.setId(2L);
        task2.setTaskStatus(FlowTaskStatus.COMPLETED.name());  // 第二个任务已完成，pass 会抛异常

        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(2));
        when(taskMapper.selectById(1L)).thenReturn(task1);
        when(taskMapper.selectById(2L)).thenReturn(task2);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(advancer.advance(any(), anyString(), eq("PASS"), any(), any()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.batchPass(List.of(1L, 2L), 1001L, "批量同意"))
                .isInstanceOf(BizException.class);

        // 第一个任务应该被完成
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.COMPLETED.name()),
                any(), any(), any());
        // 第二个任务不应被完成（completeTask 不应针对 taskId=2 调用）
        verify(taskMapper, never()).completeTask(eq(2L), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("batchPass P2-26: 空 taskIds 抛 BAD_REQUEST")
    void testBatchPass_EmptyTaskIds() {
        assertThatThrownBy(() -> service.batchPass(Collections.emptyList(), 1001L, "同意"))
                .isInstanceOf(BizException.class);
        verify(taskMapper, never()).completeTask(anyLong(), anyString(), any(), any(), any());
    }

    // ============== P2-31: 审批耗时统计 ==============

    @Test
    @DisplayName("nodeDurationStats P2-31: 按节点统计平均耗时")
    void testNodeDurationStats() {
        Map<String, Object> stat = new HashMap<>();
        stat.put("nodeCode", "t1");
        stat.put("nodeName", "审批");
        stat.put("avgDurationMs", 3600000L);
        stat.put("count", 5L);
        when(hisTaskMapper.nodeDurationStats(eq("f1"), eq(1L))).thenReturn(List.of(stat));

        List<Map<String, Object>> result = service.nodeDurationStats("f1", 1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("nodeCode")).isEqualTo("t1");
        assertThat(result.get(0).get("nodeName")).isEqualTo("审批");
        assertThat(result.get(0).get("avgDurationMs")).isEqualTo(3600000L);
        assertThat(result.get(0).get("count")).isEqualTo(5L);
        verify(hisTaskMapper).nodeDurationStats("f1", 1L);
    }

    // ============== P2-32: 超期任务统计 ==============

    @Test
    @DisplayName("listOverdue P2-32: 查询超期任务，tenantId 为 null 时默认 1L")
    void testListOverdue() {
        FlowTaskDO t = new FlowTaskDO();
        t.setId(1L);
        t.setTaskStatus(FlowTaskStatus.PENDING.name());
        when(taskMapper.selectOverdue(eq("1001"), eq(1L))).thenReturn(List.of(t));

        List<FlowTaskDO> result = service.listOverdue("1001", null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        verify(taskMapper).selectOverdue("1001", 1L);
    }

    @Test
    @DisplayName("countOverdue P2-32: 统计超期任务数量")
    void testCountOverdue() {
        when(taskMapper.countOverdue(eq("1001"), eq(1L))).thenReturn(3L);
        long count = service.countOverdue("1001", null);
        assertThat(count).isEqualTo(3L);
        verify(taskMapper).countOverdue("1001", 1L);
    }

    // ============== P2-33: 历史任务多维筛选分页 ==============

    @Test
    @DisplayName("listDoneByAssigneePageMulti P2-33: 多维筛选分页查询")
    void testListDoneByAssigneePageMulti() {
        FlowHisTaskDO his = new FlowHisTaskDO();
        his.setTaskId(88L);
        his.setInstanceId(10L);
        his.setFlowCode("f1");
        his.setNodeCode("t1");
        his.setNodeName("审批");
        his.setAssigneeId("1001");
        his.setAssigneeName("张三");
        his.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        his.setBusinessType("initiation");
        when(hisTaskMapper.selectDonePage(eq("1001"), eq("initiation"), eq("f1"),
                any(), any(), eq(1L), eq(0), eq(10))).thenReturn(List.of(his));
        when(hisTaskMapper.countDone(eq("1001"), eq("initiation"), eq("f1"),
                any(), any(), eq(1L))).thenReturn(1L);

        var page = service.listDoneByAssigneePageMulti("1001", "initiation", "f1",
                null, null, null, 1, 10);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        FlowTaskDO converted = page.getList().get(0);
        assertThat(converted.getId()).isEqualTo(88L);
        assertThat(converted.getInstanceId()).isEqualTo(10L);
        assertThat(converted.getFlowCode()).isEqualTo("f1");
        assertThat(converted.getAssigneeId()).isEqualTo("1001");
        assertThat(converted.getBusinessType()).isEqualTo("initiation");
    }

    // ============== P2-34: 关键操作事件触发 ==============

    @Test
    @DisplayName("testUrgeFiresEvent P2-34: urge 触发 onTaskUrged 事件")
    void testUrgeFiresEvent() {
        FlowTaskDO t1 = new FlowTaskDO();
        t1.setId(1L);
        t1.setAssigneeId("1001");
        when(taskMapper.selectPendingByInstance(10L)).thenReturn(List.of(t1));

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        service.urge(10L, 7L, "请尽快处理");
        // 验证 onTaskUrged 被调用（实例级催办，taskId 传 null）
        verify(listener, times(1)).onTaskUrged(10L, null);
    }

    @Test
    @DisplayName("testTransferFiresEvent P2-34: transfer 触发 onTaskTransferred 事件")
    void testTransferFiresEvent() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setInstanceId(10L);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("100");
        task.setAssigneeName("原办理人");
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(200L);
        dto.setTargetUserName("新办理人");
        service.transfer(dto);

        // 验证 onTaskTransferred 被调用：fromUserId=100, toUserId=200
        verify(listener, times(1)).onTaskTransferred(1L, 100L, 200L);
    }

    @Test
    @DisplayName("testDelegateFiresEvent P2-34: delegate 触发 onTaskDelegated 事件")
    void testDelegateFiresEvent() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setInstanceId(10L);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeId("100");
        task.setAssigneeName("原办理人");
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(300L);
        dto.setTargetUserName("被委派人");
        service.delegate(dto);

        // 验证 onTaskDelegated 被调用：fromUserId=100, toUserId=300
        verify(listener, times(1)).onTaskDelegated(1L, 100L, 300L);
    }

    // ============== P2-35: 异步事件机制 ==============

    @Test
    @DisplayName("testAsyncEventPublished P2-35: urge 时发布 Spring 异步事件")
    void testAsyncEventPublished() {
        FlowTaskDO t1 = new FlowTaskDO();
        t1.setId(1L);
        t1.setAssigneeId("1001");
        when(taskMapper.selectPendingByInstance(10L)).thenReturn(List.of(t1));

        service.urge(10L, 7L, "催办");

        // P2-35: 验证 ApplicationEventPublisher.publishEvent 被调用
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    // ============== P2-36: 超时任务 ==============

    @Test
    @DisplayName("testTimeoutTask P2-36: 标记 PENDING 任务为 TIMEOUT + 写审计 + 触发事件")
    void testTimeoutTask() {
        FlowTaskDO task = baseTask();
        task.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        when(taskMapper.selectById(1L)).thenReturn(task);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        service.timeoutTask(1L, "审批超时");

        // 1. 任务状态更新为 TIMEOUT
        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.TIMEOUT.name()),
                eq("审批超时"), any(), any());
        // 2. 写审计日志 action=TIMEOUT
        verify(auditLogMapper, times(1)).insert(
                (com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO) any());
        // 3. 触发 onTaskTimeout 事件
        verify(listener, times(1)).onTaskTimeout(1L, 10L);
        // 4. 发布 Spring 异步事件
        verify(eventPublisher, atLeastOnce()).publishEvent(any());
    }

    @Test
    @DisplayName("testTimeoutTask P2-36: 已完成任务不可标记超时")
    void testTimeoutTaskAlreadyFinished() {
        FlowTaskDO task = baseTask();
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        when(taskMapper.selectById(1L)).thenReturn(task);

        assertThatThrownBy(() -> service.timeoutTask(1L, "超时"))
                .isInstanceOf(BizException.class);
        verify(taskMapper, never()).completeTask(anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("testTimeoutTask P2-36: 任务不存在抛 NOT_FOUND")
    void testTimeoutTaskNotFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.timeoutTask(99L, "超时"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("testTimeoutTask P2-36: CLAIMED 任务也可标记超时")
    void testTimeoutTaskClaimed() {
        FlowTaskDO task = baseTask();
        task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        task.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        when(taskMapper.selectById(1L)).thenReturn(task);

        service.timeoutTask(1L, "签收后超时");

        verify(taskMapper).completeTask(eq(1L), eq(FlowTaskStatus.TIMEOUT.name()),
                eq("签收后超时"), any(), any());
    }

    // ============== P2-42: 任务审批意见分类 ==============

    @Test
    @DisplayName("testPassWithCommentType P2-42: pass 时 commentType 写入审计日志")
    void testPassWithCommentType() {
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
        dto.setComment("同意该申请");
        dto.setCommentType("AGREE");
        service.pass(dto);

        // 验证审计日志包含 commentType
        ArgumentCaptor<com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO> auditCaptor =
                ArgumentCaptor.forClass(com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO auditLog = auditCaptor.getValue();
        assertThat(auditLog.getComment()).isEqualTo("同意该申请");
        assertThat(auditLog.getCommentType()).isEqualTo("AGREE");
        assertThat(auditLog.getAction()).isEqualTo("PASS");
    }

    @Test
    @DisplayName("testRejectWithCommentType P2-42: reject 时 commentType 写入审计日志")
    void testRejectWithCommentType() {
        FlowTaskDO task = baseTask();
        FlowInstanceDO ins = simpleInstance(10L);
        ins.setStartAt(LocalDateTime.now().minusMinutes(3));
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(instanceMapper.selectById(10L)).thenReturn(ins);
        when(advancer.advance(any(), eq("t1"), eq("REJECT"), any(), any()))
                .thenReturn(Collections.emptyList());

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setComment("不同意，金额过大");
        dto.setCommentType("DISAGREE");
        service.reject(dto);

        ArgumentCaptor<com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO> auditCaptor =
                ArgumentCaptor.forClass(com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO.class);
        verify(auditLogMapper).insert(auditCaptor.capture());
        com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO auditLog = auditCaptor.getValue();
        assertThat(auditLog.getComment()).isEqualTo("不同意，金额过大");
        assertThat(auditLog.getCommentType()).isEqualTo("DISAGREE");
        assertThat(auditLog.getAction()).isEqualTo("REJECT");
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
