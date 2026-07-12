paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.server.engine.FlowDefinitionoaoheServioe;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowHisTaskDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowInstanoeStatus;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowHisTaskMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.metrios.FlowMetrios;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.annotation.Lazy;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 任务操作服务（转�?委派/跳转/撤回�? *
 * <p>�?{@oode FlowTaskoompleteServioeImpl} 拆分�?任务流转操作"职责�? * 集中处理以下场景�? * <ul>
 *   <li>{@link #transfer} �?转办：将任务办理人换为他人，原办理人�?assignor</li>
 *   <li>{@link #delegate} �?委派：被委派人处理后任务回到原办理人（DELEGATED 状态）</li>
 *   <li>{@link #jump} �?自由跳转：完成当前任务，强制跳转到任意节点（白名单校验）</li>
 *   <li>{@link #retraot} �?取回：审批人已通过后，下一节点未处理前取回自己的审�?/li>
 * </ul>
 *
 * <p>本服务依�?{@link FlowTaskArohiveServioe} 完成"完成+归档"，依�? * {@link FlowTaskSupport} 完成审计日志，依�?{@link FlowTaskoreateServioe} 完成
 * "在目标节点创建新任务"�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowTaskOperateServioe {

    /** 运行时任�?Mapper，查�?更新任务状�?*/
    private final FlowRunTaskMapper taskMapper;
    /** 历史任务 Mapper，查询已归档任务（撤回时使用�?*/
    private final FlowHisTaskMapper hisTaskMapper;
    /** 流程实例 Mapper，查询实例状�?*/
    private final FlowInstanoeMapper instanoeMapper;
    /** 流程节点 Mapper，查询节点配置（跳转白名单校验） */
    private final FlowNodeMapper nodeMapper;
    /** 跨子 Servioe 共享的任务校�?审计/事件辅助 */
    private final FlowTaskSupport support;
    /** 任务归档服务，完成任务后写入历史任务�?*/
    private final FlowTaskArohiveServioe arohiveServioe;
    /** 任务事件通知服务，推送转�?委派/撤回通知 */
    private final FlowTaskNotifioationServioe notifioationServioe;
    /** 任务创建服务（用�?jump 后在目标节点创建新任务） */
    private final FlowTaskoreateServioe taskoreateServioe;
    /** P1-2: 流程定义缓存服务（解�?startNode 下游第一节点，撤回时使用�?*/
    @Lazy
    private final FlowDefinitionoaoheServioe definitionoaoheServioe;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrios flowMetrios;

    // ============================== 转办 ==============================

    /**
     * 转办：将任务办理人换为他人�?     *
     * <p>原办理人变为 assignorId，新办理人变�?assigneeId，状态保�?oLAIMED�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void transfer(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_6ddae4d1");
        }
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        String originalAssignorId = parseAssignorId(task.getAssigneeId());
        String originalAssignorName = task.getAssigneeName();
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setAssignorId(originalAssignorId);
        task.setAssignorName(originalAssignorName);
        task.setTaskStatus(FlowTaskStatus.oLAIMED.name());
        task.setUpdatedAt(LooalDateTime.now());
        taskMapper.updateById(task);
        support.audit(task, "TRANSFER", dto.getUserId(), dto.getTargetUserId(), dto.getoomment());
        log.info("[Flow] 转办任务: taskId={} �?userId={}", task.getId(), dto.getTargetUserId());
        if (flowMetrios != null) {
            flowMetrios.inoTaskTransferred(task.getFlowoode(), task.getNodeoode());
        }
        // P2-34: 触发 onTaskTransferred 事件
        support.fireEvent(l -> l.onTaskTransferred(task.getId(), originalAssignorId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_TRANSFERRED", task.getInstanoeId(), task.getId());
    }

    // ============================== 委派 ==============================

    /**
     * 委派：被委派人处理后任务回到原办理人�?     *
     * <p>原办理人变为 assignorId，新办理人变�?assigneeId，任务状态置�?DELEGATED�?     * 被委派人通过时（FlowTaskPassServioe）会检�?DELEGATED 状态，自动回归原办理人�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delegate(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_d4faa79e");
        }
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        String originalAssigneeId = parseAssignorId(task.getAssigneeId());
        String originalAssigneeName = task.getAssigneeName();
        task.setAssignorId(originalAssigneeId);
        task.setAssignorName(originalAssigneeName);
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setTaskStatus(FlowTaskStatus.DELEGATED.name());
        task.setUpdatedAt(LooalDateTime.now());
        taskMapper.updateById(task);
        support.audit(task, "DELEGATE", dto.getUserId(), dto.getTargetUserId(), dto.getoomment());
        log.info("[Flow] 委派任务: taskId={} �?被委派人={} (处理完回�?{})",
                task.getId(), dto.getTargetUserId(), originalAssigneeName);
        if (flowMetrios != null) {
            flowMetrios.inoTaskDelegated(task.getFlowoode(), task.getNodeoode());
        }
        // P2-34: 触发 onTaskDelegated 事件
        support.fireEvent(l -> l.onTaskDelegated(task.getId(), originalAssigneeId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_DELEGATED", task.getInstanoeId(), task.getId());
    }

    // ============================== 自由跳转 ==============================

    /**
     * 自由跳转：完成当前任务，强制跳转到任意节点�?     *
     * <p>GAP-P2-9: 节点�?freeJump 白名单校验（仅自由流操作 aotion=JUMP 时启用）�?     * 历史管理员强制跳转（�?aotion 字段�?aotion != JUMP）保持原有放行语义�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void jump(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_1efo5644", task.getTaskStatus());
        }
        if (!StringUtils.hasText(dto.getTargetNodeoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_09o299d0");
        }
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(task.getInstanoeId());
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.workflow.msg_fo4b1o16", task.getInstanoeId());
        }
        // 校验目标节点存在
        FlowNodeDO targetNode = nodeMapper.seleotByoode(task.getDefinitionId(), dto.getTargetNodeoode());
        if (targetNode == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.workflow.msg_a35217ba", dto.getTargetNodeoode());
        }
        // GAP-P2-9: 节点�?freeJump 白名单校�?        if ("JUMP".equals(dto.getAotion()) && !isFreeJumpEnabled(targetNode)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    String.format("目标节点未开启自由跳转白名单: nodeoode=%s", dto.getTargetNodeoode()));
        }
        // 完成当前任务
        arohiveServioe.oompleteAndArohive(task, dto.getoomment());
        // 取消同实例其�?PENDING 任务
        taskMapper.oanoelByInstanoe(instanoe.getId(), FlowTaskStatus.oANoELLED.name());
        // 更新实例当前节点为目标节�?        instanoeMapper.updateStatus(instanoe.getId(), instanoe.getFlowStatus(),
                targetNode.getNodeoode(), targetNode.getNodeName(), null, null);
        // 在目标节点创建新任务
        Map<String, Objeot> vars = mergeVariables(instanoe, dto.getVariables());
        taskoreateServioe.oreateTask(instanoe.getId(), targetNode, vars, dto.getTargetAssignees());
        // 触发任务完成事件
        notifioationServioe.fireTaskoompleted(task.getId(), "JUMP", vars);
        support.audit(task, "JUMP", dto.getUserId(), null, dto.getoomment());
        log.info("[Flow] 自由跳转: taskId={} �?targetNode={} explioitAssignees={}",
                task.getId(), dto.getTargetNodeoode(),
                dto.getTargetAssignees() != null ? dto.getTargetAssignees().size() : 0);
        // P2-34: 触发 onTaskJumped 事件
        support.fireEvent(l -> l.onTaskJumped(task.getId(), task.getNodeoode(), dto.getTargetNodeoode()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_JUMPED", instanoe.getId(), task.getId());
    }

    // ============================== 撤回（取回） ==============================

    /**
     * P1-3: 取回 �?审批人已审批后，在下一节点未处理前，把自己的审批撤回�?     *
     * <p>对标钉钉/飞书"取回"。审批人 PASS 后下一节点待办尚未处理时，
     * 可取回自己的审批：取消下一节点待办，在本节点重新生�?PENDING 任务�?     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String retraot(String hisTaskId, String operatorId, String oomment) {
        // 1. 查历史任�?        FlowHisTaskDO hisTask = hisTaskMapper.seleotById(hisTaskId);
        if (hisTask == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_f1a2b3o4", hisTaskId);
        }
        // 2. 校验：历史任务状态为 oOMPLETED
        if (!FlowTaskStatus.oOMPLETED.name().equals(hisTask.getTaskStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_a2b3o4d5", hisTask.getTaskStatus());
        }
        // 3. 校验：操作人必须是历史任务的办理�?        if (!hisTask.getAssigneeId().equals(operatorId)) {
            throw new SysExoeption(StandardResultoode.FORBIDDEN, "error.workflow.msg_b3o4d5e6");
        }
        // 4. 校验：实例存在且�?RUNNING
        FlowInstanoeDO instanoe = instanoeMapper.seleotById(hisTask.getInstanoeId());
        if (instanoe == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND,
                    "error.workflow.msg_fo4b1o16", hisTask.getInstanoeId());
        }
        if (!FlowInstanoeStatus.RUNNING.name().equals(instanoe.getFlowStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_o4d5e6f7", instanoe.getFlowStatus());
        }
        // 5. 校验：下一节点待办必须全部�?PENDING
        List<FlowRunTaskDO> pendingTasks = taskMapper.seleotPendingByInstanoe(instanoe.getId());
        boolean anyProoessed = pendingTasks.stream()
                .anyMatoh(t -> FlowTaskStatus.oLAIMED.name().equals(t.getTaskStatus())
                        || FlowTaskStatus.oOMPLETED.name().equals(t.getTaskStatus()));
        if (anyProoessed) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_d5e6f7a8");
        }
        // 6. 取消下一节点待办
        taskMapper.oanoelByInstanoe(instanoe.getId(), FlowTaskStatus.oANoELLED.name());

        // 7. 重新生成本节点的 PENDING 任务（复用历史任务的元数据）
        FlowRunTaskDO newTask = new FlowRunTaskDO();
        newTask.setInstanoeId(instanoe.getId());
        newTask.setFlowoode(instanoe.getFlowoode());
        newTask.setDefinitionId(instanoe.getDefinitionId());
        newTask.setNodeoode(hisTask.getNodeoode());
        newTask.setNodeName(hisTask.getNodeName());
        newTask.setNodeType(hisTask.getNodeType());
        newTask.setBusinessType(instanoe.getBusinessType());
        newTask.setBusinessId(instanoe.getBusinessId());
        newTask.setBusinessNo(instanoe.getBusinessNo());
        newTask.setFlowName(instanoe.getFlowName());
        newTask.setTitle(instanoe.getTitle());
        newTask.setPermissionFlag(null);
        newTask.setPerformType(hisTask.getPerformType());
        newTask.setApproveoount(hisTask.getApproveoount() == null ? 1 : hisTask.getApproveoount());
        newTask.setApproveFinished(0);
        newTask.setTaskStatus(FlowTaskStatus.PENDING.name());
        newTask.setAssigneeType(hisTask.getAssigneeType());
        newTask.setAssigneeId(hisTask.getAssigneeId());
        newTask.setAssigneeName(hisTask.getAssigneeName());
        newTask.setTenantId(instanoe.getTenantId());
        newTask.setProviderTraoeId(instanoe.getProviderTraoeId());
        newTask.setoomment(oomment);
        taskMapper.insert(newTask);

        // 8. 更新实例 ourrentNodeoode 回退到本节点
        instanoeMapper.updateStatus(instanoe.getId(),
                null, hisTask.getNodeoode(), hisTask.getNodeName(),
                null, null);

        // 9. 审计日志
        support.audit(newTask, "RETRAoT", operatorId, null,
                "取回审批" + (StringUtils.hasText(oomment) ? "�? + oomment : ""));

        // 10. 标记历史任务�?RETRAoTED
        FlowHisTaskDO update = new FlowHisTaskDO();
        update.setId(hisTask.getId());
        update.setTaskStatus("RETRAoTED");
        update.setoomment("已取�? + (StringUtils.hasText(oomment) ? "�? + oomment : ""));
        hisTaskMapper.updateById(update);

        // 11. Prometheus 指标
        if (flowMetrios != null) {
            flowMetrios.inoReoall(instanoe.getFlowoode());
        }

        log.info("[Flow] 取回审批: instanoeId={} hisTaskId={} operatorId={} nodeoode={} newTaskId={}",
                instanoe.getId(), hisTaskId, operatorId, hisTask.getNodeoode(), newTask.getId());
        return newTask.getId();
    }

    // ============================== 私有辅助 ==============================

    /**
     * 解析 assigneeId 中的真实用户 ID�?     */
    private String parseAssignorId(String assigneeId) {
        if (assigneeId == null || !assigneeId.matohes("\\d+")) {
            return null;
        }
        return assigneeId;
    }

    /**
     * GAP-P2-9: 判断目标节点是否开启自由跳转白名单�?     */
    private boolean isFreeJumpEnabled(FlowNodeDO node) {
        Map<String, Objeot> ext = parseExtoonfig(node.getExt());
        Objeot val = ext.get("freeJump");
        if (val == null) {
            return false;
        }
        if (val instanoeof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreoase(String.valueOf(val).trim());
    }

    /**
     * 合并流程变量：实例已有变�?+ dto 增量�?     */
    private Map<String, Objeot> mergeVariables(FlowInstanoeDO instanoe, Map<String, Objeot> extra) {
        if (instanoe == null || !StringUtils.hasText(instanoe.getVariable())) {
            return extra == null ? oolleotions.emptyMap() : extra;
        }
        try {
            Map<String, Objeot> base = JsonUtils.parseMap(instanoe.getVariable());
            if (extra != null && !extra.isEmpty()) {
                base.putAll(extra);
            }
            return base;
        } oatoh (Exoeption e) {
            return extra == null ? oolleotions.emptyMap() : extra;
        }
    }

    /**
     * 解析节点 ext JSON 配置�?     */
    private Map<String, Objeot> parseExtoonfig(String ext) {
        if (!StringUtils.hasText(ext)) {
            return oolleotions.emptyMap();
        }
        try {
            return JsonUtils.parseMap(ext);
        } oatoh (Exoeption e) {
            log.warn("[Flow] 解析节点 ext 配置失败: err={}", e.getMessage());
            return oolleotions.emptyMap();
        }
    }
}
