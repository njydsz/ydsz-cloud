package com.njydsz.workflow.server.service.impl.integration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.entity.FlowAuditLogDO;
import com.njydsz.workflow.domain.entity.FlowAutoTriggerDO;
import com.njydsz.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.workflow.infra.mapper.FlowAutoTriggerMapper;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowRoutingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程自动触发服务实现
 *
 * <p>当一个流程实例完成时，自动检查 sourceFlowCode 对应的所有 enabled 触发规则，
 * 使用 literule 的 ExpressionEvaluator 评估 conditionExpression，
 * 满足条件则自动启动 targetFlowCode 对应的目标流程。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAutoTriggerServiceImpl implements FlowAutoTriggerService {

    /** 自动触发 Mapper，管理 ydsz_flow_auto_trigger 表 */
    private final FlowAutoTriggerMapper autoTriggerMapper;
    /** 智能路由服务，解析触发条件表达式 */
    private final FlowRoutingService routingService;
    /** 工作流门面，自动发起后续流程实例 */
    private final WorkflowFacade workflowFacade;
    /** 流程实例服务，查询前置流程实例状态 */
    private final FlowInstanceService instanceService;
    /** 审计日志 Mapper，记录自动触发操作轨迹 */
    private final FlowAuditLogMapper auditLogMapper;

    // ============================== 核心：实例完成时触发 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onInstanceCompleted(String instanceId) {
        if (instanceId == null) {
            return;
        }

        // 1. 获取已完成的实例
        FlowInstanceDO instance = instanceService.getById(instanceId);
        if (instance == null) {
            log.warn("[FlowAutoTrigger] 实例不存在，跳过自动触发: instanceId={}", instanceId);
            return;
        }
        String sourceFlowCode = instance.getFlowCode();
        if (!StringUtils.hasText(sourceFlowCode)) {
            log.warn("[FlowAutoTrigger] 实例 flowCode 为空，跳过自动触发: instanceId={}", instanceId);
            return;
        }

        // 2. 查询 sourceFlowCode 对应的所有 enabled 触发规则
        List<FlowAutoTriggerDO> triggers = autoTriggerMapper.selectEnabledBySourceFlowCode(sourceFlowCode);
        if (triggers == null || triggers.isEmpty()) {
            log.debug("[FlowAutoTrigger] 无触发规则: sourceFlowCode={} instanceId={}",
                    sourceFlowCode, instanceId);
            return;
        }

        // 3. 读取已完成的实例 variables 作为上下文
        Map<String, Object> variables = instanceService.getVariables(instanceId);

        log.info("[FlowAutoTrigger] 检查 {} 条触发规则: sourceFlowCode={} instanceId={}",
                triggers.size(), sourceFlowCode, instanceId);

        // 4. 逐条评估并触发
        for (FlowAutoTriggerDO trigger : triggers) {
            try {
                processTrigger(trigger, instance, variables);
            } catch (Exception e) {
                log.error("[FlowAutoTrigger] 触发规则执行失败: triggerId={} sourceFlowCode={} targetFlowCode={} err={}",
                        trigger.getId(), sourceFlowCode, trigger.getTargetFlowCode(), e.getMessage(), e);
                writeAuditLog(instance, trigger, false, "执行异常: " + e.getMessage());
            }
        }
    }

    /**
     * 处理单条触发规则
     */
    private void processTrigger(FlowAutoTriggerDO trigger, FlowInstanceDO instance,
                                 Map<String, Object> variables) {
        String conditionExpression = trigger.getConditionExpression();

        // 5. 评估 conditionExpression（如果为空则无条件触发）
        boolean conditionMet = true;
        if (StringUtils.hasText(conditionExpression)) {
            try {
                conditionMet = routingService.evaluateCondition(conditionExpression, variables);
                log.info("[FlowAutoTrigger] 条件评估: triggerId={} expr={} result={}",
                        trigger.getId(), conditionExpression, conditionMet);
            } catch (Exception e) {
                log.warn("[FlowAutoTrigger] 条件表达式评估失败，默认不触发: triggerId={} expr={} err={}",
                        trigger.getId(), conditionExpression, e.getMessage());
                conditionMet = false;
            }
        }

        if (!conditionMet) {
            log.debug("[FlowAutoTrigger] 条件不满足，跳过: triggerId={} targetFlowCode={}",
                    trigger.getId(), trigger.getTargetFlowCode());
            return;
        }

        // 6. 构建启动 DTO 并调用 WorkflowFacade.startProcess 启动目标流程
        FlowStartProcessDTO startDto = new FlowStartProcessDTO();
        startDto.setFlowCode(trigger.getTargetFlowCode());
        startDto.setBusinessType(instance.getBusinessType());
        startDto.setBusinessId(instance.getBusinessId());
        startDto.setBusinessNo(instance.getBusinessNo());
        startDto.setTitle(buildTriggerTitle(trigger, instance));
        startDto.setInitiatorId(instance.getInitiatorId());
        startDto.setInitiatorName(instance.getInitiatorName());
        startDto.setVariables(variables);
        startDto.setTenantId(instance.getTenantId());
        startDto.setProviderTraceId(instance.getProviderTraceId());

        String targetInstanceId = workflowFacade.startProcess(startDto);

        log.info("[FlowAutoTrigger] 自动触发流程成功: sourceFlowCode={} sourceInstanceId={} "
                        + "targetFlowCode={} targetInstanceId={} triggerId={}",
                instance.getFlowCode(), instance.getId(),
                trigger.getTargetFlowCode(), targetInstanceId, trigger.getId());

        // 7. 写入审计日志
        writeAuditLog(instance, trigger, true, "自动触发成功: " + trigger.getTargetFlowCode()
                + " -> 实例 " + targetInstanceId);
    }

    /**
     * 构建自动触发流程的标题
     */
    private String buildTriggerTitle(FlowAutoTriggerDO trigger, FlowInstanceDO instance) {
        String base = trigger.getTargetFlowCode();
        if (StringUtils.hasText(trigger.getDescription())) {
            base = trigger.getDescription();
        }
        return "[" + base + "] 由 " + instance.getFlowCode() + "(" + instance.getId() + ") 自动触发";
    }

    /**
     * 写入审计日志
     */
    private void writeAuditLog(FlowInstanceDO instance, FlowAutoTriggerDO trigger,
                                boolean success, String comment) {
        try {
            FlowAuditLogDO logEntry = new FlowAuditLogDO();
            logEntry.setInstanceId(instance.getId());
            logEntry.setTaskId(null);
            logEntry.setFlowCode(instance.getFlowCode());
            logEntry.setBusinessType(instance.getBusinessType());
            logEntry.setBusinessId(instance.getBusinessId());
            logEntry.setNodeCode(null);
            logEntry.setNodeName(null);
            logEntry.setAction(success ? "AUTO_TRIGGER" : "AUTO_TRIGGER_FAIL");
            logEntry.setOperatorId(null);
            logEntry.setOperatorName("SYSTEM");
            logEntry.setTargetId(null);
            logEntry.setTargetName(trigger.getTargetFlowCode());
            logEntry.setComment(comment);
            logEntry.setOperatedAt(LocalDateTime.now());
            logEntry.setTenantId(instance.getTenantId());
            logEntry.setProviderTraceId(instance.getProviderTraceId());
            auditLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("[FlowAutoTrigger] 审计日志写入失败: instanceId={} triggerId={} err={}",
                    instance.getId(), trigger.getId(), e.getMessage());
        }
    }

    // ============================== 规则管理 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerTrigger(String sourceFlowCode, String targetFlowCode,
                                 String conditionExpression) {
        FlowAutoTriggerDO trigger = new FlowAutoTriggerDO();
        trigger.setSourceFlowCode(sourceFlowCode);
        trigger.setTargetFlowCode(targetFlowCode);
        trigger.setConditionExpression(conditionExpression);
        trigger.setEnabled(1);
        trigger.setSortOrder(0);
        autoTriggerMapper.insert(trigger);
        log.info("[FlowAutoTrigger] 注册触发规则: id={} source={} target={}",
                trigger.getId(), sourceFlowCode, targetFlowCode);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTrigger(String sourceFlowCode) {
        LambdaQueryWrapper<FlowAutoTriggerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAutoTriggerDO::getSourceFlowCode, sourceFlowCode);
        autoTriggerMapper.delete(wrapper);
        log.info("[FlowAutoTrigger] 移除触发规则: sourceFlowCode={}", sourceFlowCode);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowAutoTriggerDO> listAll() {
        LambdaQueryWrapper<FlowAutoTriggerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(FlowAutoTriggerDO::getSortOrder)
                .orderByAsc(FlowAutoTriggerDO::getId);
        return autoTriggerMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(String id) {
        autoTriggerMapper.deleteById(id);
        log.info("[FlowAutoTrigger] 删除触发规则: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(String id) {
        FlowAutoTriggerDO trigger = autoTriggerMapper.selectById(id);
        if (trigger == null) {
            log.warn("[FlowAutoTrigger] 触发规则不存在: id={}", id);
            return false;
        }
        int newEnabled = (trigger.getEnabled() != null && trigger.getEnabled() == 1) ? 0 : 1;
        trigger.setEnabled(newEnabled);
        autoTriggerMapper.updateById(trigger);
        log.info("[FlowAutoTrigger] 切换触发规则状态: id={} enabled={}", id, newEnabled);
        return newEnabled == 1;
    }
}