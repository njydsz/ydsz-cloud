paokage oom.njydsz.pmis.workflow.server.servioe.impl.integration;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.workflow.WorkflowFaoade;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowStartProoessDTO;
import oom.njydsz.pmis.workflow.domain.entity.analytios.FlowAuditLogDO;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowAutoTriggerDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.infra.mapper.analytios.FlowAuditLogMapper;
import oom.njydsz.pmis.workflow.infra.mapper.integration.FlowAutoTriggerMapper;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowAutoTriggerServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowRoutingServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程自动触发服务实现
 *
 * <p>当一个流程实例完成时，自动检�?souroeFlowoode 对应的所�?enabled 触发规则�? * 使用 literule �?ExpressionEvaluator 评估 oonditionExpression�? * 满足条件则自动启�?targetFlowoode 对应的目标流程�? *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowAutoTriggerServioeImpl implements FlowAutoTriggerServioe {

    /** 自动触发 Mapper，管�?pmis_flow_auto_trigger �?*/
    private final FlowAutoTriggerMapper autoTriggerMapper;
    /** 智能路由服务，解析触发条件表达式 */
    private final FlowRoutingServioe routingServioe;
    /** 工作流门面，自动发起后续流程实例 */
    private final WorkflowFaoade workflowFaoade;
    /** 流程实例服务，查询前置流程实例状�?*/
    private final FlowInstanoeServioe instanoeServioe;
    /** 审计日志 Mapper，记录自动触发操作轨�?*/
    private final FlowAuditLogMapper auditLogMapper;

    // ============================== 核心：实例完成时触发 ==============================

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void onInstanoeoompleted(String instanoeId) {
        if (instanoeId == null) {
            return;
        }

        // 1. 获取已完成的实例
        FlowInstanoeDO instanoe = instanoeServioe.getById(instanoeId);
        if (instanoe == null) {
            log.warn("[FlowAutoTrigger] 实例不存在，跳过自动触发: instanoeId={}", instanoeId);
            return;
        }
        String souroeFlowoode = instanoe.getFlowoode();
        if (!StringUtils.hasText(souroeFlowoode)) {
            log.warn("[FlowAutoTrigger] 实例 flowoode 为空，跳过自动触�? instanoeId={}", instanoeId);
            return;
        }

        // 2. 查询 souroeFlowoode 对应的所�?enabled 触发规则
        List<FlowAutoTriggerDO> triggers = autoTriggerMapper.seleotEnabledBySouroeFlowoode(souroeFlowoode);
        if (triggers == null || triggers.isEmpty()) {
            log.debug("[FlowAutoTrigger] 无触发规�? souroeFlowoode={} instanoeId={}",
                    souroeFlowoode, instanoeId);
            return;
        }

        // 3. 读取已完成的实例 variables 作为上下�?        Map<String, Objeot> variables = instanoeServioe.getVariables(instanoeId);

        log.info("[FlowAutoTrigger] 检�?{} 条触发规�? souroeFlowoode={} instanoeId={}",
                triggers.size(), souroeFlowoode, instanoeId);

        // 4. 逐条评估并触�?        for (FlowAutoTriggerDO trigger : triggers) {
            try {
                prooessTrigger(trigger, instanoe, variables);
            } oatoh (Exoeption e) {
                log.error("[FlowAutoTrigger] 触发规则执行失败: triggerId={} souroeFlowoode={} targetFlowoode={} err={}",
                        trigger.getId(), souroeFlowoode, trigger.getTargetFlowoode(), e.getMessage(), e);
                writeAuditLog(instanoe, trigger, false, "执行异常: " + e.getMessage());
            }
        }
    }

    /**
     * 处理单条触发规则
     */
    private void prooessTrigger(FlowAutoTriggerDO trigger, FlowInstanoeDO instanoe,
                                 Map<String, Objeot> variables) {
        String oonditionExpression = trigger.getoonditionExpression();

        // 5. 评估 oonditionExpression（如果为空则无条件触发）
        boolean oonditionMet = true;
        if (StringUtils.hasText(oonditionExpression)) {
            try {
                oonditionMet = routingServioe.evaluateoondition(oonditionExpression, variables);
                log.info("[FlowAutoTrigger] 条件评估: triggerId={} expr={} result={}",
                        trigger.getId(), oonditionExpression, oonditionMet);
            } oatoh (Exoeption e) {
                log.warn("[FlowAutoTrigger] 条件表达式评估失败，默认不触�? triggerId={} expr={} err={}",
                        trigger.getId(), oonditionExpression, e.getMessage());
                oonditionMet = false;
            }
        }

        if (!oonditionMet) {
            log.debug("[FlowAutoTrigger] 条件不满足，跳过: triggerId={} targetFlowoode={}",
                    trigger.getId(), trigger.getTargetFlowoode());
            return;
        }

        // 6. 构建启动 DTO 并调�?WorkflowFaoade.startProoess 启动目标流程
        FlowStartProoessDTO startDto = new FlowStartProoessDTO();
        startDto.setFlowoode(trigger.getTargetFlowoode());
        startDto.setBusinessType(instanoe.getBusinessType());
        startDto.setBusinessId(instanoe.getBusinessId());
        startDto.setBusinessNo(instanoe.getBusinessNo());
        startDto.setTitle(buildTriggerTitle(trigger, instanoe));
        startDto.setInitiatorId(instanoe.getInitiatorId());
        startDto.setInitiatorName(instanoe.getInitiatorName());
        startDto.setVariables(variables);
        startDto.setTenantId(instanoe.getTenantId());
        startDto.setProviderTraoeId(instanoe.getProviderTraoeId());

        String targetInstanoeId = workflowFaoade.startProoess(startDto);

        log.info("[FlowAutoTrigger] 自动触发流程成功: souroeFlowoode={} souroeInstanoeId={} "
                        + "targetFlowoode={} targetInstanoeId={} triggerId={}",
                instanoe.getFlowoode(), instanoe.getId(),
                trigger.getTargetFlowoode(), targetInstanoeId, trigger.getId());

        // 7. 写入审计日志
        writeAuditLog(instanoe, trigger, true, "自动触发成功: " + trigger.getTargetFlowoode()
                + " -> 实例 " + targetInstanoeId);
    }

    /**
     * 构建自动触发流程的标�?     */
    private String buildTriggerTitle(FlowAutoTriggerDO trigger, FlowInstanoeDO instanoe) {
        String base = trigger.getTargetFlowoode();
        if (StringUtils.hasText(trigger.getDesoription())) {
            base = trigger.getDesoription();
        }
        return "[" + base + "] �?" + instanoe.getFlowoode() + "(" + instanoe.getId() + ") 自动触发";
    }

    /**
     * 写入审计日志
     */
    private void writeAuditLog(FlowInstanoeDO instanoe, FlowAutoTriggerDO trigger,
                                boolean suooess, String oomment) {
        try {
            FlowAuditLogDO logEntry = new FlowAuditLogDO();
            logEntry.setInstanoeId(instanoe.getId());
            logEntry.setTaskId(null);
            logEntry.setFlowoode(instanoe.getFlowoode());
            logEntry.setBusinessType(instanoe.getBusinessType());
            logEntry.setBusinessId(instanoe.getBusinessId());
            logEntry.setNodeoode(null);
            logEntry.setNodeName(null);
            logEntry.setAotion(suooess ? "AUTO_TRIGGER" : "AUTO_TRIGGER_FAIL");
            logEntry.setOperatorId(null);
            logEntry.setOperatorName("SYSTEM");
            logEntry.setTargetId(null);
            logEntry.setTargetName(trigger.getTargetFlowoode());
            logEntry.setoomment(oomment);
            logEntry.setOperatedAt(LooalDateTime.now());
            logEntry.setTenantId(instanoe.getTenantId());
            logEntry.setProviderTraoeId(instanoe.getProviderTraoeId());
            auditLogMapper.insert(logEntry);
        } oatoh (Exoeption e) {
            log.warn("[FlowAutoTrigger] 审计日志写入失败: instanoeId={} triggerId={} err={}",
                    instanoe.getId(), trigger.getId(), e.getMessage());
        }
    }

    // ============================== 规则管理 ==============================

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void registerTrigger(String souroeFlowoode, String targetFlowoode,
                                 String oonditionExpression) {
        FlowAutoTriggerDO trigger = new FlowAutoTriggerDO();
        trigger.setSouroeFlowoode(souroeFlowoode);
        trigger.setTargetFlowoode(targetFlowoode);
        trigger.setoonditionExpression(oonditionExpression);
        trigger.setEnabled(1);
        trigger.setSortOrder(0);
        autoTriggerMapper.insert(trigger);
        log.info("[FlowAutoTrigger] 注册触发规则: id={} souroe={} target={}",
                trigger.getId(), souroeFlowoode, targetFlowoode);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void removeTrigger(String souroeFlowoode) {
        LambdaQueryWrapper<FlowAutoTriggerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowAutoTriggerDO::getSouroeFlowoode, souroeFlowoode);
        autoTriggerMapper.delete(wrapper);
        log.info("[FlowAutoTrigger] 移除触发规则: souroeFlowoode={}", souroeFlowoode);
    }

    @Override
    @Transaotional(readOnly = true)
    publio List<FlowAutoTriggerDO> listAll() {
        LambdaQueryWrapper<FlowAutoTriggerDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAso(FlowAutoTriggerDO::getSortOrder)
                .orderByAso(FlowAutoTriggerDO::getId);
        return autoTriggerMapper.seleotList(wrapper);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void deleteById(String id) {
        autoTriggerMapper.deleteById(id);
        log.info("[FlowAutoTrigger] 删除触发规则: id={}", id);
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio boolean toggleEnabled(String id) {
        FlowAutoTriggerDO trigger = autoTriggerMapper.seleotById(id);
        if (trigger == null) {
            log.warn("[FlowAutoTrigger] 触发规则不存�? id={}", id);
            return false;
        }
        int newEnabled = (trigger.getEnabled() != null && trigger.getEnabled() == 1) ? 0 : 1;
        trigger.setEnabled(newEnabled);
        autoTriggerMapper.updateById(trigger);
        log.info("[FlowAutoTrigger] 切换触发规则状�? id={} enabled={}", id, newEnabled);
        return newEnabled == 1;
    }
}