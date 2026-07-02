package com.njydsz.pmis.workflow.flow.facade;

import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.flow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.flow.service.FlowTaskService;
import com.njydsz.pmis.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PmisWorkflowFacade 单元测试
 *
 * <p>验证 Facade 对自建工作流服务的委托与转换逻辑。
 *
 * <p>1.1.0 新增：加签 / 撤回 / 催办 / 审计轨迹。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PmisWorkflowFacade 单元测试")
class PmisWorkflowFacadeTest {

    private FlowInstanceService instanceService;
    private FlowTaskService taskService;
    private FlowAuditLogMapper auditLogMapper;
    private FlowHisTaskMapper hisTaskMapper;
    private FlowDefinitionService definitionService;
    private PmisWorkflowFacade facade;

    @BeforeEach
    void setUp() {
        instanceService = mock(FlowInstanceService.class);
        taskService = mock(FlowTaskService.class);
        auditLogMapper = mock(FlowAuditLogMapper.class);
        hisTaskMapper = mock(FlowHisTaskMapper.class);
        definitionService = mock(FlowDefinitionService.class);
        facade = new PmisWorkflowFacade(instanceService, taskService, auditLogMapper,
                hisTaskMapper, definitionService);
    }

    @Test
    @DisplayName("startProcess 应返回 String 形式的 instanceId")
    void testStartProcess() {
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("project_initiation");
        dto.setBusinessType("initiation");
        dto.setBusinessId("100");
        when(instanceService.start(any(FlowStartProcessDTO.class))).thenReturn(123L);

        String result = facade.startProcess(dto);
        assertThat(result).isEqualTo("123");
        verify(instanceService, times(1)).start(dto);
    }

    @Test
    @DisplayName("startProcess 返回 null 时 facade 应返回 null")
    void testStartProcessNull() {
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        when(instanceService.start(any())).thenReturn(null);
        assertThat(facade.startProcess(dto)).isNull();
    }

    @Test
    @DisplayName("getByBusiness 无实例应返回 null")
    void testGetByBusinessEmpty() {
        when(instanceService.getByBusiness(eq("initiation"), eq("1"))).thenReturn(null);
        assertThat(facade.getByBusiness("initiation", "1")).isNull();
    }

    @Test
    @DisplayName("getByBusiness 应组装视图 DTO")
    void testGetByBusiness() {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(10L);
        instance.setFlowCode("project_initiation");
        instance.setBusinessType("initiation");
        instance.setBusinessId("1");
        instance.setFlowStatus("RUNNING");
        instance.setCurrentNodeCode("t1");
        when(instanceService.getByBusiness("initiation", "1")).thenReturn(instance);

        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setInstanceId(10L);
        task.setNodeCode("t1");
        task.setNodeName("审批");
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        task.setBusinessType("initiation");
        task.setBusinessId("1");
        when(taskService.listPendingByInstance(10L)).thenReturn(List.of(task));

        FlowInstanceViewDTO.FlowTaskViewDTO taskView = FlowInstanceViewDTO.FlowTaskViewDTO.builder()
                .id(1L)
                .nodeCode("t1")
                .nodeName("审批")
                .build();
        when(taskService.toView(task)).thenReturn(taskView);

        FlowInstanceViewDTO expected = FlowInstanceViewDTO.builder()
                .id(10L)
                .flowCode("project_initiation")
                .businessType("initiation")
                .businessId("1")
                .flowStatus("RUNNING")
                .currentNodeCode("t1")
                .currentTasks(List.of(taskView))
                .build();
        when(instanceService.toView(eq(instance), anyList())).thenReturn(expected);

        FlowInstanceViewDTO view = facade.getByBusiness("initiation", "1");
        assertThat(view).isNotNull();
        assertThat(view.getId()).isEqualTo(10L);
        assertThat(view.getFlowStatus()).isEqualTo("RUNNING");
        assertThat(view.getCurrentTasks()).hasSize(1);
    }

