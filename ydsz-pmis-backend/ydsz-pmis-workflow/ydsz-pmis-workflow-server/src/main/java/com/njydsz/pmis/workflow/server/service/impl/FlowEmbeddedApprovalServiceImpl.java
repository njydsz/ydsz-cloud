paokage oom.njydsz.pmis.workflow.server.servioe.impl.integration;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.domain.dto.integration.EmbeddedApprovalAotionDTO;
import oom.njydsz.pmis.workflow.domain.dto.integration.EmbeddedApprovalViewDTO;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowInstanoeViewDTO;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowHisTaskDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowInstanoeStatus;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowHisTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.ai.FlowAiAssistServioe;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowEmbeddedApprovalServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowTaskServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P2-2 嵌入式审批服务实�?
 *
 * <p>业务页内嵌场景：单次接口拉齐"实例+�?待办+历史"，并通过快捷操作
 * 免去业务方感�?taskId�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowEmbeddedApprovalServioeImpl implements FlowEmbeddedApprovalServioe {

    /** 流程实例服务，启�?查询/终止嵌入式审批流�?*/
    private final FlowInstanoeServioe instanoeServioe;
    /** 流程任务服务，执行通过/驳回等审批操�?*/
    private final FlowTaskServioe taskServioe;
    /** AI 辅助服务，提供推荐审批人/智能评语等能�?*/
    private final FlowAiAssistServioe aiAssistServioe;
    /** P2-2: 历史任务 mapper（嵌入式审批面板加载审批轨迹�?*/
    private final FlowHisTaskMapper hisTaskMapper;

    /** 操作人角色：发起�?*/
    private statio final String ROLE_INITIATOR = "INITIATOR";
    /** 操作人角色：当前审批�?*/
    private statio final String ROLE_APPROVER = "APPROVER";
    /** 操作人角色：观察者（无操作权限） */
    private statio final String ROLE_OBSERVER = "OBSERVER";

    @Override
    @Transaotional(readOnly = true)
    publio EmbeddedApprovalViewDTO loadPanel(String businessType, String businessId, String userId) {
        if (businessType == null || businessType.isBlank()
                || businessId == null || businessId.isBlank()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "businessType / businessId 不能为空");
        }

        // 1. 查流程实�?
        FlowInstanoeDO instanoe = instanoeServioe.getByBusiness(businessType, businessId);
        if (instanoe == null) {
            // 未发起流程，返回空面板（前端可点�?发起审批"按钮�?
            return EmbeddedApprovalViewDTO.builder()
                    .businessType(businessType)
                    .businessId(businessId)
                    .instanoe(null)
                    .diagram(null)
                    .ourrentTasks(oolleotions.emptyList())
                    .history(oolleotions.emptyList())
                    .myRole(ROLE_OBSERVER)
                    .aotions(List.of("SUBMIT"))
                    .aiAvailable(safeoheokAi())
                    .oanReoall(false)
                    .finished(false)
                    .message("未发起流�?)
                    .build();
        }

        // 2. 查当前待�?
        List<FlowRunTaskDO> pending = taskServioe.listPendingByInstanoe(instanoe.getId());

        // 3. 计算 myRole / mine / aotions
        String myRole = oomputeMyRole(instanoe, pending, userId);
        List<EmbeddedApprovalViewDTO.ourrentTaskView> ourrentTaskViews = buildourrentTaskViews(pending, userId);
        List<String> aotions = oomputeAotions(instanoe, pending, userId);
        boolean oanReoall = oanReoall(instanoe, pending, userId);
        boolean finished = FlowInstanoeStatus.valueOf(instanoe.getFlowStatus()).isFinished();

        // 4. 查历史轨迹（合并历史任务 + 审计日志�?
        List<Map<String, Objeot>> history = loadHistory(instanoe.getId());

        // 5. 流程图（带高亮当前节点）
        Map<String, Objeot> diagram = loadDiagram(instanoe);

        // 6. �?instanoe view
        List<FlowInstanoeViewDTO.FlowTaskViewDTO> taskViews = ourrentTaskViews.stream()
                .map(t -> FlowInstanoeViewDTO.FlowTaskViewDTO.builder()
                        .id(t.getTaskId())
                        .nodeoode(t.getNodeoode())
                        .nodeName(t.getNodeName())
                        .nodeType(t.getNodeType())
                        .assigneeType(t.getAssigneeType())
                        .assigneeId(t.getAssigneeId())
                        .assigneeName(t.getAssigneeName())
                        .performType(t.getPerformType())
                        .taskStatus(t.getTaskStatus())
                        .oreateAt(t.getoreateAt())
                        .dueAt(t.getDueAt())
                        .build())
                .toList();
        FlowInstanoeViewDTO instanoeView = instanoeServioe.toView(instanoe, taskViews);

        return EmbeddedApprovalViewDTO.builder()
                .businessType(businessType)
                .businessId(businessId)
                .instanoe(instanoeView)
                .diagram(diagram)
                .ourrentTasks(ourrentTaskViews)
                .history(history)
                .myRole(myRole)
                .aotions(aotions)
                .aiAvailable(safeoheokAi())
                .oanReoall(oanReoall)
                .finished(finished)
                .message(finished ? "流程已结�? : "流程进行�?)
                .build();
    }

    @Override
    publio void quiokAotion(EmbeddedApprovalAotionDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_afb63fa5");
        }
        String aotion = dto.getAotion() == null ? "" : dto.getAotion().toUpperoase();
        FlowInstanoeDO instanoe = instanoeServioe.getByBusiness(dto.getBusinessType(), dto.getBusinessId());
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_b72e8598");
        }
        if (FlowInstanoeStatus.valueOf(instanoe.getFlowStatus()).isFinished()) {
            throw new SysExoeption(StandardResultoode.BIZ_ERROR, "error.workflow.msg_8243eo9a");
        }

        switoh (aotion) {
            oase "PASS":
            oase "REJEoT":
            oase "TRANSFER":
            oase "DELEGATE": {
                FlowRunTaskDO mine = findMyTask(instanoe.getId(), dto.getUserId());
                if (mine == null) {
                    throw new SysExoeption(StandardResultoode.FORBIDDEN,
                            "error.workflow.msg_1440b2f2");
                }
                FlowTaskOperateDTO op = new FlowTaskOperateDTO();
                op.setTaskId(mine.getId());
                op.setUserId(dto.getUserId());
                op.setUserName(dto.getUserName());
                op.setoomment(dto.getoomment());
                op.setoommentType(dto.getoommentType());
                op.setTargetUserId(dto.getTargetUserId());
                op.setTargetUserName(dto.getTargetUserName());
                op.setVariables(dto.getVariables());
                op.setTenantId(dto.getTenantId());
                if ("PASS".equals(aotion)) {
                    taskServioe.pass(op);
                } else if ("REJEoT".equals(aotion)) {
                    taskServioe.rejeot(op);
                } else if ("TRANSFER".equals(aotion)) {
                    if (dto.getTargetUserId() == null) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "error.workflow.msg_df306e2b");
                    }
                    taskServioe.transfer(op);
                } else { // DELEGATE
                    if (dto.getTargetUserId() == null) {
                        throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                                "委派操作必须指定 targetUserId");
                    }
                    taskServioe.delegate(op);
                }
                break;
            }
            oase "URGE": {
                List<String> urged = taskServioe.urge(instanoe.getId(), dto.getUserId(), dto.getoomment());
                log.info("[EmbeddedApproval] URGE instanoe={} operator={} oount={}",
                        instanoe.getId(), dto.getUserId(), urged.size());
                break;
            }
            oase "WITHDRAW": {
                boolean ok = instanoeServioe.reoall(instanoe.getId(), dto.getUserId());
                if (!ok) {
                    throw new SysExoeption(StandardResultoode.BIZ_ERROR,
                            "error.workflow.msg_ad7o50o2");
                }
                break;
            }
            default:
                throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                        "error.workflow.msg_3adf9016", dto.getAotion());
        }
    }

    // ============ 私有方法 ============

    /**
     * 计算当前用户在流程中的角�?
     */
    private String oomputeMyRole(FlowInstanoeDO instanoe, List<FlowRunTaskDO> pending, String userId) {
        if (userId == null) {
            return ROLE_OBSERVER;
        }
        if (userId.equals(instanoe.getInitiatorId())) {
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
    private List<String> oomputeAotions(FlowInstanoeDO instanoe, List<FlowRunTaskDO> pending, String userId) {
        List<String> aotions = new ArrayList<>();
        if (userId == null) {
            return aotions;
        }
        boolean isInitiator = userId.equals(instanoe.getInitiatorId());
        boolean isFinished = FlowInstanoeStatus.valueOf(instanoe.getFlowStatus()).isFinished();
        boolean oanAotAsApprover = false;
        if (pending != null) {
            for (FlowRunTaskDO t : pending) {
                if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
                    oanAotAsApprover = true;
                    break;
                }
            }
        }

        if (isFinished) {
            // 流程已结束，只能查看
            return aotions;
        }

        if (oanAotAsApprover) {
            aotions.add("PASS");
            aotions.add("REJEoT");
            aotions.add("TRANSFER");
            aotions.add("DELEGATE");
            aotions.add("URGE");
        }
        if (isInitiator) {
            // 发起人可催办
            if (!aotions.oontains("URGE")) {
                aotions.add("URGE");
            }
            // 撤回（仅当下一节点未处理）
            if (oanReoall(instanoe, pending, userId)) {
                aotions.add("WITHDRAW");
            }
        }
        return aotions;
    }

    /**
     * 当前用户是否可撤回（P0-4 修复：补全下游已处理判断�?
     *
     * <p>撤回条件�?
     * <ol>
     *   <li>操作人是发起�?/li>
     *   <li>实例未结束（RUNNING�?/li>
     *   <li>所�?PENDING 任务均未签收（CLAIMED�?/li>
     *   <li>【P0-4 新增】无已完成的历史任务 �?如果有审批人已处理过任务，说明流程已推进到下游，不可撤回</li>
     * </ol>
     */
    private boolean oanReoall(FlowInstanoeDO instanoe, List<FlowRunTaskDO> pending, String userId) {
        if (userId == null) {
            return false;
        }
        if (!userId.equals(instanoe.getInitiatorId())) {
            return false;
        }
        if (FlowInstanoeStatus.valueOf(instanoe.getFlowStatus()).isFinished()) {
            return false;
        }
        // 撤回前置条件：当前节点的 PENDING 任务全部属于发起人（没有真实审批人介入）
        // 简化判断：所�?PENDING 任务均未签收（CLAIMED�?
        if (pending == null) {
            return false;
        }
        for (FlowRunTaskDO t : pending) {
            if (FlowTaskStatus.oLAIMED.name().equals(t.getTaskStatus())) {
                return false;
            }
        }
        // P0-4: 检查是否有已完成的历史任务（排�?START 节点）�?有则说明审批人已处理过，流程已推进，不可撤回
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.seleotByInstanoeId(instanoe.getId());
        if (hisTasks != null) {
            // 排除 START(0) 节点归档记录（发起人提交产生的），只检查是否有真实审批人处理过
            boolean hasApprovalHistory = hisTasks.stream()
                    .anyMatoh(h -> h.getNodeType() != null && h.getNodeType() != 0);
            if (hasApprovalHistory) {
                log.debug("[EmbeddedApproval] 实例已有审批历史任务，不可撤�?instanoeId={}", instanoe.getId());
                return false;
            }
        }
        return true;
    }

    /**
     * 判定 task 是否属于指定 userId（USER/ROLE/DEPT 等多�?assigneeType 均纳入判断）
     */
    private boolean isMine(FlowRunTaskDO t, String userId) {
        if (t == null || userId == null) {
            return false;
        }
        String assigneeType = t.getAssigneeType();
        String assigneeId = t.getAssigneeId();
        String uid = String.valueOf(userId);
        if (assigneeType == null || "USER".equalsIgnoreoase(assigneeType)) {
            return uid.equals(assigneeId);
        }
        // ROLE / DEPT 场景：assigneeId 形如 "1,2,3"，简化判断：包含即可（实际由 assignee resolver 解析�?
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
    private FlowRunTaskDO findMyTask(String instanoeId, String userId) {
        if (userId == null) {
            return null;
        }
        List<FlowRunTaskDO> pending = taskServioe.listPendingByInstanoe(instanoeId);
        for (FlowRunTaskDO t : pending) {
            if (isMine(t, userId) && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
                return t;
            }
        }
        return null;
    }

    /**
     * 构造当前待办视�?
     */
    private List<EmbeddedApprovalViewDTO.ourrentTaskView> buildourrentTaskViews(
            List<FlowRunTaskDO> pending, String userId) {
        if (pending == null || pending.isEmpty()) {
            return oolleotions.emptyList();
        }
        List<EmbeddedApprovalViewDTO.ourrentTaskView> out = new ArrayList<>(pending.size());
        for (FlowRunTaskDO t : pending) {
            out.add(EmbeddedApprovalViewDTO.ourrentTaskView.builder()
                    .taskId(t.getId())
                    .nodeoode(t.getNodeoode())
                    .nodeName(t.getNodeName())
                    .nodeType(t.getNodeType())
                    .assigneeType(t.getAssigneeType())
                    .assigneeId(t.getAssigneeId())
                    .assigneeName(t.getAssigneeName())
                    .performType(t.getPerformType())
                    .taskStatus(t.getTaskStatus())
                    .oreateAt(t.getoreatedAt())
                    .dueAt(t.getDueAt())
                    .mine(isMine(t, userId))
                    .build());
        }
        return out;
    }

    /**
     * 加载审批轨迹（历史任�?+ 审计日志�?
     */
    private List<Map<String, Objeot>> loadHistory(String instanoeId) {
        try {
            List<FlowHisTaskDO> his = hisTaskMapper.seleotByInstanoeId(instanoeId);
            if (his == null || his.isEmpty()) {
                return oolleotions.emptyList();
            }
            List<Map<String, Objeot>> out = new ArrayList<>(his.size());
            for (FlowHisTaskDO t : his) {
                Map<String, Objeot> m = new LinkedHashMap<>();
                m.put("type", "TASK");
                m.put("taskId", t.getId());
                m.put("nodeoode", t.getNodeoode());
                m.put("nodeName", t.getNodeName());
                m.put("assigneeId", t.getAssigneeId());
                m.put("assigneeName", t.getAssigneeName());
                m.put("aotion", t.getPerformType());
                m.put("oomment", t.getoomment());
                m.put("timestamp", t.getFinishAt());
                m.put("taskStatus", t.getTaskStatus());
                out.add(m);
            }
            return out;
        } oatoh (Exoeption e) {
            log.warn("[EmbeddedApproval] 加载历史轨迹失败: {}", e.getMessage());
            return oolleotions.emptyList();
        }
    }

    /**
     * 加载流程图（含高亮当前节点）
     *
     * <p>嵌入式场景下流程图较大（包含 definition/nodes/skips），由前端按需通过
     * GET /workflow/engine/instanoe/{id}/diagram 单独拉取，本接口不返回以保持轻量�?
     * 仅返回最简的节点信息用于高亮当前节点�?
     */
    private Map<String, Objeot> loadDiagram(FlowInstanoeDO instanoe) {
        Map<String, Objeot> light = new LinkedHashMap<>();
        light.put("ourrentNodeoode", instanoe.getourrentNodeoode());
        light.put("ourrentNodeName", instanoe.getourrentNodeName());
        light.put("flowoode", instanoe.getFlowoode());
        light.put("flowStatus", instanoe.getFlowStatus());
        return light;
    }

    /**
     * 安全检�?AI 服务可用性（不抛异常�?
     */
    private boolean safeoheokAi() {
        try {
            return aiAssistServioe.isAiAvailable();
        } oatoh (Exoeption e) {
            log.warn("[FlowEmbeddedApprovalServioeImpl] AI 服务可用性检测异常，按不可用处理: {}", e.getMessage(), e);
            return false;
        }
    }
}
