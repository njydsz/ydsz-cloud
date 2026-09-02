package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.expr.ExpressionEvaluator;

/**
 * 默认流程路由服务实现（引擎自包含）
 *
 * <p>基于引擎内置的 {@link ExpressionEvaluator}（默认 Aviator 引擎）提供<b>路由条件评估</b>和<b>异常检测</b>能力。
 *
 * <p>业务系统如需更强大的规则引擎能力（如规则链、决策表、复杂规则编排），可实现本类的同名 Spring Bean 覆盖注册。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>引擎自包含</b>：不依赖外部规则引擎模块，classpath 中有 Aviator 时自动启用表达式求值
 *   <li><b>能力降级</b>：Aviator 不可用时表达式评估返回 null/false，不影响流程主链路
 *   <li><b>异常检测</b>：超时/卡单/循环审批检测通过数据库查询实现，不依赖外部模块
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExpressionEvaluator 表达式求值器 SPI
 */
@Slf4j
@Service
@ConditionalOnMissingBean(DefaultFlowRoutingService.class)
public class DefaultFlowRoutingService {

    /** 卡单判定阈值（小时） */
  private static final int STUCK_HOURS_THRESHOLD = 24;

  /** 同节点最大驳回次数（超过判定为循环驳回） */
  private static final int MAX_REJECT_COUNT = 3;

  /** 表达式求值器，评估路由条件表达式 */
  private final ExpressionEvaluator expressionEvaluator;

  /** 运行时任务仓储，查询卡单/超期任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 审计日志仓储，查询循环审批等异常模式 */
  private final FlowAuditLogRepository auditLogRepository;

  /** 流程实例仓储，查询运行中实例状态 */
  private final FlowInstanceRepository instanceRepository;

  public DefaultFlowRoutingService(
      ExpressionEvaluator expressionEvaluator,
      FlowRunTaskRepository taskRepository,
      FlowAuditLogRepository auditLogRepository,
      FlowInstanceRepository instanceRepository) {
    this.expressionEvaluator = expressionEvaluator;
    this.taskRepository = taskRepository;
    this.auditLogRepository = auditLogRepository;
    this.instanceRepository = instanceRepository;
  }

  // ============================== 路由评估 ==============================

