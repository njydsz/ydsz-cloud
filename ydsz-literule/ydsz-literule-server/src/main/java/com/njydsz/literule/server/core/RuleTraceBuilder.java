package com.njydsz.literule.server.core;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 规则执行轨迹构建器
 *
 * <p>封装 {@link RuleExecutionTraceVO} 的构建逻辑，从 {@link DefaultRuleEngine} 提取，使引擎核心聚焦评估编排。
 *
 * @since 26.09.01
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
  public RuleExecutionTraceVO buildTrace(
      RuleContextVO context, Rule rule, RuleResultVO result, long elapsedMs, Exception exception) {
    String severity =
        result != null && result.getSeverity() != null ? result.getSeverity() : null;
    String conditionResult =
        result != null && result.getThreshold() != null ? result.getThreshold() : null;

    Map<String, Object> resultSnapshot = new LinkedHashMap<>(16);
    if (result != null) {
      resultSnapshot.put("triggered", result.isTriggered());
      resultSnapshot.put("severity", severity);
      resultSnapshot.put("title", result.getTitle());
      resultSnapshot.put("description", result.getDescription());
    }

    return RuleExecutionTraceVO.builder()
        .traceId(context.getTraceId())
        .ruleCode(rule.getCode())
        .ruleName(rule.getName())
        .scenario(context.getScenario())
        .triggered(result != null && result.isTriggered())
        .severity(severity)
        .conditionResult(conditionResult)
        .elapsedMs(elapsedMs)
        .factsSnapshot(new LinkedHashMap<>(context.getFacts()))
        .resultSnapshot(resultSnapshot)
        .errorMessage(exception != null ? exception.getMessage() : null)
        .build();
  }
}
