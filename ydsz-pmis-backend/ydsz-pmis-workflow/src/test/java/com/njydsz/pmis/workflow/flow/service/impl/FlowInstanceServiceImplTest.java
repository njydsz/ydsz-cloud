package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.service.impl.FlowInstanceServiceImpl;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowEventContext;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.FlowCcService;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowSubProcessService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import com.njydsz.pmis.workflow.service.FlowCanaryService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FlowInstanceServiceImpl 单元测试")
class FlowInstanceServiceImplTest {

    private FlowInstanceMapper instanceMapper;
    private FlowDefinitionService definitionService;
    private FlowAdvancer advancer;
    private FlowTaskService taskService;
    private FlowTaskMapper taskMapper;
    private List<FlowEventListener> eventListeners;
    private ApplicationEventPublisher eventPublisher;
    private FlowSubProcessService subProcessService;
    private FlowCcService ccService;
    private FlowMetrics flowMetrics;
    private FlowCanaryService canaryService;
    private com.njydsz.pmis.workflow.mapper.FlowNodeMapper nodeMapper;
    private com.njydsz.pmis.workflow.mapper.FlowSkipMapper skipMapper;
    private com.njydsz.pmis.workflow.engine.FlowVariableStrategy variableStrategy;
    private FlowInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        instanceMapper = mock(FlowInstanceMapper.class);
        definitionService = mock(FlowDefinitionService.class);
        advancer = mock(FlowAdvancer.class);
        taskService = mock(FlowTaskService.class);
        taskMapper = mock(FlowTaskMapper.class);
        eventListeners = new ArrayList<>();
        // P2-35: 注入 ApplicationEventPublisher mock
        eventPublisher = mock(ApplicationEventPublisher.class);
        // P1-3: 注入 FlowSubProcessService mock
        subProcessService = mock(FlowSubProcessService.class);
        // GAP-P1: 注入 FlowCcService mock
        ccService = mock(FlowCcService.class);
        // P2-3: Prometheus 指标 mock（测试不需要真实指标）
        flowMetrics = mock(FlowMetrics.class);
        // P3-1: 灰度发布服务 mock
        canaryService = mock(FlowCanaryService.class);
        // GAP-V2-08: 模拟运行新增依赖
        nodeMapper = mock(FlowNodeMapper.class);
        skipMapper = mock(FlowSkipMapper.class);
        variableStrategy = mock(FlowVariableStrategy.class);
        service = new FlowInstanceServiceImpl(instanceMapper, definitionService,
                canaryService, advancer, taskService, taskMapper,
                nodeMapper, skipMapper, variableStrategy,
                eventListeners, flowMetrics, eventPublisher, subProcessService, ccService);
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
        when(canaryService.resolveEffectiveDefinition(eq("unknown"), eq("1.0"), any(), any()))
                .thenReturn(null);

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
        when(canaryService.resolveEffectiveDefinition(eq("f1"), anyString(), any(), any()))
                .thenReturn(def);
        // 模拟 insert 后回填 id
        doAnswer(inv -> {
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
        when(canaryService.resolveEffectiveDefinition(anyString(), anyString(), any(), any()))
                .thenReturn(def);
        doAnswer(inv -> {
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
        ins.setStartAt(LocalDateTime.now().minusMinutes(5));
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        service.terminate(1L, "管理员撤回");
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.TERMINATED.name()),
                eq(null), eq(null), any(), any());
        verify(taskService).cancelByInstance(1L, FlowTaskStatus.CANCELLED.name());
    }

    @Test
    @DisplayName("terminate P2-18: reason 持久化到 variable JSON")
    void testTerminatePersistsReason() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        ins.setVariable("{\"k\":\"v\"}");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.terminate(1L, "管理员强制终止");

        // 验证 updateVariable 被调用，且 variable 包含 _terminateReason
        ArgumentCaptor<String> varCaptor = ArgumentCaptor.forClass(String.class);
        verify(instanceMapper).updateVariable(eq(1L), varCaptor.capture());
        String savedVar = varCaptor.getValue();
        assertThat(savedVar).contains("_terminateReason").contains("管理员强制终止");
        // 原有变量应保留
        assertThat(savedVar).contains("\"k\":\"v\"");
    }

    @Test
    @DisplayName("terminate P2-18: 无 variable 时也能正确写入 reason")
    void testTerminatePersistsReasonNoExistingVar() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        ins.setVariable(null);
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.terminate(1L, "意外终止");