  public String evaluateRoute(String conditionExpression, Map<String, Object> variables) {
    if (conditionExpression == null || conditionExpression.isBlank()) {
      log.debug("[FlowRoute] 路由表达式为空，返回 null");
      return null;
    }
    try {
      Object result = expressionEvaluator.eval(conditionExpression, variables);
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

  public boolean evaluateCondition(String conditionExpression, Map<String, Object> variables) {
    if (conditionExpression == null || conditionExpression.isBlank()) {
      return true;
    }
    try {
      boolean result = expressionEvaluator.evalBoolean(conditionExpression, variables);
      log.debug("[FlowRoute] 条件评估: expr={} -> {}", conditionExpression, result);
      return result;
    } catch (Exception e) {
      log.warn(
          "[FlowRoute] 条件表达式评估失败，默认返回 false: expr={}, err={}", conditionExpression, e.getMessage());
      return false;
    }
  }

  // ============================== 异常检测 ==============================

  @Transactional(readOnly = true)
  public List<Map<String, Object>> detectAnomalies(String instanceId) {
    if (instanceId == null) {
      return Collections.emptyList();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      log.warn("[FlowRoute] 实例不存在，跳过异常检测: instanceId={}", instanceId);
      return Collections.emptyList();
    }
    List<Map<String, Object>> anomalies = new ArrayList<>(16);
    detectTimeout(instanceId, anomalies);
    detectStuck(instanceId, anomalies);
    detectLoop(instanceId, anomalies);
    if (!anomalies.isEmpty()) {
      log.warn("[FlowRoute] 检测到 {} 项异常: instanceId={}", anomalies.size(), instanceId);
    }
    return anomalies;
  }

  @Transactional(readOnly = true)
  public boolean isAnomaly(String instanceId) {
    return !detectAnomalies(instanceId).isEmpty();
  }

  // ============================== 私有方法 ==============================

  private void detectTimeout(String instanceId, List<Map<String, Object>> anomalies) {
    List<FlowRunTaskVO> tasks = taskRepository.findByInstanceId(instanceId);
    if (tasks == null || tasks.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (FlowRunTaskVO task : tasks) {
      if (task.getDueAt() == null) {
        continue;
      }
      if (task.getDueAt().isBefore(now)
          && !FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
        long overdueMinutes = Duration.between(task.getDueAt(), now).toMinutes();
        Map<String, Object> anomaly = new LinkedHashMap<>(16);
        anomaly.put("type", "TIMEOUT");
        anomaly.put("taskId", task.getId());
        anomaly.put("nodeCode", task.getNodeCode());
        anomaly.put("nodeName", task.getNodeName());
        anomaly.put("dueAt", task.getDueAt().toString());
        anomaly.put("overdueMinutes", overdueMinutes);
        anomaly.put("taskStatus", task.getTaskStatus());
        anomaly.put(
            "description",
            "任务超时未完成: "
                + task.getTitle()
                + " (截止时间 "
                + task.getDueAt()
                + "，已超期 "
                + overdueMinutes
                + " 分钟)");
        anomalies.add(anomaly);
      }
    }
  }

  private void detectStuck(String instanceId, List<Map<String, Object>> anomalies) {
    List<FlowRunTaskVO> tasks = taskRepository.findByInstanceId(instanceId);
    if (tasks == null || tasks.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (FlowRunTaskVO task : tasks) {
      if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
        continue;
      }
      LocalDateTime createdAt = task.getCreatedAt();
      if (createdAt == null) {
        continue;
      }
      long hours = Duration.between(createdAt, now).toHours();
      if (hours >= STUCK_HOURS_THRESHOLD) {
        Map<String, Object> anomaly = new LinkedHashMap<>(16);
        anomaly.put("type", "STUCK");
        anomaly.put("taskId", task.getId());
        anomaly.put("nodeCode", task.getNodeCode());
        anomaly.put("nodeName", task.getNodeName());
        anomaly.put("stuckHours", hours);
        anomaly.put("createdAt", createdAt.toString());
        anomaly.put("taskStatus", task.getTaskStatus());
        anomaly.put(
            "description",
            "卡单超过 " + hours + " 小时: " + task.getNodeName() + " (创建时间 " + createdAt + ")");
        anomalies.add(anomaly);
      }
    }
  }

  private void detectLoop(String instanceId, List<Map<String, Object>> anomalies) {
    List<FlowAuditLogVO> logs = auditLogRepository.findByInstanceId(instanceId);
    if (logs == null || logs.isEmpty()) {
      return;
    }
    Map<String, Long> rejectCountByNode =
        logs.stream()
            .filter(log -> "REJECT".equalsIgnoreCase(log.getAction()))
            .filter(log -> log.getNodeCode() != null)
            .collect(
                Collectors.groupingBy(
                    FlowAuditLogVO::getNodeCode, LinkedHashMap::new, Collectors.counting()));
    for (Map.Entry<String, Long> entry : rejectCountByNode.entrySet()) {
      if (entry.getValue() > MAX_REJECT_COUNT) {
        String nodeName =
            logs.stream()
                .filter(log -> entry.getKey().equals(log.getNodeCode()))
                .filter(log -> log.getNodeName() != null)
                .map(FlowAuditLogVO::getNodeName)
                .findFirst()
                .orElse(entry.getKey());
        Map<String, Object> anomaly = new LinkedHashMap<>(16);
        anomaly.put("type", "LOOP");
        anomaly.put("nodeCode", entry.getKey());
        anomaly.put("nodeName", nodeName);
        anomaly.put("rejectCount", entry.getValue());
        anomaly.put(
            "description", "节点反复驳回超过 3 次: " + nodeName + " (共 " + entry.getValue() + " 次驳回)");
        anomalies.add(anomaly);
      }
    }
  }
}
