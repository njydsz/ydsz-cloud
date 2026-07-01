package com.njydsz.pmis.workflow.flow.facade;

import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.flow.service.FlowTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
 */
@DisplayName("PmisWorkflowFacade 单元测试")
class PmisWorkflowFacadeTest {

    private FlowInstanceService instanceService;
    private FlowTaskService taskService;
    private PmisWorkflowFacade facade;

    @BeforeEach
    void setUp() {
        instanceService = mock(FlowInstanceService.class);
        taskService = mock(FlowTaskService.class);
        facade = new PmisWorkflowFacade(instanceService, taskService);
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
        when(taskService.listTodoByAssignee(eq("1001"), anyLong())).thenReturn(List.of(t1));

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
        when(taskService.listDoneByAssignee(eq("1001"), anyLong())).thenReturn(List.of(t1));

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
}
