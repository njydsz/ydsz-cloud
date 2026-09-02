package com.njydsz.workflow.server.engine.impl;

import java.util.Collections;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.server.engine.expr.ExpressionEvaluator;

/**
 * 默认流程变量表达式解析策略（外观模式 / Facade）
 *
 * <p>本组件是工作流条件评估的统一入口，内部优先委托 Aviator 表达式引擎进行求值，
 * 以统一项目中的表达式引擎，避免多引擎并存导致的语义不一致问题。
 *
 * <p>具体实现委托给以下四个专职组件：
 *
 * <ul>
 *   <li>{@link FlowExpressionEvaluator} — 条件表达式求值器（正则解析路径）
 *   <li>{@link FlowAssigneeExpressionResolver} — 办理人表达式解析器（正则路径）
 *   <li>{@link FlowVariableReplacer} — 变量替换器（占位符替换 / 字段反射）
 *   <li>{@link FlowExpressionUtils} — 表达式解析工具类（静态常量与通用方法）
 * </ul>
 *
 * <h3>P1-3 引擎收敛：Aviator 单引擎策略</h3>
 *
 * <ol>
 *   <li>若 Spring 容器中存在 {@link ExpressionEvaluator} Bean，则优先使用 Aviator 求值（主路径）
 *   <li>若 Aviator 求值失败（表达式语法不兼容等），自动回退到内置正则解析器，并输出<b>降级告警</b>日志（WARN 级别）
 *   <li>若 Aviator 不可用（classpath 中无 aviator 依赖），直接使用内置正则解析器，并输出一次性<b>降级告警</b>日志
 * </ol>
 *
 * <p><b>SpEL 已废弃：</b>自 P1-3 起，SpEL 不再作为运行时求值引擎，条件评估统一收敛为 Aviator，正则解析器仅作兼容性兜底。
 *
 * <h3>向后兼容语法</h3>
 *
 * <ul>
 *   <li>${var} - 简单占位符替换
 *   <li>${var > 100} - 简单比较表达式
 *   <li>${a > 100} && ${b < 50} - 逻辑与（P2-14）
 *   <li>${a > 100} || ${b < 50} - 逻辑或（P2-14）
 *   <li>!${flag} - 逻辑非（P2-14）
 *   <li>${cond ? 'A' : 'B'} - 三元运算符（P2-14）
 *   <li>固定字符串：role:hr / dept:10 / user:1001
 *   <li>纯 Aviator 表达式：amount > 100 && type == 'VIP'（无 ${} 包裹）
 * </ul>
 *
 * <p>当使用 Aviator 引擎时，${} 包裹会被自动剥离，内部表达式直接交给 Aviator 求值。
 * 不带 ${} 的表达式视为纯 Aviator 表达式直接求值。
 *
 * <h3>P2-1 引擎独立化</h3>
 *
 * <ul>
 *   <li>引擎内置 Aviator 表达式求值能力，无需依赖 ydsz-literule 模块即可运行
 *   <li>业务系统可通过 {@link ExpressionEvaluator} SPI 替换为更强大的规则引擎实现
 *   <li>若 Aviator 不可用（classpath 中无 aviator 依赖），直接使用内置正则解析器降级求值
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class DefaultFlowVariableStrategy {

  /**
   * Aviator 表达式求值器（可选注入）。
   *
   * <p>当 ydsz-literule 模块启用时自动注入；未启用时为 null，回退到正则解析。
   */
  private final ExpressionEvaluator expressionEvaluator;

  /** 条件表达式求值器（正则解析路径，Aviator 降级时使用） */
  private final FlowExpressionEvaluator expressionEvaluatorLegacy;

  /** 办理人表达式解析器（正则路径，Aviator 降级时使用） */
  private final FlowAssigneeExpressionResolver assigneeResolverLegacy;

  /** 标记 Aviator 不可用的警告是否已输出过（避免日志刷屏） */
  private volatile boolean aviatorUnavailableLogged = false;

  /**
   * 构造注入：使用 {@link ObjectProvider} 支持可选依赖。
   *
   *
   * @param evaluatorProvider Aviator 表达式求值器提供者（可选）
   * @param expressionEvaluatorLegacy 正则条件表达式求值器（降级路径）
   * @param assigneeResolverLegacy 办理人表达式解析器（正则路径）
   */
  public DefaultFlowVariableStrategy(
      ObjectProvider<ExpressionEvaluator> evaluatorProvider,
      FlowExpressionEvaluator expressionEvaluatorLegacy,
      FlowAssigneeExpressionResolver assigneeResolverLegacy) {
    this.expressionEvaluator = evaluatorProvider.getIfAvailable();
    this.expressionEvaluatorLegacy = expressionEvaluatorLegacy;
    this.assigneeResolverLegacy = assigneeResolverLegacy;
  }

  public boolean evaluate(String condition, Map<String, Object> variables) {
    if (condition == null || condition.isBlank()) {
      return true;
    }
    // 优先使用 Aviator 引擎求值（统一表达式引擎）
    if (expressionEvaluator != null) {
      try {
        // 剥离 ${} 包裹，转换为 Aviator 原生语法
        String aviatorExpr = FlowExpressionUtils.stripPlaceholders(condition.trim());
        Map<String, Object> facts = variables != null ? variables : Collections.emptyMap();
        boolean result = expressionEvaluator.evalBoolean(aviatorExpr, facts);
        log.debug(
            "[Flow] Aviator 条件评估: expr='{}' aviatorExpr='{}' -> {}",
            condition,
            aviatorExpr,
            result);
        return result;
      } catch (Exception e) {
        // P1-3: 降级告警 — Aviator 求值失败，回退到自研正则解析器
        log.warn(
            "[Flow][降级告警] Aviator 求值失败，回退到正则解析器: expr='{}' err={}",
            condition,
            e.getMessage());
      }
    } else {
      // P1-3: 降级告警 — Aviator 不可用，回退到自研正则解析器（仅输出一次）
      if (!aviatorUnavailableLogged) {
        log.warn(
            "[Flow][降级告警] Aviator 表达式引擎不可用，使用正则解析器降级求值。"
                + "建议启用 ydsz-literule 模块以获得统一的 Aviator 表达式支持。");
        aviatorUnavailableLogged = true;
      }
    }
    // 回退到正则解析器降级路径
    return expressionEvaluatorLegacy.evaluateLegacy(condition, variables);
  }

  public String resolveAssignee(String expression, Map<String, Object> variables) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    String trimmed = expression.trim();

    // 优先使用 Aviator 引擎解析（仅对 ${} 包裹的表达式尝试）
    if (expressionEvaluator != null && trimmed.startsWith("${") && trimmed.endsWith("}")) {
      try {
        // 剥离所有 ${} 包裹（含嵌套），转换为 Aviator 原生语法
        String aviatorExpr = FlowExpressionUtils.stripPlaceholders(trimmed);
        Map<String, Object> facts = variables != null ? variables : Collections.emptyMap();
        Object result = expressionEvaluator.eval(aviatorExpr, facts);
        if (result != null) {
          String resolved = result.toString();
          log.debug(
              "[Flow] Aviator 办理人解析: expr='{}' aviatorExpr='{}' -> '{}'",
              expression,
              aviatorExpr,
              resolved);
          return resolved;
        }
      } catch (Exception e) {
        log.debug(
            "[Flow] Aviator 办理人解析失败，回退到正则解析器: expr='{}' err={}",
            expression,
            e.getMessage());
      }
    }

    // 回退到传统解析逻辑（传入 this::evaluate 保持三元条件中的 Aviator 优先语义）
    return assigneeResolverLegacy.resolveAssigneeLegacy(trimmed, variables, this::evaluate);
  }
}
