package com.njydsz.pmis.workflow.flow.facade;

import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.flow.WorkflowFacade;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.mapper.FlowAuditLogMapper;
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
 * <p>1.1.0 新增能力：加签 / 撤回 / 催办 / 审计轨迹查询。
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
    private final FlowAuditLogMapper auditLogMapper;

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
        // P2-17: 真分页（SQL LIMIT/OFFSET）
        com.njydsz.pmis.common.api.PageResult<FlowTaskDO> pageResult = taskService.listTodoByAssigneePage(
                String.valueOf(userId), SecurityContext.getTenantIdOrDefault(1L), page, size);
        return pageResult.getList().stream().map(this::toMap).toList();
    }

    @Override
    public List<Map<String, Object>> listDoneTasks(Long userId, int page, int size) {
        // P0-3: 已办走历史表（FlowTaskServiceImpl 内部已切换到 FlowHisTaskMapper）
        // P2-17: 真分页（SQL LIMIT/OFFSET）
        com.njydsz.pmis.common.api.PageResult<FlowTaskDO> pageResult = taskService.listDoneByAssigneePage(
                String.valueOf(userId), SecurityContext.getTenantIdOrDefault(1L), page, size);
        return pageResult.getList().stream().map(this::toMap).toList();
    }

    @Override
    public void countersignBeforeTask(FlowTaskOperateDTO dto) {
        taskService.countersignBefore(dto);
    }

    @Override
    public void countersignAfterTask(FlowTaskOperateDTO dto) {
        taskService.countersignAfter(dto);
    }

    @Override
    public List<String> urgeTask(Long instanceId, Long operatorId, String comment) {
        return taskService.urge(instanceId, operatorId, comment);
    }

    @Override
    public boolean recallProcess(String processInstanceId, Long initiatorId) {
        return instanceService.recall(Long.parseLong(processInstanceId), initiatorId);
    }

    @Override
    public List<Map<String, Object>> listAuditTrail(String processInstanceId) {
        Long instanceId = Long.parseLong(processInstanceId);
        List<FlowAuditLogDO> logs = auditLogMapper.selectByInstanceId(instanceId);
        return logs.stream().map(this::auditToMap).toList();
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

    private Map<String, Object> auditToMap(FlowAuditLogDO log) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", log.getId());
        m.put("instanceId", log.getInstanceId());
        m.put("taskId", log.getTaskId());
        m.put("flowCode", log.getFlowCode());
        m.put("businessType", log.getBusinessType());
        m.put("businessId", log.getBusinessId());
        m.put("nodeCode", log.getNodeCode());
        m.put("nodeName", log.getNodeName());
        m.put("action", log.getAction());
        m.put("operatorId", log.getOperatorId());
        m.put("targetId", log.getTargetId());
        m.put("comment", log.getComment());
        m.put("operatedAt", log.getOperatedAt());
        return m;
    }
}
