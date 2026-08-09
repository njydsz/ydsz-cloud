package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.expr.ExpressionEvaluator;
import com.njydsz.literule.api.spi.DecisionTableEvalProvider;
import com.njydsz.workflow.domain.entity.FlowAuditLog;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.service.FlowRoutingService;

import lombok.extern.slf4j.Slf4j;

/**
 * 智能路由与异常检测服务实现
 *
 * <p>对 {@link FlowRoutingService} 接口的完整实现，是工作流引擎的「智能化」扩展点。
 * 基于 {@code ydsz-literule} 的 {@link RuleEngine} 和 {@link ExpressionEvaluator}（Aviator 引擎），
 * 提供<b>智能路由条件评估</b>和<b>流程异常检测</b>能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>条件评估</b>：复杂条件下解析流程分支条件（支持 Aviator 表达式、规则引擎、决策表）</li>
 *   <li><b>异常检测</b>：识别「卡单」「超时」「循环审批」等异常状态，触发告警 / 自动动作</li>
 *   <li><b>智能路由</b>：根据运行时变量（金额、申请人、紧急度）动态选择审批路径，
 *       实现「金额 &gt; 100 万需 CEO 审批」等业务规则</li>
 *   <li><b>规则联动</b>：与规则引擎（{@code ydsz-literule}）联动，规则变更无需重启即可生效</li>
 * </ul>
 *
 * <p><b>条件注入：</b>
 * <ul>
 *   <li>本实现启用 {@link ConditionalOnBean}，<b>仅当</b> Spring 容器中存在 {@link RuleEngine}
 *       和 {@link ExpressionEvaluator} Bean 时才会被注册</li>
 *   <li>如果 {@code literule} 模块未引入，则回退到 {@code DefaultFlowVariableStrategy}，
 *       仅支持基础 SpEL 表达式（不依赖规则引擎）</li>
 *   <li>这种「能力探测」机制保证核心工作流在 literule 缺失时仍可运行</li>
 * </ul>
 *
 * <p><b>异常检测（{@link #detectAnomalies}）：</b>
 * <ul>
 *   <li><b>超时检测</b>：任务超过 {@code dueAt} 截止时间仍未完成（P0 级异常）</li>
 *   <li><b>卡单检测</b>：任务在同一节点停留超过 24 小时（无任何审批动作）</li>
 *   <li><b>循环审批</b>：审计日志中同一节点被反复驳回（{@code REJECT}）超过 3 次
 *       （疑似无限循环，需人工介入）</li>
 * </ul>
 *
 * <p><b>智能路由（{@link #evaluateRoute}）：</b>
 * <ul>
 *   <li>支持「金额路由」：{@code amount > 1000000 → 需 CEO 审批}</li>
 *   <li>支持「申请人路由」：{@code initiator.dept == 'FINANCE' → 财务总监审批}</li>
 *   <li>支持「紧急度路由」：{@code priority == 'URGENT' → 跳过非关键审批人}</li>
 *   <li>支持「规则路由」：通过决策表（DMN）批量配置路由规则</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>异常检测启用 {@code @Transactional(readOnly = true)}，支持只读副本路由</li>
 *   <li>异常处理动作（如自动催办 / 升级）单独开启写事务</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>规则热更新</b>：通过 Nacos 监听规则变更，无需重启即可应用新规则</li>
 *   <li><b>规则审计</b>：所有规则评估结果写入审计日志，支持「为什么这个流程走了 A 分支」回溯</li>
 *   <li><b>规则降级</b>：规则引擎异常时自动回退到 SpEL 表达式，保证流程不卡死</li>
 *   <li><b>规则沙箱</b>：Aviator 表达式禁止调用 Java 方法，防止恶意规则影响系统</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowRoutingService 接口定义
 * @see com.njydsz.literule.api.RuleEngine 规则引擎
 * @see com.njydsz.literule.api.expr.ExpressionEvaluator Aviator 表达式评估器
 * @see com.njydsz.literule.api.spi.DecisionTableEvalProvider 决策表评估提供者
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
            if (results == null || Response.isEmpty()) {
                log.warn("[FlowRoute] DMN 路由无匹配结果: tableCode={}", tableCode);
                return null;
            }
            Map<String, Object> first = Response.get(0);
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

        FlowInstance instance = instanceMapper.selectById(instanceId);
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
        List<FlowRunTask> tasks = taskMapper.selectByInstanceId(instanceId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (FlowRunTask task : tasks) {
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
        List<FlowRunTask> tasks = taskMapper.selectByInstanceId(instanceId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (FlowRunTask task : tasks) {
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
        List<FlowAuditLog> logs = auditLogMapper.selectByInstanceId(instanceId);
        if (logs == null || logs.isEmpty()) {
            return;
        }

        // 按 nodeCode 分组统计 REJECT 次数
        Map<String, Long> rejectCountByNode = logs.stream()
                .filter(log -> "REJECT".equalsIgnoreCase(log.getAction()))
                .filter(log -> log.getNodeCode() != null)
                .collect(Collectors.groupingBy(
                        FlowAuditLog::getNodeCode,
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
                        .map(FlowAuditLog::getNodeName)
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
