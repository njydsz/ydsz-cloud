package com.njydsz.pmis.workflow.service.impl.instance;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.spi.DecisionTableEvalProvider;
import com.njydsz.pmis.workflow.entity.analytics.FlowAuditLogDO;
import com.njydsz.pmis.workflow.entity.instance.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.instance.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.instance.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.analytics.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.instance.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.instance.FlowRoutingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能路由与异常检测服务实现
 *
 * <p>基于 ydsz-pmis-literule 的 RuleEngine 和 ExpressionEvaluator（Aviator 引擎），
 * 提供智能路由条件评估和流程异常检测能力。
 *
 * <p><b>条件注入</b>：仅当 Spring 容器中存在 RuleEngine 和 ExpressionEvaluator Bean 时，
 * 本实现才会被注册。如果 literule 模块未引入，则回退到 DefaultFlowVariableStrategy。
 *
 * <h3>异常检测</h3>
 * <ul>
 *   <li>超时检测：任务超过 dueAt 截止时间仍未完成</li>
 *   <li>卡单检测：任务在同一节点停留超过 24 小时</li>
 *   <li>循环审批：审计日志中同一节点被反复驳回（REJECT）超过 3 次</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@ConditionalOnBean({RuleEngine.class, ExpressionEvaluator.class})
public class FlowRoutingServiceImpl implements FlowRoutingService {

    /** 表达式求值器（Aviator 引擎），评估路由条件表达式 */
    private final ExpressionEvaluator expressionEvaluator;
    /** 运行时任务 Mapper，查询卡单/超期任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 审计日志 Mapper，查询循环审批等异常模式 */
    private final FlowAuditLogMapper auditLogMapper;
    /** 流程实例 Mapper，查询运行中实例状态 */
    private final FlowInstanceMapper instanceMapper;

    /** DMN 决策表评估提供者（SPI 可选依赖，未注入时 DMN 路由不可用，回退到 Aviator 评估） */
    private final DecisionTableEvalProvider decisionTableEvalProvider;

    /**
     * 构造注入：使用 {@link ObjectProvider} 支持可选依赖 {@link DecisionTableEvalProvider}。
     *
     * <p>通过 SPI 接口注入，解除 workflow 对 project 模块的编译期硬依赖；
     * project 模块的 {@code DecisionTableEvalService} 已实现该 SPI，由 Spring 自动装配。
     */
    public FlowRoutingServiceImpl(ExpressionEvaluator expressionEvaluator,
                                  FlowRunTaskMapper taskMapper,
                                  FlowAuditLogMapper auditLogMapper,
                                  FlowInstanceMapper instanceMapper,
                                  ObjectProvider<DecisionTableEvalProvider> decisionTableEvalProviderObjectProvider) {
        this.expressionEvaluator = expressionEvaluator;
        this.taskMapper = taskMapper;
        this.auditLogMapper = auditLogMapper;
        this.instanceMapper = instanceMapper;
        this.decisionTableEvalProvider = decisionTableEvalProviderObjectProvider.getIfAvailable();
    }

    // ============================== 路由评估 ==============================