    @Test
    @DisplayName("completeTask 应委托 taskService.pass")
    void testCompleteTask() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setAction("PASS");
        facade.completeTask(dto);
        verify(taskService, times(1)).pass(dto);
    }

    @Test
    @DisplayName("claimTask 应调用 taskService.claim")
    void testClaimTask() {
        facade.claimTask(1L, 100L);
        verify(taskService, times(1)).claim(1L, 100L);
    }

    @Test
    @DisplayName("transferTask / delegateTask / rejectTask 委托")
    void testTaskOperations() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);

        facade.transferTask(dto);
        facade.delegateTask(dto);
        facade.rejectTask(dto);

        verify(taskService, times(1)).transfer(dto);
        verify(taskService, times(1)).delegate(dto);
        verify(taskService, times(1)).reject(dto);
    }

    @Test
    @DisplayName("terminateProcess / suspendProcess / activateProcess 应解析 String id 为 Long")
    void testProcessStateOperations() {
        facade.terminateProcess("100", "管理员撤回");
        facade.suspendProcess("100");
        facade.activateProcess("100");

        verify(instanceService).terminate(100L, "管理员撤回");
        verify(instanceService).suspend(100L);
        verify(instanceService).activate(100L);
    }

    @Test
    @DisplayName("listTodoTasks 应转换 FlowTaskDO 为 Map 列表")
    void testListTodoTasks() {
        FlowTaskDO t1 = new FlowTaskDO();
        t1.setId(1L);
        t1.setInstanceId(10L);
        t1.setNodeCode("t1");
        t1.setNodeName("审批");
        t1.setTaskStatus("PENDING");
        t1.setAssigneeId("1001");
        t1.setBusinessType("initiation");
        t1.setBusinessId("1");
        when(taskService.listTodoByAssigneePage(eq("1001"), any(), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(t1), 1L, 1, 20));

        List<Map<String, Object>> result = facade.listTodoTasks(1001L, 1, 20);
        assertThat(result).hasSize(1);
        Map<String, Object> m = result.get(0);
        assertThat(m.get("id")).isEqualTo(1L);
        assertThat(m.get("instanceId")).isEqualTo(10L);
        assertThat(m.get("nodeCode")).isEqualTo("t1");
        assertThat(m.get("taskStatus")).isEqualTo("PENDING");
        assertThat(m.get("assigneeId")).isEqualTo("1001");
    }

    @Test
    @DisplayName("listDoneTasks 应转换 FlowTaskDO 为 Map 列表")
    void testListDoneTasks() {
        FlowTaskDO t1 = new FlowTaskDO();
        t1.setId(1L);
        t1.setTaskStatus("COMPLETED");
        t1.setAssigneeId("1001");
        when(taskService.listDoneByAssigneePage(eq("1001"), any(), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(t1), 1L, 1, 20));

        List<Map<String, Object>> result = facade.listDoneTasks(1001L, 1, 20);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("taskStatus")).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("engineType 应返回 PMIS")
    void testEngineType() {
        assertThat(facade.engineType()).isEqualTo("PMIS");
    }

    @Test
    @DisplayName("ArgumentCaptor 验证 DTO 完整传递")
    void testDtoPassthrough() {
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("contract_change");
        dto.setBusinessType("contract");
        dto.setBusinessId("C-001");
        dto.setVariables(new HashMap<>());
        when(instanceService.start(any())).thenReturn(1L);

        facade.startProcess(dto);

        ArgumentCaptor<FlowStartProcessDTO> captor =
                ArgumentCaptor.forClass(FlowStartProcessDTO.class);
        verify(instanceService).start(captor.capture());
        assertThat(captor.getValue().getFlowCode()).isEqualTo("contract_change");
        assertThat(captor.getValue().getBusinessId()).isEqualTo("C-001");
    }

    // ============== P1-7: 加签 ==============

    @Test
    @DisplayName("countersignBeforeTask 应委托 taskService.countersignBefore")
    void testCountersignBeforeTask() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(888L);
        facade.countersignBeforeTask(dto);
        verify(taskService, times(1)).countersignBefore(dto);
    }

    @Test
    @DisplayName("countersignAfterTask 应委托 taskService.countersignAfter")
    void testCountersignAfterTask() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);
        dto.setTargetUserId(999L);
        facade.countersignAfterTask(dto);
        verify(taskService, times(1)).countersignAfter(dto);
    }

    // ============== P1-9: 催办 ==============

    @Test
    @DisplayName("urgeTask 应委托 taskService.urge 并返回被催办人列表")
    void testUrgeTask() {
        when(taskService.urge(eq(100L), eq(7L), eq("请尽快处理")))
                .thenReturn(List.of("1001", "1002"));
        List<String> urged = facade.urgeTask(100L, 7L, "请尽快处理");
        assertThat(urged).containsExactly("1001", "1002");
        verify(taskService, times(1)).urge(100L, 7L, "请尽快处理");
    }

    // ============== P1-8: 撤回 ==============

    @Test
    @DisplayName("recallProcess 应解析 String id 为 Long 并委托 instanceService.recall")
    void testRecallProcess() {
        when(instanceService.recall(eq(100L), eq(7L))).thenReturn(true);
        boolean result = facade.recallProcess("100", 7L);
        assertThat(result).isTrue();
        verify(instanceService, times(1)).recall(100L, 7L);
    }

    @Test
    @DisplayName("recallProcess 失败时返回 false")
    void testRecallProcessFailed() {
        when(instanceService.recall(eq(100L), eq(7L))).thenReturn(false);
        boolean result = facade.recallProcess("100", 7L);
        assertThat(result).isFalse();
    }

    // ============== P1-13: 审计轨迹 ==============

    @Test
    @DisplayName("listAuditTrail 应委托 auditLogMapper 并转换为 Map 列表")
    void testListAuditTrail() {
        FlowAuditLogDO log1 = new FlowAuditLogDO();
        log1.setId(1L);
        log1.setInstanceId(100L);
        log1.setTaskId(10L);
        log1.setAction("PASS");
        log1.setOperatorId(7L);
        log1.setComment("同意");
        log1.setOperatedAt(LocalDateTime.now());
        when(auditLogMapper.selectByInstanceId(100L)).thenReturn(List.of(log1));

        List<Map<String, Object>> result = facade.listAuditTrail("100");
        assertThat(result).hasSize(1);
        Map<String, Object> m = result.get(0);
        assertThat(m.get("id")).isEqualTo(1L);
        assertThat(m.get("instanceId")).isEqualTo(100L);
        assertThat(m.get("taskId")).isEqualTo(10L);
        assertThat(m.get("action")).isEqualTo("PASS");
        assertThat(m.get("operatorId")).isEqualTo(7L);
        assertThat(m.get("comment")).isEqualTo("同意");
    }

    @Test
    @DisplayName("listAuditTrail 无记录时返回空列表")
    void testListAuditTrailEmpty() {
        when(auditLogMapper.selectByInstanceId(99L)).thenReturn(List.of());
        List<Map<String, Object>> result = facade.listAuditTrail("99");
        assertThat(result).isEmpty();
    }

    // ============== P2-20: 任务详情查询 ==============

    @Test
    @DisplayName("getTaskDetail 任务不存在应返回 null")
    void testGetTaskDetailNotFound() {
        when(taskService.getById(99L)).thenReturn(null);
        assertThat(facade.getTaskDetail(99L)).isNull();
    }

    @Test
    @DisplayName("getTaskDetail 应返回任务详情 Map")
    void testGetTaskDetail() {
        FlowTaskDO task = new FlowTaskDO();
        task.setId(1L);
        task.setNodeCode("t1");
        task.setNodeName("审批");
        task.setTaskStatus("PENDING");
        task.setAssigneeId("1001");
        when(taskService.getById(1L)).thenReturn(task);

        FlowInstanceViewDTO.FlowTaskViewDTO view = FlowInstanceViewDTO.FlowTaskViewDTO.builder()
                .id(1L)
                .nodeCode("t1")
                .nodeName("审批")
                .taskStatus("PENDING")
                .assigneeId("1001")
                .build();
        when(taskService.toView(task)).thenReturn(view);

        Map<String, Object> result = facade.getTaskDetail(1L);
        assertThat(result).isNotNull();
        assertThat(result.get("id")).isEqualTo(1L);
        assertThat(result.get("nodeCode")).isEqualTo("t1");
        assertThat(result.get("nodeName")).isEqualTo("审批");
        assertThat(result.get("taskStatus")).isEqualTo("PENDING");
        assertThat(result.get("assigneeId")).isEqualTo("1001");
    }

    // ============== P2-22: 流程图查询（高亮当前节点） ==============

    @Test
    @DisplayName("getDiagram 实例不存在应返回 null")
    void testGetDiagramInstanceNotFound() {
        when(instanceService.getById(99L)).thenReturn(null);
        assertThat(facade.getDiagram("99")).isNull();
    }

    @Test
    @DisplayName("getDiagram 应返回带 active 标记的节点列表")
    void testGetDiagram() {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(100L);
        instance.setDefinitionId(1L);
        instance.setFlowStatus("RUNNING");
        instance.setCurrentNodeCode("t1");
        instance.setCurrentNodeName("审批");
        when(instanceService.getById(100L)).thenReturn(instance);

        // 模拟 definitionService.getDetail 返回 definition + nodes + skips
        Map<String, Object> node1 = new HashMap<>();
        node1.put("nodeCode", "s1");
        node1.put("nodeName", "开始");
        Map<String, Object> node2 = new HashMap<>();
        node2.put("nodeCode", "t1");
        node2.put("nodeName", "审批");
        Map<String, Object> node3 = new HashMap<>();
        node3.put("nodeCode", "e1");
        node3.put("nodeName", "结束");

        Map<String, Object> detail = new HashMap<>();
        detail.put("definition", new Object());
        detail.put("nodes", new java.util.ArrayList<>(List.of(node1, node2, node3)));
        detail.put("skips", List.of());
        when(definitionService.getDetail(1L)).thenReturn(detail);

        Map<String, Object> result = facade.getDiagram("100");
        assertThat(result).isNotNull();
        assertThat(result.get("instanceId")).isEqualTo(100L);
        assertThat(result.get("flowStatus")).isEqualTo("RUNNING");
        assertThat(result.get("currentNodeCode")).isEqualTo("t1");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) result.get("nodes");
        assertThat(nodes).hasSize(3);
        // s1 非当前节点 → active=false
        assertThat(nodes.get(0).get("active")).isEqualTo(false);
        // t1 是当前节点 → active=true
        assertThat(nodes.get(1).get("active")).isEqualTo(true);
        // e1 非当前节点 → active=false
        assertThat(nodes.get(2).get("active")).isEqualTo(false);
    }

    @Test
    @DisplayName("getDiagram 定义不存在（getDetail 返回 null）应返回 null")
    void testGetDiagramDefinitionNotFound() {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(100L);
        instance.setDefinitionId(1L);
        instance.setCurrentNodeCode("t1");
        when(instanceService.getById(100L)).thenReturn(instance);
        when(definitionService.getDetail(1L)).thenReturn(null);

        assertThat(facade.getDiagram("100")).isNull();
    }

    // ============== P2-30: 审批轨迹时间线查询 ==============

    @Test
    @DisplayName("getTimeline 实例不存在应返回空列表")
    void testGetTimelineInstanceNotFound() {
        when(instanceService.getById(99L)).thenReturn(null);
        assertThat(facade.getTimeline("99")).isEmpty();
    }

    @Test
    @DisplayName("getTimeline 应合并历史任务+审计日志+当前待办为统一时间线并按时间排序")
    void testGetTimeline() {
        // 实例存在
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(100L);
        when(instanceService.getById(100L)).thenReturn(instance);

        // 历史任务（finishAt = 10:00）
        FlowHisTaskDO hisTask = new FlowHisTaskDO();
        hisTask.setTaskId(10L);
        hisTask.setNodeCode("t1");
        hisTask.setNodeName("审批");
        hisTask.setAssigneeId("1001");
        hisTask.setAssigneeName("张三");
        hisTask.setTaskStatus("COMPLETED");
        hisTask.setComment("同意");
        hisTask.setFinishAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(hisTaskMapper.selectByInstanceId(100L)).thenReturn(List.of(hisTask));

        // 审计日志（operatedAt = 10:05）
        FlowAuditLogDO log = new FlowAuditLogDO();
        log.setId(1L);
        log.setInstanceId(100L);
        log.setNodeCode("t1");
        log.setNodeName("审批");
        log.setAction("PASS");
        log.setOperatorId(7L);
        log.setComment("同意");
        log.setOperatedAt(LocalDateTime.of(2026, 1, 1, 10, 5));
        when(auditLogMapper.selectByInstanceId(100L)).thenReturn(List.of(log));

        // 当前待办（createdAt = 11:00）
        FlowTaskDO task = new FlowTaskDO();
        task.setId(20L);
        task.setNodeCode("t2");
        task.setNodeName("复审");
        task.setAssigneeId("1002");
        task.setAssigneeName("李四");
        task.setTaskStatus("PENDING");
        task.setCreatedAt(LocalDateTime.of(2026, 1, 1, 11, 0));
        when(taskService.listPendingByInstance(100L)).thenReturn(List.of(task));

        List<Map<String, Object>> timeline = facade.getTimeline("100");
        assertThat(timeline).hasSize(3);

        // 按时间排序：HIS_TASK(10:00) → AUDIT_LOG(10:05) → CURRENT_TASK(11:00)
        Map<String, Object> hisEntry = timeline.get(0);
        assertThat(hisEntry.get("type")).isEqualTo("HIS_TASK");
        assertThat(hisEntry.get("nodeCode")).isEqualTo("t1");
        assertThat(hisEntry.get("assigneeId")).isEqualTo("1001");
        assertThat(hisEntry.get("action")).isEqualTo("COMPLETED");
        assertThat(hisEntry.get("taskStatus")).isEqualTo("COMPLETED");

        Map<String, Object> logEntry = timeline.get(1);
        assertThat(logEntry.get("type")).isEqualTo("AUDIT_LOG");
        assertThat(logEntry.get("action")).isEqualTo("PASS");
        assertThat(logEntry.get("assigneeId")).isEqualTo("7");
        assertThat(logEntry.get("taskStatus")).isNull();

        Map<String, Object> taskEntry = timeline.get(2);
        assertThat(taskEntry.get("type")).isEqualTo("CURRENT_TASK");
        assertThat(taskEntry.get("nodeCode")).isEqualTo("t2");
        assertThat(taskEntry.get("taskStatus")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getTimeline 无历史/日志/待办时返回空列表")
    void testGetTimelineEmpty() {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(100L);
        when(instanceService.getById(100L)).thenReturn(instance);
        when(hisTaskMapper.selectByInstanceId(100L)).thenReturn(List.of());
        when(auditLogMapper.selectByInstanceId(100L)).thenReturn(List.of());
        when(taskService.listPendingByInstance(100L)).thenReturn(List.of());

        List<Map<String, Object>> timeline = facade.getTimeline("100");
        assertThat(timeline).isEmpty();
    }
}
