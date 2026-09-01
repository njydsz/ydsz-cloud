package com.njydsz.literule.server.config;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.server.impl.ExpressionRule;

/**
 * A/B 测试服务
 *
 * <p>基于当前规则定义与候选规则定义，对同一份事实数据分别评估， 输出触发结果与严重度的对比报告（Winner / 差异 / 建议）。
 * 评估为纯仿真：不发布事件、不记录统计、不落库。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class ABTestService {

  /** 表达式求值器（构造表达式规则） */
  private final ExpressionEngine evaluator;

  /**
   * 构造 A/B 测试服务
   *
   * @param evaluator 表达式求值器
   */
  public ABTestService(ExpressionEngine evaluator) {
    this.evaluator = evaluator;
  }

  /**
   * 对当前规则与候选规则执行 A/B 对比评估
   *
   * @param currentDef 当前线上规则定义
   * @param candidateDef 候选规则定义
   * @param facts 测试用事实数据
   * @return A/B 对比报告
   */
  public ABTestReport test(
      RuleDefinitionDTO currentDef, RuleDefinitionDTO candidateDef, Map<String, Object> facts) {
    RuleContextVO context = RuleContextVO.of(facts, "AB_TEST", "MANUAL");
    RuleResultVO currentResult = evaluateSafely(currentDef, context);
    RuleResultVO candidateResult = evaluateSafely(candidateDef, context);

    Winner winner = decideWinner(currentResult, candidateResult);
    String conclusion = buildConclusion(currentDef, currentResult, candidateResult, winner);

    return ABTestReport.builder()
        .ruleCode(currentDef.getCode())
        .currentTriggered(currentResult.isTriggered())
        .candidateTriggered(candidateResult.isTriggered())
        .currentSeverity(currentResult.getSeverity())
        .candidateSeverity(candidateResult.getSeverity())
        .winner(winner)
        .conclusion(conclusion)
        .evaluatedAt(LocalDateTime.now())
        .build();
  }

  /**
   * 安全评估单条规则定义（异常隔离，异常时返回未触发结果）
   *
   * @param definition 规则定义
   * @param context 评估上下文
   * @return 评估结果
   */
  private RuleResultVO evaluateSafely(RuleDefinitionDTO definition, RuleContextVO context) {
    try {
      ExpressionRule rule = new ExpressionRule(definition, evaluator);
      return rule.evaluate(context);
    } catch (Exception e) {
      log.warn("[LiteRule-ABTest] 规则评估异常，按未触发处理: ruleCode={}, error={}",
          definition.getCode(), e.getMessage());
      return RuleResultVO.notTriggered(definition.getCode());
    }
  }

  /**
   * 判定 A/B 对比的胜者
   *
   * <p>仅当两者都触发时按严重度权重比较；一方触发则触发方胜；都未触发为平局。
   *
   * @param currentResult 当前规则结果
   * @param candidateResult 候选规则结果
   * @return 胜者
   */
  private Winner decideWinner(RuleResultVO currentResult, RuleResultVO candidateResult) {
    boolean currentTriggered = currentResult.isTriggered();
    boolean candidateTriggered = candidateResult.isTriggered();
    if (currentTriggered && candidateTriggered) {
      int currentWeight = currentResult.getSeverity() != null ? currentResult.getSeverity().getWeight() : 0;
      int candidateWeight =
          candidateResult.getSeverity() != null ? candidateResult.getSeverity().getWeight() : 0;
      if (candidateWeight > currentWeight) {
        return Winner.CANDIDATE;
      }
      if (candidateWeight < currentWeight) {
        return Winner.CURRENT;
      }
      return Winner.TIE;
    }
    if (candidateTriggered) {
      return Winner.CANDIDATE;
    }
    if (currentTriggered) {
      return Winner.CURRENT;
    }
    return Winner.NONE;
  }

  /**
   * 构建对比结论文案
   *
   * @param ruleCode 规则编码
   * @param currentResult 当前规则结果
   * @param candidateResult 候选规则结果
   * @param winner 胜者
   * @return 结论文案
   */
  private String buildConclusion(
      RuleDefinitionDTO ruleCode,
      RuleResultVO currentResult,
      RuleResultVO candidateResult,
      Winner winner) {
    return switch (winner) {
      case CANDIDATE ->
          String.format(
              "候选规则触发结果更严格（%s vs %s），建议评估后发布候选版本",
              severityText(candidateResult), severityText(currentResult));
      case CURRENT ->
          String.format(
              "当前规则触发结果更严格（%s vs %s），候选版本无收益",
              severityText(currentResult), severityText(candidateResult));
      case TIE ->
          String.format(
              "两者触发结果一致（%s），需结合更多样本评估",
              severityText(currentResult));
      case NONE -> "两者均未触发，候选版本与当前版本行为一致";
    };
  }

  /**
   * 严重度文本（未触发时返回"未触发"）
   *
   * @param result 规则结果
   * @return 严重度文本
   */
  private String severityText(RuleResultVO result) {
    if (result == null || !result.isTriggered() || result.getSeverity() == null) {
      return "未触发";
    }
    return result.getSeverity().getDesc();
  }

  /** A/B 对比胜者 */
  public enum Winner {
    /** 当前规则更优 */
    CURRENT,
    /** 候选规则更优 */
    CANDIDATE,
    /** 两者相当 */
    TIE,
    /** 均未触发 */
    NONE
  }

  /**
   * A/B 对比报告（字段语义见字段注释）。
   */
  @Builder
  @Data
  public static class ABTestReport {
    /** 规则编码 */
    private String ruleCode;
    /** 当前规则是否触发 */
    private boolean currentTriggered;
    /** 候选规则是否触发 */
    private boolean candidateTriggered;
    /** 当前规则严重度 */
    private RuleSeverity currentSeverity;
    /** 候选规则严重度 */
    private RuleSeverity candidateSeverity;
    /** 对比胜者 */
    private Winner winner;
    /** 结论文案 */
    private String conclusion;
    /** 评估时间 */
    private LocalDateTime evaluatedAt;
  }
}
