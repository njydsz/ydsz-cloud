package com.njydsz.pmis.workflow.flow.facade;

import com.njydsz.pmis.workflow.flow.WorkflowFacade;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.flow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自建工作流 Facade（唯一实现）
 *
 * <p>所有操作落 pmis_flow_* 表，对外暴露的 WorkflowFacade 统一接口实现。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PmisWorkflowFacade implements WorkflowFacade {

    private final FlowInstanceService instanceService;
    private final FlowTaskService taskService;

    @Override
    public String startProcess(FlowStartProcessDTO dto) {
        Long id = instanceService.start(dto);
        return id == null ? null : String.valueOf(id);
    }

    @Override
    public FlowInstanceViewDTO getByBusiness(String businessType, String businessId) {
        FlowInstanceDO instance = instanceService.getByBusiness(businessType, businessId);
        if (instance == null) {
            return null;
        }
        List<FlowTaskDO> currentTasks = taskService.listPendingByInstance(instance.getId());
        return instanceService.toView(instance, currentTasks.stream()
                .map(taskService::toView).toList());
    }

    @Override
    public void completeTask(FlowTaskOperateDTO dto) {
        taskService.pass(dto);
    }

    @Override
    public void claimTask(Long taskId, Long userId) {
        taskService.claim(taskId, userId);
    }

    @Override
    public void transferTask(FlowTaskOperateDTO dto) {
        taskService.transfer(dto);
    }

    @Override
    public void delegateTask(FlowTaskOperateDTO dto) {
        taskService.delegate(dto);
    }

    @Override
    public void rejectTask(FlowTaskOperateDTO dto) {
        taskService.reject(dto);
    }

    @Override
    public void terminateProcess(String processInstanceId, String reason) {
        instanceService.terminate(Long.parseLong(processInstanceId), reason);
    }

    @Override
    public void suspendProcess(String processInstanceId) {
        instanceService.suspend(Long.parseLong(processInstanceId));
    }

    @Override
    public void activateProcess(String processInstanceId) {
        instanceService.activate(Long.parseLong(processInstanceId));
    }

    @Override
    public List<Map<String, Object>> listTodoTasks(Long userId, int page, int size) {
        List<FlowTaskDO> tasks = taskService.listTodoByAssignee(String.valueOf(userId), 1L);
        return tasks.stream().map(this::toMap).limit(size).toList();
    }

    @Override
    public List<Map<String, Object>> listDoneTasks(Long userId, int page, int size) {
        List<FlowTaskDO> tasks = taskService.listDoneByAssignee(String.valueOf(userId), 1L);
        return tasks.stream().map(this::toMap).limit(size).toList();
    }

    @Override
    public String engineType() {
        return "PMIS";
    }

    private Map<String, Object> toMap(FlowTaskDO t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("instanceId", t.getInstanceId());
        m.put("flowCode", t.getFlowCode());
        m.put("nodeCode", t.getNodeCode());
        m.put("nodeName", t.getNodeName());
        m.put("title", t.getTitle());
        m.put("assigneeId", t.getAssigneeId());
        m.put("assigneeName", t.getAssigneeName());
        m.put("taskStatus", t.getTaskStatus());
        m.put("businessType", t.getBusinessType());
        m.put("businessId", t.getBusinessId());
        m.put("businessNo", t.getBusinessNo());
        m.put("createdAt", t.getCreatedAt());
        m.put("finishAt", t.getFinishAt());
        return m;
    }
}
