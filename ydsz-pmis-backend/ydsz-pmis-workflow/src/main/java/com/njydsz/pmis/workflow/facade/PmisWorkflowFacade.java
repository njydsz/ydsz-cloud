package com.njydsz.pmis.workflow.facade;

import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.engine.JsonHelper;
import com.njydsz.pmis.workflow.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
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

    // ============================== P2-4: 流程回放步骤序列 ==============================

    /**
     * P2-4: 生成流程回放步骤序列 — 按时间顺序合并历史任务 + 审计日志 + 当前待办为回放步骤。
     *
     * <p>每一步包含：
     * <ul>
     *   <li>stepIndex — 步骤序号（从 0 开始）</li>
     *   <li>type — HIS_TASK / AUDIT_LOG / CURRENT_TASK / START / END</li>
     *   <li>timestamp — 发生时间</li>
     *   <li>nodeCode / nodeName — 节点</li>
     *   <li>actor / actorName — 操作人</li>
     *   <li>action — 操作动作（PASS/REJECT/AUTO_PASS ...）</li>
     *   <li>comment — 意见</li>
     *   <li>nodeState — 节点回放后状态：ENTERED / PASSED / REJECTED / ACTIVE / SKIPPED</li>
     *   <li>durationMs — 本步耗时（可选）</li>
     * </ul>
     *
     * <p>回放步骤用于驱动前端 FlowDiagramReplay 组件，依次高亮节点 + 展示轨迹事件。
     *
     * @param instanceId 实例 ID（字符串形式）
     * @return 步骤列表（按 timestamp 升序），实例不存在时返回空列表
     */
    public List<Map<String, Object>> getReplaySteps(String instanceId) {
        Long id;
        try {
            id = Long.parseLong(instanceId);
        } catch (NumberFormatException nfe) {
            return Collections.emptyList();
        }
        FlowInstanceDO instance = instanceService.getById(id);
        if (instance == null) {
            return Collections.emptyList();
        }

        // P3-1: 预加载节点坐标映射（key = nodeCode），用于步骤中携带 coordinate 字段
        // 这样前端 FlowDiagramViewer 可以根据坐标自动滚屏到高亮节点
        Map<String, Map<String, Object>> nodeCoordMap = loadNodeCoordinates(instance.getDefinitionId());

        // 1. 起始步骤
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, Object> startStep = new HashMap<>();
        startStep.put("stepIndex", 0);
        startStep.put("type", "START");
        startStep.put("timestamp", instance.getStartAt());
        startStep.put("nodeCode", null);
        startStep.put("nodeName", null);
        startStep.put("actor", instance.getInitiatorId());
        startStep.put("actorName", instance.getInitiatorName());
        startStep.put("action", "START");
        startStep.put("comment", null);
        startStep.put("nodeState", "ENTERED");
        startStep.put("durationMs", null);
        startStep.put("coordinate", null);
        steps.add(startStep);

        // 2. 历史任务步骤
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectByInstanceId(id);
        for (FlowHisTaskDO his : hisTasks) {
            Map<String, Object> step = new HashMap<>();
            step.put("type", "HIS_TASK");
            step.put("timestamp", his.getFinishAt());
            step.put("nodeCode", his.getNodeCode());
            step.put("nodeName", his.getNodeName());
            step.put("actor", his.getAssigneeId());
            step.put("actorName", his.getAssigneeName());
            step.put("action", his.getTaskStatus());
            step.put("comment", his.getComment());
            step.put("nodeState", mapNodeState(his.getTaskStatus()));
            step.put("durationMs", his.getDurationMs());
            // P3-1: 携带节点坐标（BPMNDI 解析结果或设计器保存值）
            step.put("coordinate", nodeCoordMap.get(his.getNodeCode()));
            steps.add(step);
        }

        // 3. 审计日志步骤（URGE/TRANSFER/DELEGATE/JUMP/RECALL 等任务外操作）
        List<FlowAuditLogDO> logs = auditLogMapper.selectByInstanceId(id);
        for (FlowAuditLogDO log : logs) {
            String action = log.getAction();
            if (action == null) continue;
            // 只回放任务外操作（任务自身操作已在 HIS_TASK 中体现）
            if (action.startsWith("TASK_") || action.equals("PASS")
                    || action.equals("REJECT") || action.equals("CLAIM")
                    || action.equals("COMPLETED")) {
                continue;
            }
            Map<String, Object> step = new HashMap<>();
            step.put("type", "AUDIT_LOG");
            step.put("timestamp", log.getOperatedAt());
            step.put("nodeCode", log.getNodeCode());
            step.put("nodeName", log.getNodeName());
            step.put("actor", log.getOperatorId());
            step.put("actorName", log.getOperatorName());
            step.put("action", action);
            step.put("comment", log.getComment());
            step.put("nodeState", "OBSERVED");
            step.put("durationMs", null);
            step.put("coordinate", log.getNodeCode() != null
                    ? nodeCoordMap.get(log.getNodeCode()) : null);
            steps.add(step);
        }

        // 4. 当前待办（RUNNING 实例的最后状态）
        if ("RUNNING".equals(instance.getFlowStatus())
                || "SUSPENDED".equals(instance.getFlowStatus())) {
            List<FlowTaskDO> currentTasks = taskService.listPendingByInstance(id);
            for (FlowTaskDO task : currentTasks) {
                Map<String, Object> step = new HashMap<>();
                step.put("type", "CURRENT_TASK");
                step.put("timestamp", task.getCreatedAt());
                step.put("nodeCode", task.getNodeCode());
                step.put("nodeName", task.getNodeName());
                step.put("actor", task.getAssigneeId());
                step.put("actorName", task.getAssigneeName());
                step.put("action", task.getTaskStatus());
                step.put("comment", task.getComment());
                step.put("nodeState", "ACTIVE");
                step.put("durationMs", task.getDurationMs());
                step.put("coordinate", nodeCoordMap.get(task.getNodeCode()));
                steps.add(step);
            }
        }

        // 5. 终止步骤（COMPLETED/TERMINATED/REJECTED）
        if (instance.getEndAt() != null) {
            Map<String, Object> endStep = new HashMap<>();
            endStep.put("type", "END");
            endStep.put("timestamp", instance.getEndAt());
            endStep.put("nodeCode", instance.getCurrentNodeCode());
            endStep.put("nodeName", instance.getCurrentNodeName());
            endStep.put("actor", null);
            endStep.put("actorName", null);
            endStep.put("action", instance.getFlowStatus());
            endStep.put("comment", null);
            endStep.put("nodeState", "FINISHED");
            endStep.put("durationMs", instance.getDurationMs());
            endStep.put("coordinate", instance.getCurrentNodeCode() != null
                    ? nodeCoordMap.get(instance.getCurrentNodeCode()) : null);
            steps.add(endStep);
        }

        // 6. 按 timestamp 升序排序，null 排最后
        steps.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("timestamp");
            LocalDateTime tb = (LocalDateTime) b.get("timestamp");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ta.compareTo(tb);
        });

        // 7. 重新分配 stepIndex
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).put("stepIndex", i);
        }

        return steps;
    }

    /**
     * P3-1: 加载流程定义下所有节点的坐标映射。
     *
     * <p>key = nodeCode，value = {x, y, width, height}。
     * 来源：pmis_flow_node.coordinate JSON 字段（BPMN 部署时由 BPMNDI 段自动注入，
     * 或前端设计器保存）。
     *
     * <p>解析失败或字段为空时降级为 null，前端回放将不自动滚屏。
     *
     * @param definitionId 流程定义 ID
     * @return 节点坐标映射，无定义时返回空 Map
     */
    private Map<String, Map<String, Object>> loadNodeCoordinates(Long definitionId) {
        if (definitionId == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> detail = definitionService.getDetail(definitionId);
        if (detail == null) {
            return Collections.emptyMap();
        }
        @SuppressWarnings("unchecked")
        List<com.njydsz.pmis.workflow.entity.FlowNodeDO> nodes =
                (List<com.njydsz.pmis.workflow.entity.FlowNodeDO>) detail.get("nodes");
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (com.njydsz.pmis.workflow.entity.FlowNodeDO n : nodes) {
            String coord = n.getCoordinate();
            if (coord == null || coord.isBlank()) {
                continue;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = JsonHelper.fromJson(coord);
                if (parsed != null && !parsed.isEmpty()) {
                    result.put(n.getNodeCode(), parsed);
                }
            } catch (Exception ignore) {
                // coordinate 解析失败：跳过此节点
            }
        }
        return result;
    }

    /** 根据任务状态映射到回放节点状态 */
    private String mapNodeState(String taskStatus) {
        if (taskStatus == null) return "ENTERED";
        return switch (taskStatus) {
            case "PASSED", "COMPLETED" -> "PASSED";
            case "REJECTED" -> "REJECTED";
            case "SKIPPED" -> "SKIPPED";
            case "CANCELLED" -> "SKIPPED";
            case "TIMEOUT" -> "SKIPPED";
            case "PENDING", "CLAIMED" -> "ACTIVE";
            default -> "ENTERED";
        };
    }
}