    @Override
    public String evaluateRoute(String conditionExpression, Map<String, Object> variables) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            log.debug("[FlowRoute] 路由表达式为空，返回 null");
            return null;
        }
        // DMN 决策表路由：表达式以 "dmn:" 开头时，冒号后为决策表编码
        if (conditionExpression.startsWith("dmn:")) {
            String tableCode = conditionExpression.substring(4).trim();
            return evaluateRouteByDmn(tableCode, variables);
        }
        try {
            RuleContext context = buildContext(variables, "FLOW_ROUTE");
            Object result = expressionEvaluator.eval(conditionExpression, context);
            if (result == null) {
                log.debug("[FlowRoute] 路由表达式评估结果为 null: expr={}", conditionExpression);
                return null;
            }
            String nodeCode = result.toString();
            log.info("[FlowRoute] 路由命中: expr={} -> nodeCode={}", conditionExpression, nodeCode);
            return nodeCode;
        } catch (Exception e) {
            log.warn("[FlowRoute] 路由表达式评估失败: expr={}, err={}", conditionExpression, e.getMessage());
            return null;
        }
    }

    /**
     * 基于 DMN 决策表的路由评估
     *
     * <p>调用 {@link DecisionTableEvalProvider} SPI 评估决策表，
     * 取首条命中行的第一个动作值作为目标节点编码。
     *
     * <p>当 DecisionTableEvalProvider 未注入（如分服务部署、project 模块未引入）时，
     * 记录告警并返回 null，路由交由上层兜底处理。
     *
     * @param tableCode 决策表编码
     * @param variables 流程变量（作为决策表事实数据）
     * @return 目标节点编码；评估失败或无命中时返回 null
     */
    private String evaluateRouteByDmn(String tableCode, Map<String, Object> variables) {
        if (decisionTableEvalProvider == null) {
            log.warn("[FlowRoute] DMN 路由不可用（DecisionTableEvalProvider 未注入）: tableCode={}", tableCode);
            return null;
        }
        try {
            List<Map<String, Object>> results = decisionTableEvalProvider.evaluate(tableCode, variables);
            if (results == null || results.isEmpty()) {
                log.warn("[FlowRoute] DMN 路由无匹配结果: tableCode={}", tableCode);
                return null;
            }
            Map<String, Object> first = results.get(0);
            if (first == null || first.isEmpty()) {
                return null;
            }
            // 取第一个动作值作为目标节点编码
            Object target = first.values().iterator().next();
            String nodeCode = target == null ? null : target.toString();
            log.info("[FlowRoute] DMN 路由命中: tableCode={} -> nodeCode={}", tableCode, nodeCode);
            return nodeCode;
        } catch (Exception e) {
            log.warn("[FlowRoute] DMN 路由评估失败: tableCode={}, err={}", tableCode, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean evaluateCondition(String conditionExpression, Map<String, Object> variables) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            return true;
        }
        try {
            RuleContext context = buildContext(variables, "FLOW_CONDITION");
            boolean result = expressionEvaluator.evalBoolean(conditionExpression, context);
            log.debug("[FlowRoute] 条件评估: expr={} -> {}", conditionExpression, result);
            return result;
        } catch (Exception e) {
            log.warn("[FlowRoute] 条件表达式评估失败，默认返回 false: expr={}, err={}",
                    conditionExpression, e.getMessage());
            return false;
        }
    }

    // ============================== 异常检测 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> detectAnomalies(String instanceId) {
        if (instanceId == null) {
            return Collections.emptyList();
        }

        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            log.warn("[FlowRoute] 实例不存在，跳过异常检测: instanceId={}", instanceId);
            return Collections.emptyList();
        }

        List<Map<String, Object>> anomalies = new ArrayList<>();

        // 检测顺序：超时 -> 卡单 -> 循环审批
        detectTimeout(instanceId, anomalies);
        detectStuck(instanceId, anomalies);
        detectLoop(instanceId, anomalies);

        if (!anomalies.isEmpty()) {
            log.warn("[FlowRoute] 检测到 {} 项异常: instanceId={}", anomalies.size(), instanceId);
        } else {
            log.debug("[FlowRoute] 未检测到异常: instanceId={}", instanceId);
        }

        return anomalies;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAnomaly(String instanceId) {
        return !detectAnomalies(instanceId).isEmpty();
    }

    // ============================== 私有方法 ==============================

    /**
     * 构建 literule 规则上下文
     *
     * @param variables 流程变量
     * @param scenario  业务场景标识
     * @return RuleContext 实例
     */
    private RuleContext buildContext(Map<String, Object> variables, String scenario) {
        Map<String, Object> facts = variables != null
                ? new LinkedHashMap<>(variables)
                : Collections.emptyMap();
        return RuleContext.of(facts, scenario, "FlowRoutingService");
    }

    /**
     * 超时检测：任务超过 dueAt 截止时间仍未完成
     *
     * <p>遍历实例下所有任务，筛选出 dueAt 已过期且任务状态未完成的记录。
     */
    private void detectTimeout(String instanceId, List<Map<String, Object>> anomalies) {
        List<FlowRunTaskDO> tasks = taskMapper.selectByInstanceId(instanceId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (FlowRunTaskDO task : tasks) {
            if (task.getDueAt() == null) {
                continue;
            }
            if (task.getDueAt().isBefore(now) && !FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
                long overdueMinutes = Duration.between(task.getDueAt(), now).toMinutes();
                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "TIMEOUT");
                anomaly.put("taskId", task.getId());
                anomaly.put("nodeCode", task.getNodeCode());
                anomaly.put("nodeName", task.getNodeName());
                anomaly.put("dueAt", task.getDueAt().toString());
                anomaly.put("overdueMinutes", overdueMinutes);
                anomaly.put("taskStatus", task.getTaskStatus());
                anomaly.put("description", "任务超时未完成: " + task.getTitle()
                        + " (截止时间 " + task.getDueAt() + "，已超期 " + overdueMinutes + " 分钟)");
                anomalies.add(anomaly);
                log.info("[FlowRoute] 超时检测: taskId={} nodeCode={} overdueMinutes={}",
                        task.getId(), task.getNodeCode(), overdueMinutes);
            }
        }
    }

    /**
     * 卡单检测：任务在同一节点停留超过 24 小时
     *
     * <p>以任务的创建时间（createdAt）为起点，计算停留时长。
     * 仅检测未完成的任务。
     */
    private void detectStuck(String instanceId, List<Map<String, Object>> anomalies) {
        List<FlowRunTaskDO> tasks = taskMapper.selectByInstanceId(instanceId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (FlowRunTaskDO task : tasks) {
            if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
                continue;
            }
            LocalDateTime createdAt = task.getCreatedAt();
            if (createdAt == null) {
                continue;
            }
            long hours = Duration.between(createdAt, now).toHours();
            if (hours >= 24) {
                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "STUCK");
                anomaly.put("taskId", task.getId());
                anomaly.put("nodeCode", task.getNodeCode());
                anomaly.put("nodeName", task.getNodeName());
                anomaly.put("stuckHours", hours);
                anomaly.put("createdAt", createdAt.toString());
                anomaly.put("taskStatus", task.getTaskStatus());
                anomaly.put("description", "卡单超过 " + hours + " 小时: " + task.getNodeName()
                        + " (创建时间 " + createdAt + ")");
                anomalies.add(anomaly);
                log.info("[FlowRoute] 卡单检测: taskId={} nodeCode={} stuckHours={}",
                        task.getId(), task.getNodeCode(), hours);
            }
        }
    }

    /**
     * 循环审批检测：审计日志中同一节点被反复驳回（REJECT）超过 3 次
     *
     * <p>统计审计日志中 action=REJECT 的记录，按节点编码分组计数。
     * 任意节点驳回次数超过 3 次即视为循环审批异常。
     */
    private void detectLoop(String instanceId, List<Map<String, Object>> anomalies) {
        List<FlowAuditLogDO> logs = auditLogMapper.selectByInstanceId(instanceId);
        if (logs == null || logs.isEmpty()) {
            return;
        }

        // 按 nodeCode 分组统计 REJECT 次数
        Map<String, Long> rejectCountByNode = logs.stream()
                .filter(log -> "REJECT".equalsIgnoreCase(log.getAction()))
                .filter(log -> log.getNodeCode() != null)
                .collect(Collectors.groupingBy(
                        FlowAuditLogDO::getNodeCode,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        // 筛选驳回次数超过阈值的节点
        for (Map.Entry<String, Long> entry : rejectCountByNode.entrySet()) {
            if (entry.getValue() > 3) {
                // 获取节点名称（从审计日志中取最近一条的名称）
                String nodeName = logs.stream()
                        .filter(log -> entry.getKey().equals(log.getNodeCode()))
                        .filter(log -> log.getNodeName() != null)
                        .map(FlowAuditLogDO::getNodeName)
                        .findFirst()
                        .orElse(entry.getKey());

                Map<String, Object> anomaly = new LinkedHashMap<>();
                anomaly.put("type", "LOOP");
                anomaly.put("nodeCode", entry.getKey());
                anomaly.put("nodeName", nodeName);
                anomaly.put("rejectCount", entry.getValue());
                anomaly.put("description", "节点反复驳回超过 3 次: " + nodeName
                        + " (共 " + entry.getValue() + " 次驳回)");
                anomalies.add(anomaly);
                log.info("[FlowRoute] 循环审批检测: nodeCode={} rejectCount={}",
                        entry.getKey(), entry.getValue());
            }
        }
    }
}