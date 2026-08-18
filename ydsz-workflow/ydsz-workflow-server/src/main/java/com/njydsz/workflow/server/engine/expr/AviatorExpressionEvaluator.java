package com.njydsz.workflow.server.engine.expr;

import java.util.Map;

import com.googlecode.aviator.AviatorEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 工作流内置 Aviator 表达式求值器
 *
 * <p>引擎自包含的表达式求值实现，基于 Aviator 5.x。当 Aviator 在 classpath 中且无其他 {@link ExpressionEvaluator}
 * 实现时自动启用。
 *
 * <p>业务系统如需更强大的规则引擎能力（如规则链、决策表），可自行实现 {@link ExpressionEvaluator} 并注册为 Bean 覆盖本实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnClass(AviatorEvaluator.class)
@ConditionalOnMissingBean(ExpressionEvaluator.class)
public class AviatorExpressionEvaluator implements ExpressionEvaluator {

  @Override
  public boolean evalBoolean(String expression, Map<String, Object> variables) {
    if (expression == null || expression.isBlank()) {
      return true;
    }
    try {
      Object result = AviatorEvaluator.execute(expression, variables != null ? variables : Map.of(), true);
      if (result instanceof Boolean b) {
        return b;
      }
      if (result instanceof Number n) {
        return n.doubleValue() != 0;
      }
      return result != null;
    } catch (Exception e) {
      log.warn("[Flow][Aviator] 布尔表达式求值失败: expr='{}' err={}", expression, e.getMessage());
      return false;
    }
  }

  @Override
  public Object eval(String expression, Map<String, Object> variables) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    try {
      return AviatorEvaluator.execute(expression, variables != null ? variables : Map.of(), true);
    } catch (Exception e) {
      log.warn("[Flow][Aviator] 表达式求值失败: expr='{}' err={}", expression, e.getMessage());
      return null;
    }
  }
}
