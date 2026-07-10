package com.njydsz.pmis.workflow.service.impl.integration;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.integration.EmbeddedApprovalActionDTO;
import com.njydsz.pmis.workflow.dto.integration.EmbeddedApprovalViewDTO;
import com.njydsz.pmis.workflow.dto.instance.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.instance.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.instance.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.instance.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.ai.FlowAiAssistService;
import com.njydsz.pmis.workflow.service.integration.FlowEmbeddedApprovalService;
import com.njydsz.pmis.workflow.service.instance.FlowInstanceService;
import com.njydsz.pmis.workflow.service.instance.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2-2 嵌入式审批服务实现
 *
 * <p>业务页内嵌场景：单次接口拉齐"实例+图+待办+历史"，并通过快捷操作
 * 免去业务方感知 taskId。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEmbeddedApprovalServiceImpl implements FlowEmbeddedApprovalService {

    private final FlowInstanceService instanceService;
    private final FlowTaskService taskService;
    private final FlowAiAssistService aiAssistService;
    /** P2-2: 历史任务 mapper（嵌入式审批面板加载审批轨迹） */
    private final FlowHisTaskMapper hisTaskMapper;

    /** 操作人角色：发起人 */
    private static final String ROLE_INITIATOR = "INITIATOR";
    /** 操作人角色：当前审批人 */
    private static final String ROLE_APPROVER = "APPROVER";
    /** 操作人角色：观察者（无操作权限） */
    private static final String ROLE_OBSERVER = "OBSERVER";

    @Override
    @Transactional(readOnly = true)
    public EmbeddedApprovalViewDTO loadPanel(String businessType, String businessId, String userId) {
        if (businessType == null || businessType.isBlank()
                || businessId == null || businessId.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "businessType / businessId 不能为空");
        }

        // 1. 查流程实例
        FlowInstanceDO instance = instanceService.getByBusiness(businessType, businessId);
        if (instance == null) {
            // 未发起流程，返回空面板（前端可点击"发起审批"按钮）
            return EmbeddedApprovalViewDTO.builder()
                    .businessType(businessType)
                    .businessId(businessId)
                    .instance(null)
                    .diagram(null)
                    .currentTasks(Collections.emptyList())
                    .history(Collections.emptyList())
                    .myRole(ROLE_OBSERVER)
                    .actions(List.of("SUBMIT"))
                    .aiAvailable(safeCheckAi())
                    .canRecall(false)
                    .finished(false)
                    .message("未发起流程")
                    .build();
        }

        // 2. 查当前待办
        List<FlowRunTaskDO> pending = taskService.listPendingByInstance(instance.getId());

        // 3. 计算 myRole / mine / actions
        String myRole = computeMyRole(instance, pending, userId);
        List<EmbeddedApprovalViewDTO.CurrentTaskView> currentTaskViews = buildCurrentTaskViews(pending, userId);
        List<String> actions = computeActions(instance, pending, userId);
        boolean canRecall = canRecall(instance, pending, userId);
        boolean finished = FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished();

        // 4. 查历史轨迹（合并历史任务 + 审计日志）
        List<Map<String, Object>> history = loadHistory(instance.getId());

        // 5. 流程图（带高亮当前节点）
        Map<String, Object> diagram = loadDiagram(instance);

        // 6. 转 instance view
        List<FlowInstanceViewDTO.FlowTaskViewDTO> taskViews = currentTaskViews.stream()
                .map(t -> FlowInstanceViewDTO.FlowTaskViewDTO.builder()
                        .id(t.getTaskId())
                        .nodeCode(t.getNodeCode())
                        .nodeName(t.getNodeName())
                        .nodeType(t.getNodeType())
                        .assigneeType(t.getAssigneeType())
                        .assigneeId(t.getAssigneeId())
                        .assigneeName(t.getAssigneeName())
                        .performType(t.getPerformType())
                        .taskStatus(t.getTaskStatus())
                        .createAt(t.getCreateAt())
                        .dueAt(t.getDueAt())
                        .build())
                .toList();
        FlowInstanceViewDTO instanceView = instanceService.toView(instance, taskViews);

        return EmbeddedApprovalViewDTO.builder()
                .businessType(businessType)
                .businessId(businessId)
                .instance(instanceView)
                .diagram(diagram)
                .currentTasks(currentTaskViews)
                .history(history)
                .myRole(myRole)
                .actions(actions)
                .aiAvailable(safeCheckAi())
                .canRecall(canRecall)
                .finished(finished)
                .message(finished ? "流程已结束" : "流程进行中")
                .build();
    }

    @Override
    public void quickAction(EmbeddedApprovalActionDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_afb63fa5");
        }
        String action = dto.getAction() == null ? "" : dto.getAction().toUpperCase();
        FlowInstanceDO instance = instanceService.getByBusiness(dto.getBusinessType(), dto.getBusinessId());
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_b72e8598");
        }
        if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BIZ_ERROR, "error.workflow.msg_8243ec9a");
        }

        switch (action) {
            case "PASS":
            case "REJECT":
            case "TRANSFER":
            case "DELEGATE": {
                FlowRunTaskDO mine = findMyTask(instance.getId(), dto.getUserId());
                if (mine == null) {
                    throw new BizException(BizErrorCode.FORBIDDEN,
                            "error.workflow.msg_1440b2f2");
                }
                FlowTaskOperateDTO op = new FlowTaskOperateDTO();
                op.setTaskId(mine.getId());
                op.setUserId(dto.getUserId());
                op.setUserName(dto.getUserName());
                op.setComment(dto.getComment());
                op.setCommentType(dto.getCommentType());
                op.setTargetUserId(dto.getTargetUserId());
                op.setTargetUserName(dto.getTargetUserName());
                op.setVariables(dto.getVariables());
                op.setTenantId(dto.getTenantId());
                if ("PASS".equals(action)) {
                    taskService.pass(op);
                } else if ("REJECT".equals(action)) {
                    taskService.reject(op);
                } else if ("TRANSFER".equals(action)) {
                    if (dto.getTargetUserId() == null) {
                        throw new BizException(BizErrorCode.BAD_REQUEST,
                                "error.workflow.msg_df306e2b");
                    }
                    taskService.transfer(op);
                } else { // DELEGATE
                    if (dto.getTargetUserId() == null) {
                        throw new BizException(BizErrorCode.BAD_REQUEST,
                                "委派操作必须指定 targetUserId");
                    }
                    taskService.delegate(op);
                }
                break;
            }
            case "URGE": {
                List<String> urged = taskService.urge(instance.getId(), dto.getUserId(), dto.getComment());
                log.info("[EmbeddedApproval] URGE instance={} operator={} count={}",
                        instance.getId(), dto.getUserId(), urged.size());
                break;
            }
            case "WITHDRAW": {
                boolean ok = instanceService.recall(instance.getId(), dto.getUserId());
                if (!ok) {
                    throw new BizException(BizErrorCode.BIZ_ERROR,
                            "error.workflow.msg_ad7c50c2");
                }
                break;
            }
            default:
                throw new BizException(BizErrorCode.BAD_REQUEST,
                        "error.workflow.msg_3adf9016", dto.getAction());
        }
    }

    // ============ 私有方法 ============

    /**
     * 计算当前用户在流程中的角色
     */
    private String computeMyRole(FlowInstanceDO instance, List<FlowRunTaskDO> pending, String userId) {
        if (userId == null) {
            return ROLE_OBSERVER;
        }
        if (userId.equals(instance.getInitiatorId())) {
            return ROLE_INITIATOR;
        }
        if (pending != null) {
            for (FlowRunTaskDO t : pending) {
                if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
                    return ROLE_APPROVER;
                }
            }
        }
        return ROLE_OBSERVER;
    }

    /**
     * 计算当前用户可执行的操作
     */
    private List<String> computeActions(FlowInstanceDO instance, List<FlowRunTaskDO> pending, String userId) {
        List<String> actions = new ArrayList<>();
        if (userId == null) {
            return actions;
        }
        boolean isInitiator = userId.equals(instance.getInitiatorId());
        boolean isFinished = FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished();
        boolean canActAsApprover = false;
        if (pending != null) {
            for (FlowRunTaskDO t : pending) {
                if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
                    canActAsApprover = true;
                    break;
                }
            }
        }

        if (isFinished) {
            // 流程已结束，只能查看
            return actions;
        }

        if (canActAsApprover) {
            actions.add("PASS");
            actions.add("REJECT");
            actions.add("TRANSFER");
            actions.add("DELEGATE");
            actions.add("URGE");
        }
        if (isInitiator) {
            // 发起人可催办
            if (!actions.contains("URGE")) {
                actions.add("URGE");
            }
            // 撤回（仅当下一节点未处理）
            if (canRecall(instance, pending, userId)) {
                actions.add("WITHDRAW");
            }
        }
        return actions;
    }

    /**
     * 当前用户是否可撤回（P0-4 修复：补全下游已处理判断）
     *
     * <p>撤回条件：
     * <ol>
     *   <li>操作人是发起人</li>
     *   <li>实例未结束（RUNNING）</li>
     *   <li>所有 PENDING 任务均未签收（CLAIMED）</li>
     *   <li>【P0-4 新增】无已完成的历史任务 — 如果有审批人已处理过任务，说明流程已推进到下游，不可撤回</li>
     * </ol>
     */
    private boolean canRecall(FlowInstanceDO instance, List<FlowRunTaskDO> pending, String userId) {
        if (userId == null) {
            return false;
        }
        if (!userId.equals(instance.getInitiatorId())) {
            return false;
        }
        if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
            return false;
        }
        // 撤回前置条件：当前节点的 PENDING 任务全部属于发起人（没有真实审批人介入）
        // 简化判断：所有 PENDING 任务均未签收（CLAIMED）
        if (pending == null) {
            return false;
        }
        for (FlowRunTaskDO t : pending) {
            if (FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())) {
                return false;
            }
        }
        // P0-4: 检查是否有已完成的历史任务（排除 START 节点）— 有则说明审批人已处理过，流程已推进，不可撤回
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectByInstanceId(instance.getId());
        if (hisTasks != null) {
            // 排除 START(0) 节点归档记录（发起人提交产生的），只检查是否有真实审批人处理过
            boolean hasApprovalHistory = hisTasks.stream()
                    .anyMatch(h -> h.getNodeType() != null && h.getNodeType() != 0);
            if (hasApprovalHistory) {
                log.debug("[EmbeddedApproval] 实例已有审批历史任务，不可撤回 instanceId={}", instance.getId());
                return false;
            }
        }
        return true;
    }

    /**
     * 判定 task 是否属于指定 userId（USER/ROLE/DEPT 等多种 assigneeType 均纳入判断）
     */
    private boolean isMine(FlowRunTaskDO t, String userId) {
        if (t == null || userId == null) {
            return false;
        }
        String assigneeType = t.getAssigneeType();
        String assigneeId = t.getAssigneeId();
        String uid = String.valueOf(userId);
        if (assigneeType == null || "USER".equalsIgnoreCase(assigneeType)) {
            return uid.equals(assigneeId);
        }
        // ROLE / DEPT 场景：assigneeId 形如 "1,2,3"，简化判断：包含即可（实际由 assignee resolver 解析）
        if (assigneeId != null) {
            for (String s : assigneeId.split(",")) {
                if (uid.equals(s.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 找到当前用户 mine 的第一个未完成任务
     */
    private FlowRunTaskDO findMyTask(String instanceId, String userId) {
        if (userId == null) {
            return null;
        }
        List<FlowRunTaskDO> pending = taskService.listPendingByInstance(instanceId);
        for (FlowRunTaskDO t : pending) {
            if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
                return t;
            }
        }
        return null;
    }

    /**
     * 构造当前待办视图
     */
    private List<EmbeddedApprovalViewDTO.CurrentTaskView> buildCurrentTaskViews(
            List<FlowRunTaskDO> pending, String userId) {
        if (pending == null || pending.isEmpty()) {
            return Collections.emptyList();
        }
        List<EmbeddedApprovalViewDTO.CurrentTaskView> out = new ArrayList<>(pending.size());
        for (FlowRunTaskDO t : pending) {
            out.add(EmbeddedApprovalViewDTO.CurrentTaskView.builder()
                    .taskId(t.getId())
                    .nodeCode(t.getNodeCode())
                    .nodeName(t.getNodeName())
                    .nodeType(t.getNodeType())
                    .assigneeType(t.getAssigneeType())
                    .assigneeId(t.getAssigneeId())
                    .assigneeName(t.getAssigneeName())
                    .performType(t.getPerformType())
                    .taskStatus(t.getTaskStatus())
                    .createAt(t.getCreatedAt())
                    .dueAt(t.getDueAt())
                    .mine(isMine(t, userId))
                    .build());
        }
        return out;
    }

    /**
     * 加载审批轨迹（历史任务 + 审计日志）
     */
    private List<Map<String, Object>> loadHistory(String instanceId) {
        try {
            List<FlowHisTaskDO> his = hisTaskMapper.selectByInstanceId(instanceId);
            if (his == null || his.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> out = new ArrayList<>(his.size());
            for (FlowHisTaskDO t : his) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", "TASK");
                m.put("taskId", t.getId());
                m.put("nodeCode", t.getNodeCode());
                m.put("nodeName", t.getNodeName());
                m.put("assigneeId", t.getAssigneeId());
                m.put("assigneeName", t.getAssigneeName());
                m.put("action", t.getPerformType());
                m.put("comment", t.getComment());
                m.put("timestamp", t.getFinishAt());
                m.put("taskStatus", t.getTaskStatus());
                out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.warn("[EmbeddedApproval] 加载历史轨迹失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 加载流程图（含高亮当前节点）
     *
     * <p>嵌入式场景下流程图较大（包含 definition/nodes/skips），由前端按需通过
     * GET /workflow/engine/instance/{id}/diagram 单独拉取，本接口不返回以保持轻量。
     * 仅返回最简的节点信息用于高亮当前节点。
     */
    private Map<String, Object> loadDiagram(FlowInstanceDO instance) {
        Map<String, Object> light = new LinkedHashMap<>();
        light.put("currentNodeCode", instance.getCurrentNodeCode());
        light.put("currentNodeName", instance.getCurrentNodeName());
        light.put("flowCode", instance.getFlowCode());
        light.put("flowStatus", instance.getFlowStatus());
        return light;
    }

    /**
     * 安全检测 AI 服务可用性（不抛异常）
     */
    private boolean safeCheckAi() {
        try {
            return aiAssistService.isAiAvailable();
        } catch (Exception e) {
            log.warn("[FlowEmbeddedApprovalServiceImpl] AI 服务可用性检测异常，按不可用处理: {}", e.getMessage(), e);
            return false;
        }
    }
}
