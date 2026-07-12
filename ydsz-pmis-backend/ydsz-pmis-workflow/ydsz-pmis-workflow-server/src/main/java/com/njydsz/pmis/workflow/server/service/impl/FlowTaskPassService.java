package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.workflow.domain.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.server.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.definition.FlowNodeType;
import com.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import com.njydsz.pmis.workflow.domain.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.integration.FlowUserMapper;
import com.njydsz.pmis.workflow.server.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.server.service.integration.FlowAttachmentService;
import com.njydsz.pmis.workflow.server.form.FlowFormEngineService;
import com.njydsz.pmis.workflow.server.form.FlowFormSchema;
import com.njydsz.pmis.workflow.server.service.integration.FlowFormFieldPermService;
import com.njydsz.pmis.workflow.server.service.instance.FlowInstanceService;
import com.njydsz.pmis.workflow.server.service.instance.FlowTodoCountPushService;
import com.njydsz.pmis.workflow.server.service.impl.strategy.CountersignStrategy;
import com.njydsz.pmis.workflow.server.service.impl.strategy.CountersignStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 任务通过服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"任务通过"职责。
 * 核心流程：
 * <ol>
 *   <li>校验任务状态（未结束）</li>
 *   <li>合并流程变量 + 表单字段权限校验</li>
 *   <li>处理委派回归（DELEGATED 状态）</li>
 *   <li>标记当前用户已处理</li>
 *   <li>保存审批附件</li>
 *   <li>按 {@link FlowPerformType} 选择 {@link CountersignStrategy} 执行会签</li>
 *   <li>策略返回 true 时推进到下一节点</li>
 *   <li>推送 WebSocket 待办数 + 累计 Prometheus 指标</li>
 * </ol>
 *
 * <p>新增会签类型时：实现 {@link CountersignStrategy} + 在 {@code FlowPerformType} 枚举中加值，无需修改本类。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskPassService {

    /** 运行时任务 Mapper，查询/更新任务状态 */
    private final FlowRunTaskMapper taskMapper;
    /** 用户 Mapper，查询审批人用户信息 */
    private final FlowUserMapper userMapper;
    /** 流程实例 Mapper，查询实例状态和流程变量 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程节点 Mapper，查询节点配置 */
    private final FlowNodeMapper nodeMapper;
    /** 流程推进引擎，会签完成后推进到下一节点 */
    private final FlowAdvancer advancer;
    /** 流程实例服务，更新实例状态和变量 */
    private final FlowInstanceService instanceService;
    /** 跨子 Service 共享的任务校验/审计/事件辅助 */
    private final FlowTaskSupport support;
    /** 任务事件通知服务，推送任务通过通知 */
    private final FlowTaskNotificationService notificationService;
    /** 委派代理审计服务，记录代理人审批操作 */
    private final FlowTaskAuditService auditService;
    /** 会签策略工厂，根据 performType 选择会签策略 */
    private final CountersignStrategyFactory strategyFactory;
    /** 表单字段权限服务，校验表单字段读写权限 */
    private final FlowFormFieldPermService formFieldPermService;
    /** P0-3: 表单引擎服务 */
    private final FlowFormEngineService formEngineService;
    /** P1-6: 审批附件服务 */
    private final FlowAttachmentService attachmentService;
    /** P1-7: 待办数 WebSocket 推送服务 */
    @Lazy
    private final FlowTodoCountPushService todoCountPushService;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;

    /**
     * 通过任务。
     *
     * @param dto 操作参数（taskId/userId/comment/variables/attachments）
     */
    @Transactional(rollbackFor = Exception.class)
    public void pass(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_7f4098fb", task.getTaskStatus());
        }
        Map<String, Object> variables = dto.getVariables() == null
                ? Collections.emptyMap() : dto.getVariables();
        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        Map<String, Object> mergedVars = mergeVariables(instance, variables);

        // P0-2: 表单字段权限校验
        validateFormFieldPerms(task, dto.getVariables(), instance);

        // P1-10: 委派回归 — 被委派人通过后任务回到原办理人
        if (FlowTaskStatus.DELEGATED.name().equals(task.getTaskStatus())
                && task.getAssignorId() != null) {
            handleDelegateReturn(task, dto);
            return;
        }

        FlowPerformType performType = FlowPerformType.valueOf(
                task.getPerformType() == null ? FlowPerformType.OR.name() : task.getPerformType());

        // 标记当前用户已处理（pmis_flow_user）
        if (dto.getUserId() != null) {
            userMapper.markProcessed(task.getId(), String.valueOf(dto.getUserId()),
                    dto.getComment(), java.time.LocalDateTime.now());
        }

        // P1-6: 保存审批附件
        attachmentService.saveBatch(task.getInstanceId(), task.getId(), task.getNodeCode(),
                "TASK", dto.getUserId(), dto.getUserName(), dto.getAttachments(),
                task.getTenantId(), task.getProviderTraceId());

        // 策略模式处理会签
        CountersignStrategy strategy = strategyFactory.getStrategy(performType);
        strategy.preCheck(task, dto);
        strategy.onUserPassed(task, dto);

        boolean shouldAdvance = strategy.shouldAdvance(task);
        if (shouldAdvance) {
            strategy.onAdvance(task, dto);
            advanceProcess(instance, task, mergedVars, performType, dto);
        } else {
            support.audit(task, performType.name() + "_PASS", dto.getUserId(), null,
                    dto.getComment(), dto.getCommentType());
            log.info("[Flow] {} 部分通过: taskId={} finished={}/{}",
                    performType, task.getId(),
                    task.getApproveFinished(), task.getApproveCount());
        }

        // P1-7: WebSocket 推送任务完成
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskCompleted(task, dto.getUserId());
        }
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskPassed(task.getFlowCode(), task.getNodeCode());
            flowMetrics.recordTaskDuration(task, "PASSED");
        }
    }

    /**
     * 委派回归处理：被委派人通过后任务回到原办理人
     */
    private void handleDelegateReturn(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        auditService.logDelegateOperation(task, "DELEGATE_RETURN", "ACT");
        task.setAssigneeId(String.valueOf(task.getAssignorId()));
        task.setAssigneeName(task.getAssignorName());
        task.setAssignorId(null);
        task.setAssignorName(null);
        task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        task.setUpdatedAt(java.time.LocalDateTime.now());
        taskMapper.updateById(task);
        support.audit(task, "DELEGATE_RETURN", dto.getUserId(), null,
                dto.getComment(), dto.getCommentType());
        log.info("[Flow] 委派回归: taskId={} → 原办理人={}", task.getId(), task.getAssigneeId());
    }

    /**
     * 表单字段权限校验 + P0-3 表单 Schema 校验
     */
    private void validateFormFieldPerms(FlowRunTaskDO task, Map<String, Object> variables,
                                       FlowInstanceDO instance) {
        FlowNodeDO formNode = nodeMapper.selectByCode(task.getDefinitionId(), task.getNodeCode());
        if (formNode == null) {
            return;
        }
        // 字段权限校验
        Map<String, String> fieldPerms = null;
        if (StringUtils.hasText(formNode.getFormFieldsConfig())) {
            fieldPerms = formFieldPermService.parseFieldPerms(formNode.getFormFieldsConfig());
            if (!fieldPerms.isEmpty()) {
                Map<String, Object> existingVars = mergeVariables(instance, Collections.emptyMap());
                formFieldPermService.validateFieldPerms(fieldPerms, variables, existingVars);
            }
        }
        // P0-3: 表单 Schema 校验
        FlowFormSchema schema = formEngineService.getFormSchema(formNode.getExt());
        if (schema != null) {
            formEngineService.validateAndThrow(schema, variables, fieldPerms);
        }
    }

    /**
     * 流程推进
     */
    private void advanceProcess(FlowInstanceDO instance, FlowRunTaskDO task,
                                Map<String, Object> vars, FlowPerformType performType,
                                FlowTaskOperateDTO dto) {
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        notificationService.fireTaskCompleted(task.getId(), "PASS", vars);
        support.audit(task, performType.name() + "_PASS_ALL", dto.getUserId(), null,
                dto.getComment(), dto.getCommentType());
        log.info("[Flow] {} 全部通过: taskId={} next={}", performType, task.getId(), nextNodes.size());
    }

    /**
     * 更新实例当前节点
     */
    private void updateInstanceNode(FlowInstanceDO instance, List<FlowNodeDO> nextNodes) {
        if (!nextNodes.isEmpty() && nextNodes.get(0).getNodeType()
                != FlowNodeType.END.getCode()) {
            instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                    nextNodes.get(0).getNodeCode(), nextNodes.get(0).getNodeName(),
                    null, null);
        }
    }

    /**
     * 合并流程变量：实例已有变量 + dto 增量
     */
    private Map<String, Object> mergeVariables(FlowInstanceDO instance, Map<String, Object> extra) {
        if (instance == null || !StringUtils.hasText(instance.getVariable())) {
            return extra == null ? Collections.emptyMap() : extra;
        }
        try {
            Map<String, Object> base = JsonUtils.parseMap(instance.getVariable());
            if (extra != null && !extra.isEmpty()) {
                base.putAll(extra);
            }
            return base;
        } catch (Exception e) {
            return extra == null ? Collections.emptyMap() : extra;
        }
    }
}
