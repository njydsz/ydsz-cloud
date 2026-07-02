package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.flow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.flow.service.FlowTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowInstanceServiceImpl 单元测试
 *
 * <p>覆盖：start / getById / getByBusiness / terminate / suspend / activate /
 * complete / toView / listByInitiator / 幂等等核心逻辑。
 */
@DisplayName("FlowInstanceServiceImpl 单元测试")
class FlowInstanceServiceImplTest {

    private FlowInstanceMapper instanceMapper;
    private FlowDefinitionService definitionService;
    private FlowAdvancer advancer;
    private FlowTaskService taskService;
    private FlowTaskMapper taskMapper;
    private List<FlowEventListener> eventListeners;
    private FlowInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        instanceMapper = mock(FlowInstanceMapper.class);
        definitionService = mock(FlowDefinitionService.class);
        advancer = mock(FlowAdvancer.class);
        taskService = mock(FlowTaskService.class);
        taskMapper = mock(FlowTaskMapper.class);
        eventListeners = new ArrayList<>();
        service = new FlowInstanceServiceImpl(instanceMapper, definitionService,
                advancer, taskService, taskMapper, eventListeners);
    }

    @Test
    @DisplayName("start 参数缺失应抛 BAD_REQUEST")
    void testStartMissingFields() {
        assertThatThrownBy(() -> service.start(new FlowStartProcessDTO()))
                .isInstanceOf(BizException.class);
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("f1");
        assertThatThrownBy(() -> service.start(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("start 已有 RUNNING 实例时直接返回原 ID（幂等）")
    void testStartIdempotent() {
        FlowInstanceDO existing = new FlowInstanceDO();
        existing.setId(99L);
        existing.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("f1");
        dto.setBusinessType("initiation");
        dto.setBusinessId("100");
        when(instanceMapper.selectByBusiness("initiation", "100")).thenReturn(existing);

        Long result = service.start(dto);
        assertThat(result).isEqualTo(99L);
        verify(instanceMapper, never()).insert((FlowInstanceDO) any());
    }

    @Test
    @DisplayName("start 流程定义未发布应抛 NOT_FOUND")
    void testStartDefinitionNotFound() {
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("unknown");
        dto.setBusinessType("initiation");
        dto.setBusinessId("100");
        when(instanceMapper.selectByBusiness(anyString(), anyString())).thenReturn(null);
        when(definitionService.getPublished(eq("unknown"), eq("1.0"), any())).thenReturn(null);

        assertThatThrownBy(() -> service.start(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未发布");
    }

    @Test
    @DisplayName("start 成功：插入实例并推进到首节点")
    void testStartSuccess() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(1L);
        def.setFlowCode("f1");
        def.setFlowName("测试流程");
        def.setVersion("1.0");
        when(instanceMapper.selectByBusiness(anyString(), anyString())).thenReturn(null);
        when(definitionService.getPublished(eq("f1"), anyString(), any())).thenReturn(def);
        // 模拟 insert 后回填 id
        org.mockito.Mockito.doAnswer(inv -> {
            FlowInstanceDO arg = inv.getArgument(0);
            arg.setId(101L);
            return 1;
        }).when(instanceMapper).insert((FlowInstanceDO) any());

        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("f1");
        dto.setBusinessType("initiation");
        dto.setBusinessId("100");
        dto.setBusinessNo("IN-100");
        dto.setInitiatorId(7L);
        dto.setInitiatorName("张三");
        dto.setVariables(Map.of("k", "v"));
        dto.setProviderTraceId("trace-001");

        Long instanceId = service.start(dto);
        assertThat(instanceId).isEqualTo(101L);
        ArgumentCaptor<FlowInstanceDO> captor = ArgumentCaptor.forClass(FlowInstanceDO.class);
        verify(instanceMapper).insert(captor.capture());
        FlowInstanceDO inserted = captor.getValue();
        assertThat(inserted.getFlowCode()).isEqualTo("f1");
        assertThat(inserted.getBusinessType()).isEqualTo("initiation");
        assertThat(inserted.getFlowStatus()).isEqualTo(FlowInstanceStatus.RUNNING.name());
        assertThat(inserted.getActivityStatus()).isEqualTo(1);
        assertThat(inserted.getStartAt()).isNotNull();
        assertThat(inserted.getProviderTraceId()).isNotNull();
        // variable JSON 应包含 k=v
        assertThat(inserted.getVariable()).contains("k").contains("v");
        verify(advancer, times(1)).start(101L);
    }

    @Test
    @DisplayName("start 成功但变量为 null 时不应写入 variable 字段")
    void testStartNullVariables() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(1L);
        def.setFlowCode("f1");
        def.setFlowName("测试");
        when(instanceMapper.selectByBusiness(anyString(), anyString())).thenReturn(null);
        when(definitionService.getPublished(anyString(), anyString(), any())).thenReturn(def);
        org.mockito.Mockito.doAnswer(inv -> {
            FlowInstanceDO arg = inv.getArgument(0);
            arg.setId(200L);
            return 1;
        }).when(instanceMapper).insert((FlowInstanceDO) any());

        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("f1");
        dto.setBusinessType("initiation");
        dto.setBusinessId("1");
        service.start(dto);

        ArgumentCaptor<FlowInstanceDO> captor = ArgumentCaptor.forClass(FlowInstanceDO.class);
        verify(instanceMapper).insert(captor.capture());
        assertThat(captor.getValue().getVariable()).isNull();
    }

    @Test
    @DisplayName("getById 委托给 mapper")
    void testGetById() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        assertThat(service.getById(1L)).isSameAs(ins);
    }

    @Test
    @DisplayName("getByBusiness 委托给 mapper")
    void testGetByBusiness() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        when(instanceMapper.selectByBusiness("initiation", "100")).thenReturn(ins);
        assertThat(service.getByBusiness("initiation", "100")).isSameAs(ins);
    }

    @Test
    @DisplayName("terminate 已结束流程应抛 BAD_REQUEST")
    void testTerminateAlreadyFinished() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.COMPLETED.name());
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        assertThatThrownBy(() -> service.terminate(1L, "撤回"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("terminate 运行中流程：更新状态 + 取消 PENDING 任务")
    void testTerminateRunning() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(java.time.LocalDateTime.now().minusMinutes(5));
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        service.terminate(1L, "管理员撤回");
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.TERMINATED.name()),
                eq(null), eq(null), any(), any());
        verify(taskService).cancelByInstance(1L, FlowTaskStatus.CANCELLED.name());
    }

    @Test
    @DisplayName("terminate 不存在的实例应抛 NOT_FOUND")
    void testTerminateNotFound() {
        when(instanceMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.terminate(99L, "reason"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("suspend 非运行中流程应抛 BAD_REQUEST")
    void testSuspendNotRunning() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.SUSPENDED.name());
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        assertThatThrownBy(() -> service.suspend(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("suspend 运行中流程：切换为 SUSPENDED")
    void testSuspendRunning() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("审批");
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        service.suspend(1L);
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.SUSPENDED.name()),
                eq("n1"), eq("审批"), eq(null), eq(null));
    }

    @Test
    @DisplayName("activate 非挂起流程应抛 BAD_REQUEST")
    void testActivateNotSuspended() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        assertThatThrownBy(() -> service.activate(1L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("activate 挂起流程：切换为 RUNNING")
    void testActivateSuspended() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.SUSPENDED.name());
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("审批");
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        service.activate(1L);
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.RUNNING.name()),
                eq("n1"), eq("审批"), eq(null), eq(null));
    }

    @Test
    @DisplayName("complete 已结束流程为幂等操作")
    void testCompleteAlreadyFinished() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.COMPLETED.name());
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        service.complete(1L, "end");
        verify(instanceMapper, never()).updateStatus(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("complete 运行中流程：更新状态 + 跳过后续任务 + 触发事件")
    void testCompleteRunning() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(java.time.LocalDateTime.now().minusMinutes(10));
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        service.complete(1L, "end");
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.COMPLETED.name()),
                eq("end"), eq(null), any(), any());
        verify(taskService).cancelByInstance(1L, FlowTaskStatus.SKIPPED.name());
        verify(listener, times(1)).onInstanceCompleted(1L);
    }

    @Test
    @DisplayName("complete 时事件监听器抛异常不应中断主流程")
    void testCompleteListenerThrows() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(java.time.LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowEventListener bad = mock(FlowEventListener.class);
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(bad).onInstanceCompleted(1L);
        eventListeners.add(bad);

        // 不应抛异常
        service.complete(1L, "end");
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.COMPLETED.name()),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("toView 输入 null 返回 null")
    void testToViewNull() {
        assertThat(service.toView(null, null)).isNull();
        assertThat(service.toView(null, Collections.emptyList())).isNull();
    }

    @Test
    @DisplayName("toView 转换所有字段")
    void testToView() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowCode("f1");
        ins.setFlowName("F1");
        ins.setFlowVersion("2.0");
        ins.setBusinessType("initiation");
        ins.setBusinessId("100");
        ins.setBusinessNo("IN-100");
        ins.setTitle("标题");
        ins.setInitiatorId(7L);
        ins.setInitiatorName("张三");
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("审批");
        ins.setFlowStatus("RUNNING");
        ins.setActivityStatus(1);
        ins.setStartAt(java.time.LocalDateTime.of(2026, 1, 1, 0, 0));
        ins.setEndAt(java.time.LocalDateTime.of(2026, 1, 2, 0, 0));
        ins.setDurationMs(86400000L);
        ins.setVariable("{\"k\":\"v\"}");

        FlowInstanceViewDTO.FlowTaskViewDTO task = FlowInstanceViewDTO.FlowTaskViewDTO.builder()
                .id(10L).nodeCode("n1").build();
        FlowInstanceViewDTO view = service.toView(ins, List.of(task));
        assertThat(view).isNotNull();
        assertThat(view.getId()).isEqualTo(1L);
        assertThat(view.getFlowCode()).isEqualTo("f1");
        assertThat(view.getVersion()).isEqualTo("2.0");
        assertThat(view.getBusinessType()).isEqualTo("initiation");
        assertThat(view.getBusinessId()).isEqualTo("100");
        assertThat(view.getTitle()).isEqualTo("标题");
        assertThat(view.getInitiatorId()).isEqualTo(7L);
        assertThat(view.getCurrentNodeCode()).isEqualTo("n1");
        assertThat(view.getFlowStatus()).isEqualTo("RUNNING");
        assertThat(view.getStartAt()).isNotNull();
        assertThat(view.getEndAt()).isNotNull();
        assertThat(view.getDurationMs()).isEqualTo(86400000L);
        assertThat(view.getCurrentTasks()).hasSize(1);
    }

    @Test
    @DisplayName("listByInitiator 委托给 mapper")
    void testListByInitiator() {
        FlowInstanceDO a = new FlowInstanceDO();
        a.setId(1L);
        when(instanceMapper.selectByInitiator(7L, "RUNNING")).thenReturn(List.of(a));
        List<FlowInstanceDO> result = service.listByInitiator(7L, "RUNNING");
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("createFirstTask：推进到下一节点 + 取消时仍生成任务")
    void testCreateFirstTask() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowNodeDO startNode = new FlowNodeDO();
        startNode.setNodeCode("s1");
        startNode.setNodeCode("s1");

        FlowNodeDO next = new FlowNodeDO();
        next.setNodeCode("t1");
        next.setNodeName("审批");
        next.setNodeType(1);
        when(advancer.advance(any(), eq("s1"), eq("PASS"), eq(null), any()))
                .thenReturn(List.of(next));

        Long result = ((FlowInstanceServiceImpl) service).createFirstTask(1L, startNode, Map.of());
        assertThat(result).isEqualTo(1L);
        verify(taskService, times(1)).createTask(eq(1L), eq(next), any());
        verify(instanceMapper).updateStatus(eq(1L), any(), eq("t1"), eq("审批"), eq(null), eq(null));
    }

    @Test
    @DisplayName("createFirstTask：nextNodes 为空时直接 complete 流程")
    void testCreateFirstTaskNoNext() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(java.time.LocalDateTime.now());
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowNodeDO startNode = new FlowNodeDO();
        startNode.setNodeCode("s1");
        when(advancer.advance(any(), eq("s1"), eq("PASS"), eq(null), any()))
                .thenReturn(Collections.emptyList());

        Long result = ((FlowInstanceServiceImpl) service).createFirstTask(1L, startNode, Map.of());
        assertThat(result).isNull();
        // 内部调用了 instanceService.complete
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.COMPLETED.name()),
                eq("s1"), any(), any(), any());
    }

    @Test
    @DisplayName("generateTasksForNodes：跳过 CC 节点（nodeType=2）")
    void testGenerateTasksForNodesSkipCC() {
        FlowNodeDO cc = new FlowNodeDO();
        cc.setNodeCode("cc1");
        cc.setNodeType(2);
        ((FlowInstanceServiceImpl) service).generateTasksForNodes(1L, List.of(cc), Map.of());
        verify(taskService, never()).createTask(any(), any(), any());
    }

    @Test
    @DisplayName("generateTasksForNodes：遇到 END 节点时直接 complete")
    void testGenerateTasksForNodesEnd() {
        // 模拟 complete 内部 selectById 返回非空实例
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(java.time.LocalDateTime.now().minusSeconds(1));
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        FlowNodeDO end = new FlowNodeDO();
        end.setNodeCode("end1");
        end.setNodeType(6);
        ((FlowInstanceServiceImpl) service).generateTasksForNodes(1L, List.of(end), Map.of());
        verify(taskService, never()).createTask(any(), any(), any());
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.COMPLETED.name()),
                eq("end1"), any(), any(), any());
    }

    @Test
    @DisplayName("generateTasksForNodes：APPROVAL 节点生成 task")
    void testGenerateTasksForNodesApproval() {
        FlowNodeDO t = new FlowNodeDO();
        t.setNodeCode("t1");
        t.setNodeType(1);
        ((FlowInstanceServiceImpl) service).generateTasksForNodes(1L, List.of(t), Map.of());
        verify(taskService, times(1)).createTask(eq(1L), eq(t), any());
    }

    // ============== P1-8: 撤回 recall ==============

    @Test
    @DisplayName("recall 实例不存在应抛 NOT_FOUND")
    void testRecallNotFound() {
        when(instanceMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.recall(99L, 7L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("recall 非发起人应抛 FORBIDDEN")
    void testRecallNotInitiator() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setInitiatorId(7L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        assertThatThrownBy(() -> service.recall(1L, 999L))  // 999 不是发起人
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("recall 非运行中流程应抛 BAD_REQUEST")
    void testRecallNotRunning() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setInitiatorId(7L);
        ins.setFlowStatus(FlowInstanceStatus.COMPLETED.name());
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        assertThatThrownBy(() -> service.recall(1L, 7L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("recall 审批人已签收/已处理应抛 BAD_REQUEST")
    void testRecallAlreadyProcessed() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setInitiatorId(7L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());

        FlowTaskDO claimedTask = new FlowTaskDO();
        claimedTask.setId(10L);
        claimedTask.setTaskStatus(FlowTaskStatus.CLAIMED.name());  // 已签收

        when(instanceMapper.selectById(1L)).thenReturn(ins);
        when(taskMapper.selectPendingByInstance(1L)).thenReturn(List.of(claimedTask));

        assertThatThrownBy(() -> service.recall(1L, 7L))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("recall 成功：取消当前待办 + 重新推进")
    void testRecallSuccess() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setInitiatorId(7L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());

        FlowTaskDO pendingTask = new FlowTaskDO();
        pendingTask.setId(10L);
        pendingTask.setTaskStatus(FlowTaskStatus.PENDING.name());  // 未签收，可撤回

        when(instanceMapper.selectById(1L)).thenReturn(ins);
        when(taskMapper.selectPendingByInstance(1L)).thenReturn(List.of(pendingTask));

        boolean result = service.recall(1L, 7L);
        assertThat(result).isTrue();
        // 取消当前待办
        verify(taskService).cancelByInstance(1L, FlowTaskStatus.CANCELLED.name());
        // 重新推进
        verify(advancer, times(1)).start(1L);
    }
}
