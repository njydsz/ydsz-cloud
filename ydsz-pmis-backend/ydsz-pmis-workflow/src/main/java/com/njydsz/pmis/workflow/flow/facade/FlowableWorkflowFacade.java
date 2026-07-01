package com.njydsz.pmis.workflow.flow.facade;

import com.njydsz.pmis.workflow.flow.WorkflowFacade;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 工作流 Facade（双轨之一）
 *
 * <p>对应 pmis.flow.engine=flowable 模式（旧流程保留），所有操作委托给原 WorkflowService（Flowable 7）。
 *
 * <p>该 Facade 维持对老流程（pmis_leave）的兼容。后续逐步迁移到 LocalWorkflowFacade。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "pmis.flow", name = "engine", havingValue = "flowable", matchIfMissing = true)
public class FlowableWorkflowFacade implements WorkflowFacade {

    private final WorkflowService workflowService;

    @Override
    public String startProcess(FlowStartProcessDTO dto) {
        // 转换为 WorkflowService 需要的入参
        Map<String, Object> body = new HashMap<>();
        body.put("businessKey", dto.getBusinessType() + "_" + dto.getBusinessId());
        body.put("processDefinitionKey", dto.getFlowCode());
        body.put("initiator", dto.getInitiatorId());
        body.put("variables", dto.getVariables());
        return workflowService.startProcess(body);
    }

    @Override
    public FlowInstanceViewDTO getByBusiness(String businessType, String businessId) {
        // Flowable 通过 businessKey 反查
        var inst = workflowService.getByBusinessKey(businessType + "_" + businessId);
        if (inst == null) {
            return null;
        }
        FlowInstanceViewDTO v = new FlowInstanceViewDTO();
        v.setBusinessType(businessType);
        v.setBusinessId(businessId);
        v.setFlowStatus(inst.get("status") == null ? "RUNNING" : inst.get("status").toString());
        v.setCurrentNodeCode(inst.get("currentNode") == null ? null : inst.get("currentNode").toString());
        v.setTitle(inst.get("title") == null ? null : inst.get("title").toString());
        return v;
    }

    @Override
    public void completeTask(FlowTaskOperateDTO dto) {
        workflowService.completeTask(dto.getTaskId(),
                dto.getVariables() == null ? Map.of() : dto.getVariables(),
                dto.getComment());
    }

    @Override
    public void claimTask(Long taskId, Long userId) {
        workflowService.claim(taskId, userId);
    }

    @Override
    public void transferTask(FlowTaskOperateDTO dto) {
        workflowService.transfer(dto.getTaskId(), dto.getTargetUserId());
    }

    @Override
    public void delegateTask(FlowTaskOperateDTO dto) {
        workflowService.delegate(dto.getTaskId(), dto.getTargetUserId());
    }

    @Override
    public void rejectTask(FlowTaskOperateDTO dto) {
        workflowService.reject(dto.getTaskId(), dto.getComment(),
                dto.getTargetNodeCode());
    }

    @Override
    public void terminateProcess(String processInstanceId, String reason) {
        workflowService.terminate(processInstanceId, reason);
    }

    @Override
    public void suspendProcess(String processInstanceId) {
        workflowService.suspend(processInstanceId);
    }

    @Override
    public void activateProcess(String processInstanceId) {
        workflowService.activate(processInstanceId);
    }

    @Override
    public List<Map<String, Object>> listTodoTasks(Long userId, int page, int size) {
        return workflowService.listTodoTasks(userId, page, size);
    }

    @Override
    public List<Map<String, Object>> listDoneTasks(Long userId, int page, int size) {
        return workflowService.listDoneTasks(userId, page, size);
    }

    @Override
    public String engineType() {
        return "FLOWABLE";
    }
}
