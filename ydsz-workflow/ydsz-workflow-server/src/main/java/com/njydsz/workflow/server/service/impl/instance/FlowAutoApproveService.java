package com.njydsz.workflow.server.service.impl.instance;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.server.engine.FlowServiceNodeExecutor;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskCoreService;

/**
 * 自动审批服务
 *
 * <p>负责流程任务的<b>自动审批</b>逻辑，支持配置化规则引擎（{@code INITIATOR_IS_APPROVER / AMOUNT_BELOW / EXPR / ALWAYS}）。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>规则引擎</b>：支持多规则配置（rules 数组），每条规则可指定 type + action</li>
 *   <li><b>向后兼容</b>：兼容旧配置（enabled + whenInitiatorIsApprover + expr 单条规则格式）</li>
 *   <li><b>动作执行</b>：命中规则后自动执行 PASS / REJECT 动作</li>
 * </ul>
 *
 * <p><b>ext JSON 配置示例：</b>
 *
 * <pre>
 * {
 * "autoApprove": {
 * "enabled": true,
 * "rules": [
 * {"type": "INITIATOR_IS_APPROVER", "action": "PASS"},
 * {"type": "AMOUNT_BELOW", "threshold": 1000, "variable": "amount", "action": "PASS"},
 * {"type": "EXPR", "expr": "deptType == 'engineering' && urgency == 'low'", "action": "PASS"},
 * {"type": "AMOUNT_ABOVE", "threshold": 100000, "variable": "amount", "action": "REJECT"}
 * ]
 * }
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class FlowAutoApproveService {

  private final FlowTaskCoreService flowTaskCoreService;
  private final FlowServiceNodeExecutor serviceNodeExecutor;

  /**
   * 构造函数
   *
   * @param flowTaskCoreService 任务核心服务
   * @param serviceNodeExecutor 服务节点执行器（表达式求值）
   */
  public FlowAutoApproveService(
      FlowTaskCoreService flowTaskCoreService,
      FlowServiceNodeExecutor serviceNodeExecutor) {
    this.flowTaskCoreService = flowTaskCoreService;
    this.serviceNodeExecutor = serviceNodeExecutor;
  }

  /**
   * 尝试自动审批
   *
   * @param instance 流程实例
   * @param node 流程节点
   * @param task 运行时任务
   * @param variables 流程变量
   */
  public void tryAutoApprove(
      FlowInstanceVO instance, FlowNodeVO node, FlowRunTaskVO task, Map<String, Object> variables) {
    Map<String, Object> cfg = checkAutoApproveConditions(node, task);
    if (cfg == null) {
      return;
    }
    Map<String, Object> env = buildAutoApproveContext(instance, task, node, variables);
    executeAutoApprove(cfg, env, instance, node, task, variables);
  }

  /**
   * 检查是否满足自动审批触发条件
   *
   * @param node 流程节点
   * @param task 运行时任务
   * @return 自动审批配置 Map，不满足时返回 null
   */
  private Map<String, Object> checkAutoApproveConditions(FlowNodeVO node, FlowRunTaskVO task) {
    if (node.getExt() == null || node.getExt().isBlank()) {
      return Collections.emptyMap();
    }
    Map<String, Object> extConfig;
    try {
      extConfig = FlowNodeExt.parseSafe(node.getExt());
    } catch (Exception e) {
      return Collections.emptyMap();
    }
    if (extConfig == null) {
      return Collections.emptyMap();
    }
    Object autoApproveObj = extConfig.get("autoApprove");
    if (!(autoApproveObj instanceof Map<?, ?> autoApprove)) {
      return Collections.emptyMap();
    }
    Map<String, Object> cfg = MapUtils.toStringObjectMap(autoApprove);
    Boolean enabled = (Boolean) cfg.get("enabled");
    if (enabled == null || !enabled) {
      return Collections.emptyMap();
    }
    // 仅单人 OR 模式自动通过
    if (!FlowPerformType.OR.name().equals(task.getPerformType())) {
      return Collections.emptyMap();
    }
    return cfg;
  }

  /**
   * 构建自动审批评估环境变量
   *
   * @param instance 流程实例
   * @param task 运行时任务
   * @param node 流程节点
   * @param variables 流程变量
   * @return 环境变量 Map
   */
  private Map<String, Object> buildAutoApproveContext(
      FlowInstanceVO instance, FlowRunTaskVO task, FlowNodeVO node, Map<String, Object> variables) {
    Map<String, Object> env = new HashMap<>();
    if (variables != null) {
      env.putAll(variables);
    }
    env.put("_initiatorId", instance.getInitiatorId());
    env.put("_assigneeId", task.getAssigneeId());
    env.put("_nodeCode", node.getNodeCode());
    return env;
  }

  /**
   * 评估自动审批规则并执行匹配的动作
   *
   * @param cfg 自动审批配置
   * @param env 环境变量
   * @param instance 流程实例
   * @param node 流程节点
   * @param task 运行时任务
   * @param variables 流程变量
   */
  private void executeAutoApprove(
      Map<String, Object> cfg,
      Map<String, Object> env,
      FlowInstanceVO instance,
      FlowNodeVO node,
      FlowRunTaskVO task,
      Map<String, Object> variables) {
    // 优先使用 rules 数组（新配置）
    Object rulesObj = cfg.get("rules");
    if (rulesObj instanceof List<?> rulesList && !rulesList.isEmpty()) {
      for (Object ruleObj : rulesList) {
        if (!(ruleObj instanceof Map<?, ?> rule)) {
          continue;
        }
        Map<String, Object> ruleCfg = MapUtils.toStringObjectMap(rule);
        String action = evaluateAutoApproveRule(ruleCfg, instance, task, env);
        if (action != null) {
          executeAutoAction(action, instance, node, task, variables, ruleCfg);
          return;
        }
      }
      return;
    }

    // 兼容旧配置：单条规则
    boolean matched = false;
    String action = "PASS";

    Object whenInitiator = cfg.get("whenInitiatorIsApprover");
    if (Boolean.TRUE.equals(whenInitiator) && instance.getInitiatorId() != null) {
      String initiator = String.valueOf(instance.getInitiatorId());
      if (initiator.equals(task.getAssigneeId())
          || (task.getAssigneeName() != null && task.getAssigneeName().contains(initiator))) {
        matched = true;
      }
    }
    if (!matched) {
      Object exprObj = cfg.get("expr");
      if (exprObj instanceof String expr && !expr.isBlank()) {
        try {
          Object result = serviceNodeExecutor.evalExpr(expr, env);
          matched = Boolean.TRUE.equals(result);
        } catch (Exception e) {
          log.warn(
              "[Flow] 自动审批表达式求值失败 node={} expr={} err={}",
              node.getNodeCode(),
              exprObj,
              e.getMessage());
        }
      }
    }
    if (matched) {
      executeAutoAction(action, instance, node, task, variables, null);
    }
  }

  /**
   * 评估单条自动审批规则
   *
   * @param rule 规则配置
   * @param instance 流程实例
   * @param task 运行时任务
   * @param env 环境变量
   * @return 命中时返回动作（PASS/REJECT），未命中返回 null
   */
  private String evaluateAutoApproveRule(
      Map<String, Object> rule, FlowInstanceVO instance, FlowRunTaskVO task, Map<String, Object> env) {
    String type = String.valueOf(rule.getOrDefault("type", "")).toUpperCase();
    String action = String.valueOf(rule.getOrDefault("action", "PASS")).toUpperCase();
    boolean matched = false;

    switch (type) {
      case "INITIATOR_IS_APPROVER" -> {
        if (instance.getInitiatorId() != null) {
          String initiator = String.valueOf(instance.getInitiatorId());
          matched =
              initiator.equals(task.getAssigneeId())
                  || (task.getAssigneeName() != null && task.getAssigneeName().contains(initiator));
        }
      }
      case "EXPR" -> {
        Object exprObj = rule.get("expr");
        if (exprObj instanceof String expr && !expr.isBlank()) {
          try {
            Object result = serviceNodeExecutor.evalExpr(expr, env);
            matched = Boolean.TRUE.equals(result);
          } catch (Exception e) {
            log.warn(
                "[Flow] P0-4 自动审批规则表达式求值失败: type={} expr={} err={}",
                type,
                exprObj,
                e.getMessage());
          }
        }
      }
      case "AMOUNT_BELOW" -> {
        String varName = String.valueOf(rule.getOrDefault("variable", "amount"));
        Object thresholdObj = rule.get("threshold");
        Object val = env.get(varName);
        if (thresholdObj != null && val instanceof Number n) {
          double threshold = ((Number) thresholdObj).doubleValue();
          matched = n.doubleValue() < threshold;
        }
      }
      case "AMOUNT_ABOVE" -> {
        String varName = String.valueOf(rule.getOrDefault("variable", "amount"));
        Object thresholdObj = rule.get("threshold");
        Object val = env.get(varName);
        if (thresholdObj != null && val instanceof Number n) {
          double threshold = ((Number) thresholdObj).doubleValue();
          matched = n.doubleValue() > threshold;
        }
      }
      case "ALWAYS" -> matched = true;
      default -> log.debug("[Flow] P0-4 未知自动审批规则类型: type={}", type);
    }

    return matched ? action : null;
  }

  /**
   * 执行自动审批动作（PASS / REJECT）
   *
   * @param action 动作类型
   * @param instance 流程实例
   * @param node 流程节点
   * @param task 运行时任务
   * @param variables 流程变量
   * @param ruleCfg 规则配置（可为 null，表示旧配置）
   */
  private void executeAutoAction(
      String action,
      FlowInstanceVO instance,
      FlowNodeVO node,
      FlowRunTaskVO task,
      Map<String, Object> variables,
      Map<String, Object> ruleCfg) {
    FlowTaskOperateDTO autoDto = new FlowTaskOperateDTO();
    autoDto.setTaskId(task.getId());
    autoDto.setUserId("0");
    autoDto.setUserName("SYSTEM_AUTO_APPROVE");
    String ruleDesc =
        ruleCfg != null ? String.valueOf(ruleCfg.getOrDefault("type", "UNKNOWN")) : "LEGACY";
    if ("REJECT".equals(action)) {
      autoDto.setComment("P0-4 自动审批规则[" + ruleDesc + "]命中，自动驳回");
      try {
        flowTaskCoreService.reject(autoDto);
        log.info(
            "[Flow] P0-4 自动审批规则驳回: instanceId={} node={} taskId={} rule={}",
            instance.getId(),
            node.getNodeCode(),
            task.getId(),
            ruleDesc);
      } catch (Exception e) {
        log.warn(
            "[Flow] P0-4 自动审批驳回失败（降级为人工）: instanceId={} node={} err={}",
            instance.getId(),
            node.getNodeCode(),
            e.getMessage());
      }
    } else {
      autoDto.setComment("P0-4 自动审批规则[" + ruleDesc + "]命中，自动通过");
      autoDto.setVariables(variables);
      try {
        flowTaskCoreService.pass(autoDto);
        log.info(
            "[Flow] P0-4 自动审批规则通过: instanceId={} node={} taskId={} rule={}",
            instance.getId(),
            node.getNodeCode(),
            task.getId(),
            ruleDesc);
      } catch (Exception e) {
        log.warn(
            "[Flow] P0-4 自动审批通过失败（降级为人工）: instanceId={} node={} err={}",
            instance.getId(),
            node.getNodeCode(),
            e.getMessage());
      }
    }
  }
}