        ArgumentCaptor<String> varCaptor = ArgumentCaptor.forClass(String.class);
        verify(instanceMapper).updateVariable(eq(1L), varCaptor.capture());
        assertThat(varCaptor.getValue()).contains("_terminateReason").contains("意外终止");
    }

    @Test
    @DisplayName("terminate P2-18: reason 为空时不调用 updateVariable")
    void testTerminateNoReason() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.terminate(1L, null);
        // 没有 reason，不应该调用 updateVariable
        verify(instanceMapper, never()).updateVariable(any(), any());
        verify(instanceMapper).updateStatus(eq(1L), eq(FlowInstanceStatus.TERMINATED.name()),
                eq(null), eq(null), any(), any());
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
    @DisplayName("suspend P2-18: 冻结 PENDING/CLAIMED 任务为 FROZEN")
    void testSuspendFreezesTasks() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("审批");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.suspend(1L);
        // 验证冻结任务的 mapper 被调用
        verify(taskMapper).freezeByInstance(1L);
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
    @DisplayName("activate P2-18: 解冻 FROZEN 任务回到 PENDING")
    void testActivateUnfreezesTasks() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.SUSPENDED.name());
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("审批");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.activate(1L);
        // 验证解冻任务的 mapper 被调用
        verify(taskMapper).unfreezeByInstance(1L);
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
        ins.setStartAt(LocalDateTime.now().minusMinutes(10));
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
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowEventListener bad = mock(FlowEventListener.class);
        doThrow(new RuntimeException("boom")).when(bad).onInstanceCompleted(1L);
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
        ins.setStartAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        ins.setEndAt(LocalDateTime.of(2026, 1, 2, 0, 0));
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
        ins.setStartAt(LocalDateTime.now());
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
        ins.setStartAt(LocalDateTime.now().minusSeconds(1));
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

    // ============== P2-23: 实例多维分页查询 ==============

    @Test
    @DisplayName("page P2-23: 多维度过滤 + 真分页 offset=(page-1)*size")
    void testPageMultiDimension() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowCode("f1");
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 6, 30, 23, 59);
        // 模拟第 2 页、每页 10 条：offset = (2-1)*10 = 10
        when(instanceMapper.selectPage(eq("initiation"), eq(7L), eq("RUNNING"),
                eq(start), eq(end), eq(1L), eq(10), eq(10)))
                .thenReturn(List.of(ins));
        when(instanceMapper.countPage(eq("initiation"), eq(7L), eq("RUNNING"),
                eq(start), eq(end), eq(1L))).thenReturn(25L);

        PageResult<FlowInstanceDO> page = service.page(
                "initiation", 7L, "RUNNING", start, end, 1L, 2, 10);
        assertThat(page.getList()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(25L);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(10);
        // 总页数 = (25 + 10 - 1) / 10 = 3
        assertThat(page.getPages()).isEqualTo(3L);
        verify(instanceMapper).selectPage("initiation", 7L, "RUNNING",
                start, end, 1L, 10, 10);
        verify(instanceMapper).countPage("initiation", 7L, "RUNNING",
                start, end, 1L);
    }

    @Test
    @DisplayName("page P2-23: 非法 pageNo/pageSize 兜底（page<1→1, size<=0→20）")
    void testPageMultiDimensionInvalidPaging() {
        when(instanceMapper.selectPage(any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(instanceMapper.countPage(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        PageResult<FlowInstanceDO> page = service.page(
                null, null, null, null, null, null, -1, 0);
        // page<1 → safePage=1, size<=0 → safeSize=20, offset=0
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(20);
        verify(instanceMapper).selectPage(null, null, null, null, null, null, 0, 20);
    }

    @Test
    @DisplayName("page P2-23: 全空过滤条件也能查询")
    void testPageMultiDimensionAllNullFilters() {
        when(instanceMapper.selectPage(any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(instanceMapper.countPage(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        PageResult<FlowInstanceDO> page = service.page(
                null, null, null, null, null, null, 1, 20);
        assertThat(page.getList()).isEmpty();
        assertThat(page.getTotal()).isEqualTo(0L);
    }

    // ============== P2-24: 流程变量读写 ==============

    @Test
    @DisplayName("getVariables P2-24: 解析 variable JSON 返回 Map")
    void testGetVariables() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setVariable("{\"amount\":1000,\"initiator\":\"张三\"}");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        Map<String, Object> vars = service.getVariables(1L);
        assertThat(vars).isNotEmpty();
        assertThat(vars.get("amount")).isEqualTo(1000);
        assertThat(vars.get("initiator")).isEqualTo("张三");
    }

    @Test
    @DisplayName("getVariables P2-24: 实例不存在或无变量返回空 Map")
    void testGetVariablesEmpty() {
        // 实例不存在
        when(instanceMapper.selectById(99L)).thenReturn(null);
        assertThat(service.getVariables(99L)).isEmpty();

        // variable 为 null
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setVariable(null);
        when(instanceMapper.selectById(1L)).thenReturn(ins);
        assertThat(service.getVariables(1L)).isEmpty();

        // variable 为空字符串
        ins.setVariable("");
        assertThat(service.getVariables(1L)).isEmpty();
    }

    @Test
    @DisplayName("getVariables P2-24: JSON 解析失败返回空 Map")
    void testGetVariablesBadJson() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setVariable("not-a-json");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        assertThat(service.getVariables(1L)).isEmpty();
    }

    @Test
    @DisplayName("setVariable P2-24: 合并写入单个变量并持久化")
    void testSetVariable() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setVariable("{\"k1\":\"v1\"}");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.setVariable(1L, "k2", "v2");

        ArgumentCaptor<String> varCaptor = ArgumentCaptor.forClass(String.class);
        verify(instanceMapper).updateVariable(eq(1L), varCaptor.capture());
        String saved = varCaptor.getValue();
        // 原有变量应保留
        assertThat(saved).contains("\"k1\":\"v1\"");
        // 新变量应写入
        assertThat(saved).contains("\"k2\":\"v2\"");
    }

    @Test
    @DisplayName("setVariable P2-24: 无原有变量时也能写入")
    void testSetVariableNoExisting() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setVariable(null);
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.setVariable(1L, "k1", "v1");

        ArgumentCaptor<String> varCaptor = ArgumentCaptor.forClass(String.class);
        verify(instanceMapper).updateVariable(eq(1L), varCaptor.capture());
        assertThat(varCaptor.getValue()).contains("\"k1\":\"v1\"");
    }

    @Test
    @DisplayName("setVariable P2-24: key 为空抛 BAD_REQUEST")
    void testSetVariableEmptyKey() {
        assertThatThrownBy(() -> service.setVariable(1L, "", "v"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.setVariable(1L, null, "v"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("setVariable P2-24: 实例不存在抛 NOT_FOUND")
    void testSetVariableNotFound() {
        when(instanceMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.setVariable(99L, "k", "v"))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("setVariables P2-24: 批量合并写入并持久化")
    void testSetVariables() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setVariable("{\"k1\":\"v1\"}");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        Map<String, Object> newVars = new HashMap<>();
        newVars.put("k2", "v2");
        newVars.put("k3", "v3");
        service.setVariables(1L, newVars);

        ArgumentCaptor<String> varCaptor = ArgumentCaptor.forClass(String.class);
        verify(instanceMapper).updateVariable(eq(1L), varCaptor.capture());
        String saved = varCaptor.getValue();
        // 原有变量应保留
        assertThat(saved).contains("\"k1\":\"v1\"");
        // 新变量应写入
        assertThat(saved).contains("\"k2\":\"v2\"");
        assertThat(saved).contains("\"k3\":\"v3\"");
    }

    @Test
    @DisplayName("setVariables P2-24: 空 Map 时不调用 updateVariable")
    void testSetVariablesEmpty() {
        service.setVariables(1L, Collections.emptyMap());
        service.setVariables(1L, null);
        verify(instanceMapper, never()).updateVariable(any(), any());
    }

    @Test
    @DisplayName("setVariables P2-24: 实例不存在抛 NOT_FOUND")
    void testSetVariablesNotFound() {
        when(instanceMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.setVariables(99L, Map.of("k", "v")))
                .isInstanceOf(BizException.class);
    }

    // ============== P2-34: 关键操作事件触发 ==============

    @Test
    @DisplayName("testTerminateFiresEvent P2-34: terminate 触发 onInstanceTerminated 事件")
    void testTerminateFiresEvent() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        service.terminate(1L, "管理员终止");
        // 验证 onInstanceTerminated 被调用
        verify(listener, times(1)).onInstanceTerminated(1L, "管理员终止");
    }

    @Test
    @DisplayName("testSuspendFiresEvent P2-34: suspend 触发 onInstanceSuspended 事件")
    void testSuspendFiresEvent() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("审批");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        service.suspend(1L);
        // 验证 onInstanceSuspended 被调用
        verify(listener, times(1)).onInstanceSuspended(1L);
    }

    @Test
    @DisplayName("testActivateFiresEvent P2-34: activate 触发 onInstanceActivated 事件")
    void testActivateFiresEvent() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.SUSPENDED.name());
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("审批");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        service.activate(1L);
        // 验证 onInstanceActivated 被调用
        verify(listener, times(1)).onInstanceActivated(1L);
    }

    // ============== P2-35: 异步事件机制 ==============

    @Test
    @DisplayName("testAsyncEventPublished P2-35: complete 时发布 Spring 异步事件")
    void testAsyncEventPublished() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.complete(1L, "end");

        // P2-35: 验证 ApplicationEventPublisher.publishEvent 被调用
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    @Test
    @DisplayName("testAsyncEventPublished P2-35: terminate 时也发布 Spring 异步事件")
    void testAsyncEventPublishedOnTerminate() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        service.terminate(1L, "终止原因");

        // P2-35: 验证 ApplicationEventPublisher.publishEvent 被调用
        verify(eventPublisher, times(1)).publishEvent(any());
    }

    // ============== P2-37: 事件元数据携带 ==============

    @Test
    @DisplayName("testTerminateFiresEventWithContext P2-37: terminate 同时触发携带上下文的重载版本")
    void testTerminateFiresEventWithContext() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        ins.setStartAt(LocalDateTime.now().minusMinutes(1));
        ins.setTenantId(2L);
        ins.setProviderTraceId("trace-001");
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowEventListener listener = mock(FlowEventListener.class);
        eventListeners.add(listener);

        service.terminate(1L, "管理员终止");

        // P2-37: 验证携带上下文的重载版本被调用
        ArgumentCaptor<FlowEventContext> ctxCaptor = ArgumentCaptor.forClass(FlowEventContext.class);
        verify(listener, times(1)).onInstanceTerminated(eq(1L), eq("管理员终止"), ctxCaptor.capture());
        FlowEventContext ctx = ctxCaptor.getValue();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getInstanceId()).isEqualTo(1L);
        assertThat(ctx.getAction()).isEqualTo("TERMINATE");
        assertThat(ctx.getOperatedAt()).isNotNull();
        // 上下文应携带租户与链路追踪 ID
        assertThat(ctx.getTenantId()).isEqualTo("2");
        assertThat(ctx.getTraceId()).isEqualTo("trace-001");
    }

    // ============== GAP-V2-02: 表单渲染数据 ==============

    @Test
    @DisplayName("getFormRenderData GAP-V2-02: 通过 taskId 获取表单渲染数据")
    void testGetFormRenderDataWithTaskId() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setDefinitionId(10L);
        ins.setFlowStatus("RUNNING");
        ins.setTitle("测试标题");
        ins.setVariable(null);
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowTaskDO task = new FlowTaskDO();
        task.setId(50L);
        task.setNodeCode("t1");
        task.setNodeName("审批");
        when(taskMapper.selectById(50L)).thenReturn(task);

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("t1");
        node.setNodeName("审批");
        String config = "[{\"field\":\"amount\",\"label\":\"金额\"}]";
        node.setFormFieldsConfig(config);
        when(nodeMapper.selectByCode(10L, "t1")).thenReturn(node);

        Map<String, Object> result = service.getFormRenderData(1L, 50L);
        assertThat(result).isNotNull();
        assertThat(result.get("instanceId")).isEqualTo(1L);
        assertThat(result.get("taskId")).isEqualTo(50L);
        assertThat(result.get("nodeCode")).isEqualTo("t1");
        assertThat(result.get("nodeName")).isEqualTo("审批");
        assertThat(result.get("formFieldsConfig")).isEqualTo(config);
        assertThat(result.get("flowStatus")).isEqualTo("RUNNING");
        assertThat(result.get("title")).isEqualTo("测试标题");
    }

    @Test
    @DisplayName("getFormRenderData GAP-V2-02: 不传 taskId 时回退到实例当前节点")
    void testGetFormRenderDataWithoutTaskId() {
        FlowInstanceDO ins = new FlowInstanceDO();
        ins.setId(1L);
        ins.setDefinitionId(10L);
        ins.setCurrentNodeCode("n1");
        ins.setCurrentNodeName("部门审批");
        ins.setFlowStatus("RUNNING");
        ins.setTitle("测试标题");
        ins.setVariable(null);
        when(instanceMapper.selectById(1L)).thenReturn(ins);

        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("n1");
        node.setNodeName("部门审批");
        String config = "[{\"field\":\"dept\",\"label\":\"部门\"}]";
        node.setFormFieldsConfig(config);
        when(nodeMapper.selectByCode(10L, "n1")).thenReturn(node);

        Map<String, Object> result = service.getFormRenderData(1L, null);
        assertThat(result).isNotNull();
        assertThat(result.get("nodeCode")).isEqualTo("n1");
        assertThat(result.get("nodeName")).isEqualTo("部门审批");
        assertThat(result.get("formFieldsConfig")).isEqualTo(config);
        assertThat(result.get("taskId")).isNull();
    }

    @Test
    @DisplayName("getFormRenderData GAP-V2-02: 实例不存在抛 NOT_FOUND")
    void testGetFormRenderDataInstanceNotFound() {
        when(instanceMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getFormRenderData(99L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }
}
