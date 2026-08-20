package com.njydsz.literule.server.core;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleExecutionTrace;
import com.njydsz.literule.api.RuleResult;

/**
 * 规则执行轨迹构建器
 *
 * <p>封装 {@link RuleExecutionTrace} 的构建逻辑，从 {@link DefaultRuleEngine} 提取，使引擎核心聚焦评估编排。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class RuleTraceBuilder {

  /**
   * 构建执行轨迹记录
   *
   * @param context 规则上下文
   * @param rule 规则
   * @param result 评估结果（可能为 null）
   * @param elapsedMs 耗时
   * @param exception 评估异常（可能为 null）
   * @return 轨迹记录
   */
  public RuleExecutionTrace buildTrace(
      RuleContext context, Rule rule, RuleResult result, long elapsedMs, Exception exception) {
    String severity =
        result != null && result.getSeverity() != null ? result.getSeverity().getCode() : null;
    String conditionResult =
        result != null && result.getThreshold() != null ? result.getThreshold() : null;

    Map<String, Object> resultSnapshot = new LinkedHashMap<>();
    if (result != null) {
      resultSnapshot.put("triggered", result.isTriggered());
      resultSnapshot.put("severity", severity);
      resultSnapshot.put("title", result.getTitle());
      resultSnapshot.put("description", result.getDescription());
    }

    return new RuleExecutionTrace(
        context.getTraceId(),
        rule.getCode(),
        rule.getName(),
        context.getScenario(),
        result != null && result.isTriggered(),
        severity,
        conditionResult,
        elapsedMs,
        new LinkedHashMap<>(context.getFacts()),
        resultSnapshot,
        exception != null ? exception.getMessage() : null);
  }
}
