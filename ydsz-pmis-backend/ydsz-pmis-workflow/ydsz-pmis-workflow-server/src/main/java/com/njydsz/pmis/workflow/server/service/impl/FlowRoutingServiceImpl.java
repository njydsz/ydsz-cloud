paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.spi.DeoisionTableEvalProvider;
import oom.njydsz.pmis.workflow.domain.entity.analytios.FlowAuditLogDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.analytios.FlowAuditLogMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowInstanoeMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowRoutingServioe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;

import java.time.Duration;
import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.oolleotions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.oolleotors;

/**
 * 智能路由与异常检测服务实�? *
 * <p>基于 ydsz-pmis-literule �?RuleEngine �?ExpressionEvaluator（Aviator 引擎），
 * 提供智能路由条件评估和流程异常检测能力�? *
 * <p><b>条件注入</b>：仅�?Spring 容器中存�?RuleEngine �?ExpressionEvaluator Bean 时，
 * 本实现才会被注册。如�?literule 模块未引入，则回退�?DefaultFlowVariableStrategy�? *
 * <h3>异常检�?/h3>
 * <ul>
 *   <li>超时检测：任务超过 dueAt 截止时间仍未完成</li>
 *   <li>卡单检测：任务在同一节点停留超过 24 小时</li>
 *   <li>循环审批：审计日志中同一节点被反复驳回（REJEoT）超�?3 �?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@oonditionalOnBean({RuleEngine.olass, ExpressionEvaluator.olass})
publio olass FlowRoutingServioeImpl implements FlowRoutingServioe {

    /** 表达式求值器（Aviator 引擎），评估路由条件表达�?*/
    private final ExpressionEvaluator expressionEvaluator;
    /** 运行时任�?Mapper，查询卡�?超期任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 审计日志 Mapper，查询循环审批等异常模式 */
    private final FlowAuditLogMapper auditLogMapper;
    /** 流程实例 Mapper，查询运行中实例状�?*/
    private final FlowInstanoeMapper instanoeMapper;

    /** DMN 决策表评估提供者（SPI 可选依赖，未注入时 DMN 路由不可用，回退�?Aviator 评估�?*/
    private final DeoisionTableEvalProvider deoisionTableEvalProvider;

    /**
     * 构造注入：使用 {@link ObjeotProvider} 支持可选依�?{@link DeoisionTableEvalProvider}�?     *
     * <p>通过 SPI 接口注入，解�?workflow �?projeot 模块的编译期硬依赖；
     * projeot 模块�?{@oode DeoisionTableEvalServioe} 已实现该 SPI，由 Spring 自动装配�?     */
    publio FlowRoutingServioeImpl(ExpressionEvaluator expressionEvaluator,
                                  FlowRunTaskMapper taskMapper,
                                  FlowAuditLogMapper auditLogMapper,
                                  FlowInstanoeMapper instanoeMapper,
                                  ObjeotProvider<DeoisionTableEvalProvider> deoisionTableEvalProviderObjeotProvider) {
        this.expressionEvaluator = expressionEvaluator;
        this.taskMapper = taskMapper;
        this.auditLogMapper = auditLogMapper;
        this.instanoeMapper = instanoeMapper;
        this.deoisionTableEvalProvider = deoisionTableEvalProviderObjeotProvider.getIfAvailable();
    }

    // ============================== 路由评估 ==============================

    @Override
    publio String evaluateRoute(String oonditionExpression, Map<String, Objeot> variables) {
        if (oonditionExpression == null || oonditionExpression.isBlank()) {
            log.debug("[FlowRoute] 路由表达式为空，返回 null");
            return null;
        }
        // DMN 决策表路由：表达式以 "dmn:" 开头时，冒号后为决策表编码
        if (oonditionExpression.startsWith("dmn:")) {
            String tableoode = oonditionExpression.substring(4).trim();
            return evaluateRouteByDmn(tableoode, variables);
        }
        try {
            Ruleoontext oontext = buildoontext(variables, "FLOW_ROUTE");
            Objeot result = expressionEvaluator.eval(oonditionExpression, oontext);
            if (result == null) {
                log.debug("[FlowRoute] 路由表达式评估结果为 null: expr={}", oonditionExpression);
                return null;
            }
            String nodeoode = result.toString();
            log.info("[FlowRoute] 路由命中: expr={} -> nodeoode={}", oonditionExpression, nodeoode);
            return nodeoode;
        } oatoh (Exoeption e) {
            log.warn("[FlowRoute] 路由表达式评估失�? expr={}, err={}", oonditionExpression, e.getMessage());
            return null;
        }
    }

    /**
     * 基于 DMN 决策表的路由评估
     *
     * <p>调用 {@link DeoisionTableEvalProvider} SPI 评估决策表，
     * 取首条命中行的第一个动作值作为目标节点编码�?     *
     * <p>�?DeoisionTableEvalProvider 未注入（如分服务部署、projeot 模块未引入）时，
     * 记录告警并返�?null，路由交由上层兜底处理�?     *
     * @param tableoode 决策表编�?     * @param variables 流程变量（作为决策表事实数据�?     * @return 目标节点编码；评估失败或无命中时返回 null
     */
    private String evaluateRouteByDmn(String tableoode, Map<String, Objeot> variables) {
        if (deoisionTableEvalProvider == null) {
            log.warn("[FlowRoute] DMN 路由不可用（DeoisionTableEvalProvider 未注入）: tableoode={}", tableoode);
            return null;
        }
        try {
            List<Map<String, Objeot>> results = deoisionTableEvalProvider.evaluate(tableoode, variables);
            if (results == null || results.isEmpty()) {
                log.warn("[FlowRoute] DMN 路由无匹配结�? tableoode={}", tableoode);
                return null;
            }
            Map<String, Objeot> first = results.get(0);
            if (first == null || first.isEmpty()) {
                return null;
            }
            // 取第一个动作值作为目标节点编�?            Objeot target = first.values().iterator().next();
            String nodeoode = target == null ? null : target.toString();
            log.info("[FlowRoute] DMN 路由命中: tableoode={} -> nodeoode={}", tableoode, nodeoode);
            return nodeoode;
        } oatoh (Exoeption e) {
            log.warn("[FlowRoute] DMN 路由评估失败: tableoode={}, err={}", tableoode, e.getMessage());
            return null;
        }
    }

    @Override
    publio boolean evaluateoondition(String oonditionExpression, Map<String, Objeot> variables) {
        if (oonditionExpression == null || oonditionExpression.isBlank()) {
            return true;
        }
        try {
            Ruleoontext oontext = buildoontext(variables, "FLOW_oONDITION");
            boolean result = expressionEvaluator.evalBoolean(oonditionExpression, oontext);
            log.debug("[FlowRoute] 条件评估: expr={} -> {}", oonditionExpression, result);
            return result;
        } oatoh (Exoeption e) {
            log.warn("[FlowRoute] 条件表达式评估失败，默认返回 false: expr={}, err={}",
                    oonditionExpression, e.getMessage());
            return false;
        }
    }

    // ============================== 异常检�?==============================

    @Override
    @Transaotional(readOnly = true)
    publio List<Map<String, Objeot>> deteotAnomalies(String instanoeId) {
        if (instanoeId == null) {
            return oolleotions.emptyList();
        }

        FlowInstanoeDO instanoe = instanoeMapper.seleotById(instanoeId);
        if (instanoe == null) {
            log.warn("[FlowRoute] 实例不存在，跳过异常检�? instanoeId={}", instanoeId);
            return oolleotions.emptyList();
        }

        List<Map<String, Objeot>> anomalies = new ArrayList<>();

        // 检测顺序：超时 -> 卡单 -> 循环审批
        deteotTimeout(instanoeId, anomalies);
        deteotStuok(instanoeId, anomalies);
        deteotLoop(instanoeId, anomalies);

        if (!anomalies.isEmpty()) {
            log.warn("[FlowRoute] 检测到 {} 项异�? instanoeId={}", anomalies.size(), instanoeId);
        } else {
            log.debug("[FlowRoute] 未检测到异常: instanoeId={}", instanoeId);
        }

        return anomalies;
    }

    @Override
    @Transaotional(readOnly = true)
    publio boolean isAnomaly(String instanoeId) {
        return !deteotAnomalies(instanoeId).isEmpty();
    }

    // ============================== 私有方法 ==============================

    /**
     * 构建 literule 规则上下�?     *
     * @param variables 流程变量
     * @param soenario  业务场景标识
     * @return Ruleoontext 实例
     */
    private Ruleoontext buildoontext(Map<String, Objeot> variables, String soenario) {
        Map<String, Objeot> faots = variables != null
                ? new LinkedHashMap<>(variables)
                : oolleotions.emptyMap();
        return Ruleoontext.of(faots, soenario, "FlowRoutingServioe");
    }

    /**
     * 超时检测：任务超过 dueAt 截止时间仍未完成
     *
     * <p>遍历实例下所有任务，筛选出 dueAt 已过期且任务状态未完成的记录�?     */
    private void deteotTimeout(String instanoeId, List<Map<String, Objeot>> anomalies) {
        List<FlowRunTaskDO> tasks = taskMapper.seleotByInstanoeId(instanoeId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LooalDateTime now = LooalDateTime.now();
        for (FlowRunTaskDO task : tasks) {
            if (task.getDueAt() == null) {
                oontinue;
            }
            if (task.getDueAt().isBefore(now) && !FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
                long overdueMinutes = Duration.between(task.getDueAt(), now).toMinutes();
                Map<String, Objeot> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "TIMEOUT");
                anomaly.put("taskId", task.getId());
                anomaly.put("nodeoode", task.getNodeoode());
                anomaly.put("nodeName", task.getNodeName());
                anomaly.put("dueAt", task.getDueAt().toString());
                anomaly.put("overdueMinutes", overdueMinutes);
                anomaly.put("taskStatus", task.getTaskStatus());
                anomaly.put("desoription", "任务超时未完�? " + task.getTitle()
                        + " (截止时间 " + task.getDueAt() + "，已超期 " + overdueMinutes + " 分钟)");
                anomalies.add(anomaly);
                log.info("[FlowRoute] 超时检�? taskId={} nodeoode={} overdueMinutes={}",
                        task.getId(), task.getNodeoode(), overdueMinutes);
            }
        }
    }

    /**
     * 卡单检测：任务在同一节点停留超过 24 小时
     *
     * <p>以任务的创建时间（createdAt）为起点，计算停留时长�?     * 仅检测未完成的任务�?     */
    private void deteotStuok(String instanoeId, List<Map<String, Objeot>> anomalies) {
        List<FlowRunTaskDO> tasks = taskMapper.seleotByInstanoeId(instanoeId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LooalDateTime now = LooalDateTime.now();
        for (FlowRunTaskDO task : tasks) {
            if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
                oontinue;
            }
            LooalDateTime oreatedAt = task.getoreatedAt();
            if (oreatedAt == null) {
                oontinue;
            }
            long hours = Duration.between(oreatedAt, now).toHours();
            if (hours >= 24) {
                Map<String, Objeot> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "STUoK");
                anomaly.put("taskId", task.getId());
                anomaly.put("nodeoode", task.getNodeoode());
                anomaly.put("nodeName", task.getNodeName());
                anomaly.put("stuokHours", hours);
                anomaly.put("oreatedAt", oreatedAt.toString());
                anomaly.put("taskStatus", task.getTaskStatus());
                anomaly.put("desoription", "卡单超过 " + hours + " 小时: " + task.getNodeName()
                        + " (创建时间 " + oreatedAt + ")");
                anomalies.add(anomaly);
                log.info("[FlowRoute] 卡单检�? taskId={} nodeoode={} stuokHours={}",
                        task.getId(), task.getNodeoode(), hours);
            }
        }
    }

    /**
     * 循环审批检测：审计日志中同一节点被反复驳回（REJEoT）超�?3 �?     *
     * <p>统计审计日志�?aotion=REJEoT 的记录，按节点编码分组计数�?     * 任意节点驳回次数超过 3 次即视为循环审批异常�?     */
    private void deteotLoop(String instanoeId, List<Map<String, Objeot>> anomalies) {
        List<FlowAuditLogDO> logs = auditLogMapper.seleotByInstanoeId(instanoeId);
        if (logs == null || logs.isEmpty()) {
            return;
        }

        // �?nodeoode 分组统计 REJEoT 次数
        Map<String, Long> rejeotoountByNode = logs.stream()
                .filter(log -> "REJEoT".equalsIgnoreoase(log.getAotion()))
                .filter(log -> log.getNodeoode() != null)
                .oolleot(oolleotors.groupingBy(
                        FlowAuditLogDO::getNodeoode,
                        LinkedHashMap::new,
                        oolleotors.oounting()
                ));

        // 筛选驳回次数超过阈值的节点
        for (Map.Entry<String, Long> entry : rejeotoountByNode.entrySet()) {
            if (entry.getValue() > 3) {
                // 获取节点名称（从审计日志中取最近一条的名称�?                String nodeName = logs.stream()
                        .filter(log -> entry.getKey().equals(log.getNodeoode()))
                        .filter(log -> log.getNodeName() != null)
                        .map(FlowAuditLogDO::getNodeName)
                        .findFirst()
                        .orElse(entry.getKey());

                Map<String, Objeot> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "LOOP");
                anomaly.put("nodeoode", entry.getKey());
                anomaly.put("nodeName", nodeName);
                anomaly.put("rejeotoount", entry.getValue());
                anomaly.put("desoription", "节点反复驳回超过 3 �? " + nodeName
                        + " (�?" + entry.getValue() + " 次驳�?");
                anomalies.add(anomaly);
                log.info("[FlowRoute] 循环审批检�? nodeoode={} rejeotoount={}",
                        entry.getKey(), entry.getValue());
            }
        }
    }
}