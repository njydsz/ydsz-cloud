package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.DeployProcessDTO;
import com.njydsz.pmis.workflow.dto.StartProcessDTO;
import com.njydsz.pmis.workflow.dto.TaskOperateDTO;
import com.njydsz.pmis.workflow.entity.WorkflowBusinessDO;
import com.njydsz.pmis.workflow.mapper.WorkflowBusinessMapper;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkflowServiceImpl 单元测试
 */
@DisplayName("WorkflowServiceImpl 工作流核心测试")
class WorkflowServiceImplTest {

    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;
    private WorkflowBusinessMapper businessMapper;
    private WorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RepositoryService.class);
        runtimeService = mock(RuntimeService.class);
        taskService = mock(TaskService.class);
        historyService = mock(HistoryService.class);
        businessMapper = mock(WorkflowBusinessMapper.class);
        service = new WorkflowServiceImpl(repositoryService, runtimeService, taskService, historyService, businessMapper);
    }

    @Test
    @DisplayName("startProcess 启动后应记录业务关联")
    void startProcess() {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("pi-001");
        when(pi.getProcessDefinitionKey()).thenReturn("pmis_leave");
        when(pi.getProcessDefinitionId()).thenReturn("pd-001");
        when(runtimeService.startProcessInstanceByKey(eq("pmis_leave"), anyString(), any())).thenReturn(pi);
        when(businessMapper.insert(any(WorkflowBusinessDO.class))).thenAnswer(inv -> {
            WorkflowBusinessDO b = inv.getArgument(0);
            b.setId(1L);
            return 1;
        });

        StartProcessDTO dto = new StartProcessDTO();
        dto.setProcessKey("pmis_leave");
        dto.setBusinessType("LEAVE");
        dto.setBusinessId("B-001");
        dto.setBusinessNo("LV-2026-0001");
        dto.setTitle("请假");
        dto.setInitiatorId(10L);
        dto.setInitiatorName("张三");

        String piId = service.startProcess(dto);
        assertThat(piId).isEqualTo("pi-001");

        ArgumentCaptor<WorkflowBusinessDO> captor = ArgumentCaptor.forClass(WorkflowBusinessDO.class);
        org.mockito.Mockito.verify(businessMapper).insert(captor.capture());
        WorkflowBusinessDO b = captor.getValue();
        assertThat(b.getProcessInstanceId()).isEqualTo("pi-001");
        assertThat(b.getBusinessType()).isEqualTo("LEAVE");
        assertThat(b.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("startProcess Flowable 异常应抛 INTERNAL_ERROR")
    void startProcess_flowableError() {
        when(runtimeService.startProcessInstanceByKey(anyString(), anyString(), any()))
                .thenThrow(new FlowableException("engine down"));

        StartProcessDTO dto = new StartProcessDTO();
        dto.setProcessKey("k");
        dto.setBusinessType("T");
        dto.setBusinessId("1");

        assertThatThrownBy(() -> service.startProcess(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("listTodoTasks 应返回按时间倒序的任务")
    void todoTasks() {
        Task task = mockTask("t-1");
        TaskQuery q = mock(TaskQuery.class);
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getProcessDefinitionKey()).thenReturn("pmis_leave");
        org.flowable.engine.runtime.ProcessInstanceQuery piq =
                mock(org.flowable.engine.runtime.ProcessInstanceQuery.class);
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.taskCandidateOrAssigned(anyString())).thenReturn(q);
        when(q.count()).thenReturn(1L);
        when(q.orderByTaskCreateTime()).thenReturn(q);
        when(q.desc()).thenReturn(q);
        when(q.listPage(0, 20)).thenReturn(List.of(task));
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId(anyString())).thenReturn(piq);
        when(piq.singleResult()).thenReturn(pi);

        List<Map<String, Object>> r = service.listTodoTasks(10L, 1, 20);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).get("taskId")).isEqualTo("t-1");
    }

    @Test
    @DisplayName("listTodoTasks userId 为空应返回空列表")
    void todoTasks_empty() {
        assertThat(service.listTodoTasks(null, 1, 20)).isEmpty();
    }

    @Test
    @DisplayName("claimTask 应将任务签给指定用户")
    void claimTask() {
        service.claimTask("t-1", 99L);
        org.mockito.Mockito.verify(taskService).claim("t-1", "99");
    }

    @Test
    @DisplayName("rejectTask 无 targetNodeKey 应终止流程")
    void rejectTask_terminate() {
        TaskQuery q = mock(TaskQuery.class);
        Task t = mock(Task.class);
        when(t.getProcessInstanceId()).thenReturn("pi-1");
        when(t.getTaskDefinitionKey()).thenReturn("userTask1");
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.taskId("t-1")).thenReturn(q);
        when(q.singleResult()).thenReturn(t);

        TaskOperateDTO dto = new TaskOperateDTO();
        dto.setTaskId("t-1");
        dto.setComment("驳回测试");
        service.rejectTask(dto);

        org.mockito.Mockito.verify(runtimeService).deleteProcessInstance(eq("pi-1"), anyString());
        org.mockito.Mockito.verify(businessMapper).updateStatusByInstanceId(eq("pi-1"), eq("TERMINATED"), any(), any(), any());
    }

    @Test
    @DisplayName("rejectTask 指定 targetNodeKey 应使用 ChangeActivityStateBuilder")
    void rejectTask_jump() {
        TaskQuery q = mock(TaskQuery.class);
        Task t = mock(Task.class);
        when(t.getProcessInstanceId()).thenReturn("pi-1");
        when(t.getTaskDefinitionKey()).thenReturn("userTask1");
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.taskId("t-1")).thenReturn(q);
        when(q.singleResult()).thenReturn(t);

        org.flowable.engine.runtime.ChangeActivityStateBuilder cb =
                mock(org.flowable.engine.runtime.ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(cb);
        when(cb.processInstanceId(anyString())).thenReturn(cb);
        when(cb.moveActivityIdTo(anyString(), anyString())).thenReturn(cb);

        TaskOperateDTO dto = new TaskOperateDTO();
        dto.setTaskId("t-1");
        dto.setTargetNodeKey("startEvent");

        service.rejectTask(dto);

        org.mockito.Mockito.verify(cb).moveActivityIdTo("userTask1", "startEvent");
        org.mockito.Mockito.verify(cb).changeState();
    }

    @Test
    @DisplayName("terminateInstance 应更新业务表状态")
    void terminate() {
        org.flowable.engine.history.HistoricProcessInstanceQuery hiq =
                mock(org.flowable.engine.history.HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hiq);
        when(hiq.processInstanceId("pi-1")).thenReturn(hiq);
        when(hiq.singleResult()).thenReturn(null);

        service.terminateInstance("pi-1", "管理员撤回");
        org.mockito.Mockito.verify(runtimeService).deleteProcessInstance(eq("pi-1"), eq("管理员撤回"));
    }

    @Test
    @DisplayName("getByBusiness / getByProcessInstance 应走 Mapper")
    void getByBiz() {
        WorkflowBusinessDO b = new WorkflowBusinessDO();
        b.setId(1L);
        when(businessMapper.selectByBusiness("LEAVE", "B-1")).thenReturn(b);
        when(businessMapper.selectByProcessInstanceId("pi-1")).thenReturn(b);

        assertThat(service.getByBusiness("LEAVE", "B-1").getId()).isEqualTo(1L);
        assertThat(service.getByProcessInstance("pi-1").getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("suspendInstance / activateInstance 应改业务表状态")
    void suspendActivate() {
        service.suspendInstance("pi-1");
        org.mockito.Mockito.verify(businessMapper).updateStatusByInstanceId("pi-1", "SUSPENDED", null, null, null);

        service.activateInstance("pi-1");
        org.mockito.Mockito.verify(businessMapper).updateStatusByInstanceId("pi-1", "RUNNING", null, null, null);
    }

    @Test
    @DisplayName("deploy BPMN XML 为空应抛 BAD_REQUEST")
    void deploy_empty() {
        DeployProcessDTO dto = new DeployProcessDTO();
        dto.setBpmnXml("");
        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("completeTask 应先写 comment 再完成")
    void completeTask() {
        TaskQuery q = mock(TaskQuery.class);
        Task t = mock(Task.class);
        when(t.getProcessInstanceId()).thenReturn("pi-1");
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.taskId("t-1")).thenReturn(q);
        when(q.singleResult()).thenReturn(t);

        TaskOperateDTO dto = new TaskOperateDTO();
        dto.setTaskId("t-1");
        dto.setComment("OK");

        service.completeTask(dto);
        org.mockito.Mockito.verify(taskService).addComment(eq("t-1"), eq("pi-1"), eq("OK"));
        org.mockito.Mockito.verify(taskService).complete("t-1");
    }

    private Task mockTask(String id) {
        Task t = mock(Task.class);
        when(t.getId()).thenReturn(id);
        when(t.getName()).thenReturn("审批任务");
        when(t.getProcessInstanceId()).thenReturn("pi-1");
        when(t.getProcessDefinitionId()).thenReturn("pd-1");
        return t;
    }
}
