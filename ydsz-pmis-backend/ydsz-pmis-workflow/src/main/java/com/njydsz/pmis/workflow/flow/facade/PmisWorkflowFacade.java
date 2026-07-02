package com.njydsz.pmis.workflow.flow.facade;

import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.flow.WorkflowFacade;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    /** P2-30: 审批轨迹时间线需要查询历史任务 */
    private final FlowHisTaskMapper hisTaskMapper;
    /** P2-22: 流程图查询需要查询流程定义详情 */
    private final FlowDefinitionService definitionService;

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

    // ============================== P2-20: 任务详情查询 ==============================

    @Override
    public Map<String, Object> getTaskDetail(Long taskId) {
        // P2-20: 调用 taskService.getById 获取任务，再用 toView 转换为视图
        FlowTaskDO task = taskService.getById(taskId);
        if (task == null) {
            return null;
        }
        FlowInstanceViewDTO.FlowTaskViewDTO view = taskService.toView(task);
        return taskViewToMap(view);
    }

    // ============================== P2-25: 自由跳转 / P2-26: 批量审批 ==============================

    @Override
    public void jumpTask(FlowTaskOperateDTO dto) {
        taskService.jump(dto);
    }

    @Override
    public void batchPassTasks(List<Long> taskIds, Long userId, String comment) {
        taskService.batchPass(taskIds, userId, comment);
    }

    // ============================== P2-22: 流程图查询（高亮当前节点） ==============================

    /**
     * P2-22: 流程图查询，高亮当前节点
     *
     * @param instanceId 实例 ID（字符串形式）
     * @return 包含 definition / nodes / skips 的 Map，nodes 中每个节点带 active 标记
     */
    public Map<String, Object> getDiagram(String instanceId) {
        Long id = Long.parseLong(instanceId);
        FlowInstanceDO instance = instanceService.getById(id);
        if (instance == null) {
            return null;
        }
        // 通过 definitionService.getDetail 组装 definition + nodes + skips
        Map<String, Object> detail = definitionService.getDetail(instance.getDefinitionId());
        if (detail == null) {
            return null;
        }
        String currentNodeCode = instance.getCurrentNodeCode();
        // 在每个 node 上标注 active: true/false（currentNodeCode 匹配则为 active）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) detail.get("nodes");
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                boolean active = currentNodeCode != null
                        && currentNodeCode.equals(node.get("nodeCode"));
                node.put("active", active);
            }
        }
        // 附带实例当前状态信息
        Map<String, Object> result = new HashMap<>(detail);
        result.put("instanceId", instance.getId());
        result.put("flowStatus", instance.getFlowStatus());
        result.put("currentNodeCode", currentNodeCode);
        result.put("currentNodeName", instance.getCurrentNodeName());
        return result;
    }

    // ============================== P2-30: 审批轨迹时间线查询 ==============================

    /**
     * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
     *
     * <p>每条记录包含：type（HIS_TASK/AUDIT_LOG/CURRENT_TASK）、timestamp、nodeCode、nodeName、
     * assigneeId、assigneeName、action、comment、taskStatus。
     * 按 timestamp 排序（历史任务用 finishAt，审计日志用 operatedAt，当前待办用 createdAt）。
     *
     * @param instanceId 实例 ID（字符串形式）
     * @return 统一时间线列表，实例不存在时返回空列表
     */
    @Override
    public List<Map<String, Object>> getTimeline(String instanceId) {
        Long id = Long.parseLong(instanceId);
        // 1. 获取实例信息
        FlowInstanceDO instance = instanceService.getById(id);
        if (instance == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> timeline = new ArrayList<>();

        // 2. 获取历史任务列表
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectByInstanceId(id);
        for (FlowHisTaskDO his : hisTasks) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", "HIS_TASK");
            entry.put("timestamp", his.getFinishAt());
            entry.put("nodeCode", his.getNodeCode());
            entry.put("nodeName", his.getNodeName());
            entry.put("assigneeId", his.getAssigneeId());
            entry.put("assigneeName", his.getAssigneeName());
            entry.put("action", his.getTaskStatus());
            entry.put("comment", his.getComment());
            entry.put("taskStatus", his.getTaskStatus());
            timeline.add(entry);
        }

        // 3. 获取审计日志列表
        List<FlowAuditLogDO> logs = auditLogMapper.selectByInstanceId(id);
        for (FlowAuditLogDO log : logs) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", "AUDIT_LOG");
            entry.put("timestamp", log.getOperatedAt());
            entry.put("nodeCode", log.getNodeCode());
            entry.put("nodeName", log.getNodeName());
            entry.put("assigneeId", log.getOperatorId() == null ? null
                    : String.valueOf(log.getOperatorId()));
            entry.put("assigneeName", log.getOperatorName());
            entry.put("action", log.getAction());
            entry.put("comment", log.getComment());
            entry.put("taskStatus", null);
            timeline.add(entry);
        }

        // 4. 获取当前待办任务
        List<FlowTaskDO> currentTasks = taskService.listPendingByInstance(id);
        for (FlowTaskDO task : currentTasks) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("type", "CURRENT_TASK");
            entry.put("timestamp", task.getCreatedAt());
            entry.put("nodeCode", task.getNodeCode());
            entry.put("nodeName", task.getNodeName());
            entry.put("assigneeId", task.getAssigneeId());
            entry.put("assigneeName", task.getAssigneeName());
            entry.put("action", task.getTaskStatus());
            entry.put("comment", task.getComment());
            entry.put("taskStatus", task.getTaskStatus());
            timeline.add(entry);
        }

        // 5. 按 timestamp 排序（null 排最后），保持同时间戳的插入顺序（稳定排序）
        timeline.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("timestamp");
            LocalDateTime tb = (LocalDateTime) b.get("timestamp");
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return ta.compareTo(tb);
        });

        return timeline;
    }

    // ============================== 私有辅助 ==============================

    /** 将 FlowTaskViewDTO 转换为 Map */
    private Map<String, Object> taskViewToMap(FlowInstanceViewDTO.FlowTaskViewDTO v) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", v.getId());
        m.put("nodeCode", v.getNodeCode());
        m.put("nodeName", v.getNodeName());
        m.put("nodeType", v.getNodeType());
        m.put("assigneeType", v.getAssigneeType());
        m.put("assigneeId", v.getAssigneeId());
        m.put("assigneeName", v.getAssigneeName());
        m.put("performType", v.getPerformType());
        m.put("taskStatus", v.getTaskStatus());
        m.put("comment", v.getComment());
        m.put("createAt", v.getCreateAt());
        m.put("claimAt", v.getClaimAt());
        m.put("finishAt", v.getFinishAt());
        m.put("durationMs", v.getDurationMs());
        m.put("dueAt", v.getDueAt());
        return m;
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
