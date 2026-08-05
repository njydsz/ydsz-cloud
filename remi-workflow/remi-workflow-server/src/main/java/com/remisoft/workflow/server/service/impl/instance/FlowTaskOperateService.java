package com.remisoft.workflow.server.service.impl.instance;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.remisoft.common.core.code.BaseResultCode;
import com.remisoft.common.exception.custom.SysException;
import com.remisoft.common.json.RemiJson;
import com.remisoft.workflow.domain.dto.FlowTaskOperateDTO;
import com.remisoft.workflow.domain.entity.FlowHisTask;
import com.remisoft.workflow.domain.entity.FlowInstance;
import com.remisoft.workflow.domain.entity.FlowNode;
import com.remisoft.workflow.domain.entity.FlowRunTask;
import com.remisoft.workflow.domain.enums.FlowInstanceStatus;
import com.remisoft.workflow.domain.enums.FlowTaskStatus;
import com.remisoft.workflow.infra.mapper.FlowHisTaskMapper;
import com.remisoft.workflow.infra.mapper.FlowInstanceMapper;
import com.remisoft.workflow.infra.mapper.FlowNodeMapper;
import com.remisoft.workflow.infra.mapper.FlowRunTaskMapper;
import com.remisoft.workflow.server.engine.FlowDefinitionCacheService;
import com.remisoft.workflow.server.metrics.FlowMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程任务操作服务实现。
 *
 * <p>提供任务级别的转办/委派/加签/减签/沟通/传阅等运营操作，
 *
 * <p>每种操作均产生审计记录与流程轨迹。
 *
 * @author remi-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskOperateService {

    /** 运行时任务 Mapper，查询/更新任务状态 */
    private final FlowRunTaskMapper taskMapper;
    /** 历史任务 Mapper，查询已归档任务（撤回时使用） */
    private final FlowHisTaskMapper hisTaskMapper;
    /** 流程实例 Mapper，查询实例状态 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程节点 Mapper，查询节点配置（跳转白名单校验） */
    private final FlowNodeMapper nodeMapper;
    /** 跨子 Service 共享的任务校验/审计/事件辅助 */
    private final FlowTaskSupport support;
    /** 任务归档服务，完成任务后写入历史任务表 */
    private final FlowTaskArchiveService archiveService;
    /** 任务事件通知服务，推送转办/委派/撤回通知 */
    private final FlowTaskNotificationService notificationService;
    /** 任务创建服务（用于 jump 后在目标节点创建新任务） */
    private final FlowTaskCreateService taskCreateService;
    /** P1-2: 流程定义缓存服务（解析 startNode 下游第一节点，撤回时使用） */
    @Lazy
    private final FlowDefinitionCacheService definitionCacheService;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;

    // ============================== 转办 ==============================

    /**
     * 转办：将任务办理人换为他人。
     *
     * <p>原办理人变为 assignorId，新办理人变为 assigneeId，状态保持 CLAIMED。
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_6ddae4d1");
        }
        FlowRunTask task = support.getTaskOrThrow(dto.getTaskId());
        String originalAssignorId = parseAssignorId(task.getAssigneeId());
        String originalAssignorName = task.getAssigneeName();
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setAssignorId(originalAssignorId);
        task.setAssignorName(originalAssignorName);
        task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        taskMapper.updateById(task);
        support.audit(task, "TRANSFER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 转办任务: taskId={} → userId={}", task.getId(), dto.getTargetUserId());
        if (flowMetrics != null) {
            flowMetrics.incTaskTransferred(task.getFlowCode(), task.getNodeCode());
        }
        // P2-34: 触发 onTaskTransferred 事件
        support.fireEvent(l -> l.onTaskTransferred(task.getId(), originalAssignorId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_TRANSFERRED", task.getInstanceId(), task.getId());
    }

    // ============================== 委派 ==============================

    /**
     * 委派：被委派人处理后任务回到原办理人。
     *
     * <p>原办理人变为 assignorId，新办理人变为 assigneeId，任务状态置为 DELEGATED。
     * 被委派人通过时（FlowTaskPassService）会检测 DELEGATED 状态，自动回归原办理人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delegate(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_d4faa79e");
        }
        FlowRunTask task = support.getTaskOrThrow(dto.getTaskId());
        String originalAssigneeId = parseAssignorId(task.getAssigneeId());
        String originalAssigneeName = task.getAssigneeName();
        task.setAssignorId(originalAssigneeId);
        task.setAssignorName(originalAssigneeName);
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setTaskStatus(FlowTaskStatus.DELEGATED.name());
        taskMapper.updateById(task);
        support.audit(task, "DELEGATE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 委派任务: taskId={} → 被委派人={} (处理完回到 {})",
                task.getId(), dto.getTargetUserId(), originalAssigneeName);
        if (flowMetrics != null) {
            flowMetrics.incTaskDelegated(task.getFlowCode(), task.getNodeCode());
        }
        // P2-34: 触发 onTaskDelegated 事件
        support.fireEvent(l -> l.onTaskDelegated(task.getId(), originalAssigneeId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_DELEGATED", task.getInstanceId(), task.getId());
    }

    // ============================== 自由跳转 ==============================

    /**
     * 自由跳转：完成当前任务，强制跳转到任意节点。
     *
     * <p>GAP-P2-9: 节点级 freeJump 白名单校验（仅自由流操作 action=JUMP 时启用）。
     * 历史管理员强制跳转（无 action 字段或 action != JUMP）保持原有放行语义。
     */
    @Transactional(rollbackFor = Exception.class)
    public void jump(FlowTaskOperateDTO dto) {
        FlowRunTask task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_1efc5644", task.getTaskStatus());
        }
        if (!StringUtils.hasText(dto.getTargetNodeCode())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_09c299d0");
        }
        FlowInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "error.workflow.msg_fc4b1c16", task.getInstanceId());
        }
        // 校验目标节点存在
        FlowNode targetNode = nodeMapper.selectByCode(task.getDefinitionId(), dto.getTargetNodeCode());
        if (targetNode == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "error.workflow.msg_a35217ba", dto.getTargetNodeCode());
        }
        // GAP-P2-9: 节点级 freeJump 白名单校验
        if ("JUMP".equals(dto.getAction()) && !isFreeJumpEnabled(targetNode)) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    String.format("目标节点未开启自由跳转白名单: nodeCode=%s", dto.getTargetNodeCode()));
        }
        // 完成当前任务
        archiveService.completeAndArchive(task, dto.getComment());
        // 取消同实例其他 PENDING 任务
        taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
        // 更新实例当前节点为目标节点
        instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                targetNode.getNodeCode(), targetNode.getNodeName(), null, null);
        // 在目标节点创建新任务
        Map<String, Object> vars = mergeVariables(instance, dto.getVariables());
        taskCreateService.createTask(instance.getId(), targetNode, vars, dto.getTargetAssignees());
        // 触发任务完成事件
        notificationService.fireTaskCompleted(task.getId(), "JUMP", vars);
        support.audit(task, "JUMP", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 自由跳转: taskId={} → targetNode={} explicitAssignees={}",
                task.getId(), dto.getTargetNodeCode(),
                dto.getTargetAssignees() != null ? dto.getTargetAssignees().size() : 0);
        // P2-34: 触发 onTaskJumped 事件
        support.fireEvent(l -> l.onTaskJumped(task.getId(), task.getNodeCode(), dto.getTargetNodeCode()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_JUMPED", instance.getId(), task.getId());
    }

    // ============================== 撤回（取回） ==============================

    /**
     * P1-3: 取回 — 审批人已审批后，在下一节点未处理前，把自己的审批撤回。
     *
     * <p>对标钉钉/飞书"取回"。审批人 PASS 后下一节点待办尚未处理时，
     * 可取回自己的审批：取消下一节点待办，在本节点重新生成 PENDING 任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public String retract(String hisTaskId, String operatorId, String comment) {
        // 1. 查历史任务
        FlowHisTask hisTask = hisTaskMapper.selectById(hisTaskId);
        if (hisTask == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_f1a2b3c4", hisTaskId);
        }
        // 2. 校验：历史任务状态为 COMPLETED
        if (!FlowTaskStatus.COMPLETED.name().equals(hisTask.getTaskStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_a2b3c4d5", hisTask.getTaskStatus());
        }
        // 3. 校验：操作人必须是历史任务的办理人
        if (!hisTask.getAssigneeId().equals(operatorId)) {
            throw new SysException(BaseResultCode.FORBIDDEN, "error.workflow.msg_b3c4d5e6");
        }
        // 4. 校验：实例存在且为 RUNNING
        FlowInstance instance = instanceMapper.selectById(hisTask.getInstanceId());
        if (instance == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "error.workflow.msg_fc4b1c16", hisTask.getInstanceId());
        }
        if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_c4d5e6f7", instance.getFlowStatus());
        }
        // 5. 校验：下一节点待办必须全部为 PENDING
        List<FlowRunTask> pendingTasks = taskMapper.selectPendingByInstance(instance.getId());
        boolean anyProcessed = pendingTasks.stream()
                .anyMatch(t -> FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())
                        || FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()));
        if (anyProcessed) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_d5e6f7a8");
        }
        // 6. 取消下一节点待办
        taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());

        // 7. 重新生成本节点的 PENDING 任务（复用历史任务的元数据）
        FlowRunTask newTask = new FlowRunTask();
        newTask.setInstanceId(instance.getId());
        newTask.setFlowCode(instance.getFlowCode());
        newTask.setDefinitionId(instance.getDefinitionId());
        newTask.setNodeCode(hisTask.getNodeCode());
        newTask.setNodeName(hisTask.getNodeName());
        newTask.setNodeType(hisTask.getNodeType());
        newTask.setBusinessType(instance.getBusinessType());
        newTask.setBusinessId(instance.getBusinessId());
        newTask.setBusinessNo(instance.getBusinessNo());
        newTask.setFlowName(instance.getFlowName());
        newTask.setTitle(instance.getTitle());
        newTask.setPermissionFlag(null);
        newTask.setPerformType(hisTask.getPerformType());
        newTask.setApproveCount(hisTask.getApproveCount() == null ? 1 : hisTask.getApproveCount());
        newTask.setApproveFinished(0);
        newTask.setTaskStatus(FlowTaskStatus.PENDING.name());
        newTask.setAssigneeType(hisTask.getAssigneeType());
        newTask.setAssigneeId(hisTask.getAssigneeId());
        newTask.setAssigneeName(hisTask.getAssigneeName());
        newTask.setTenantId(instance.getTenantId());
        newTask.setProviderTraceId(instance.getProviderTraceId());
        newTask.setComment(comment);
        taskMapper.insert(newTask);

        // 8. 更新实例 currentNodeCode 回退到本节点
        instanceMapper.updateStatus(instance.getId(),
                null, hisTask.getNodeCode(), hisTask.getNodeName(),
                null, null);

        // 9. 审计日志
        support.audit(newTask, "RETRACT", operatorId, null,
                "取回审批" + (StringUtils.hasText(comment) ? "：" + comment : ""));

        // 10. 标记历史任务为 RETRACTED
        FlowHisTask update = new FlowHisTask();
        update.setId(hisTask.getId());
        update.setTaskStatus("RETRACTED");
        update.setComment("已取回" + (StringUtils.hasText(comment) ? "：" + comment : ""));
        hisTaskMapper.updateById(update);

        // 11. Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incRecall(instance.getFlowCode());
        }

        log.info("[Flow] 取回审批: instanceId={} hisTaskId={} operatorId={} nodeCode={} newTaskId={}",
                instance.getId(), hisTaskId, operatorId, hisTask.getNodeCode(), newTask.getId());
        return newTask.getId();
    }

    // ============================== 私有辅助 ==============================

    /**
     * 解析 assigneeId 中的真实用户 ID。
     */
    private String parseAssignorId(String assigneeId) {
        if (assigneeId == null || !assigneeId.matches("\\d+")) {
            return null;
        }
        return assigneeId;
    }

    /**
     * GAP-P2-9: 判断目标节点是否开启自由跳转白名单。
     */
    private boolean isFreeJumpEnabled(FlowNode node) {
        Map<String, Object> ext = parseExtConfig(node.getExt());
        Object val = ext.get("freeJump");
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(val).trim());
    }

    /**
     * 合并流程变量：实例已有变量 + dto 增量。
     */
    private Map<String, Object> mergeVariables(FlowInstance instance, Map<String, Object> extra) {
        if (instance == null || !StringUtils.hasText(instance.getVariable())) {
            return extra == null ? Collections.emptyMap() : extra;
        }
        try {
            Map<String, Object> base = RemiJson.parseMap(instance.getVariable());
            if (extra != null && !extra.isEmpty()) {
                base.putAll(extra);
            }
            return base;
        } catch (Exception e) {
            return extra == null ? Collections.emptyMap() : extra;
        }
    }

    /**
     * 解析节点 ext JSON 配置。
     */
    private Map<String, Object> parseExtConfig(String ext) {
        if (!StringUtils.hasText(ext)) {
            return Collections.emptyMap();
        }
        try {
            return RemiJson.parseMap(ext);
        } catch (Exception e) {
            log.warn("[Flow] 解析节点 ext 配置失败: err={}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
