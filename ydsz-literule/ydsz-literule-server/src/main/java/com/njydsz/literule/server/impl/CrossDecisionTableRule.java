package com.njydsz.literule.server.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.api.CrossDecisionTableDefinition;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.api.RuleSeverity;
import com.njydsz.literule.api.expression.ExpressionEngine;

/**
 * 交叉决策表规则（决策矩阵运行时，P0-3 补全）
 *
 * <p>行和列双维度交叉匹配：从 facts 中取行维度值（{@code rowDimension}）与列维度值
 * （{@code columnDimension}），按分桶顺序首中即定索引，交叉查表：
 *
 * <ol>
 *   <li>行维度值按 {@code rowBuckets} 顺序匹配，首个命中的桶索引 = 行索引
 *   <li>列维度值按 {@code columnBuckets} 顺序匹配，首个命中的桶索引 = 列索引
 *   <li>查 {@code cells["rowIndex_columnIndex"]} 取动作；无单元格或行列未命中时回退 {@code defaultActions}
 *   <li>无默认动作且未命中时返回未触发
 * </ol>
 *
 * <p>动作键约定与决策表一致：{@code severity} / {@code title} / {@code description} / {@code currentValue}。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class CrossDecisionTableRule implements Rule {

  /** 纳秒到毫秒的换算系数 */
  private static final long NANOS_PER_MILLI = 1_000_000L;

  private final CrossDecisionTableDefinition definition;
  private final ExpressionEngine evaluator;

  public CrossDecisionTableRule(
      CrossDecisionTableDefinition definition, ExpressionEngine evaluator) {
    this.definition = definition;
    this.evaluator = evaluator;
  }

  @Override
  public String getCode() {
    return definition.getMatrixCode();
  }

  @Override
  public String getName() {
    return definition.getMatrixName() != null
        ? definition.getMatrixName()
        : definition.getMatrixCode();
  }

  @Override
  public String getCategory() {
    return definition.getCategory();
  }

  @Override
  public int getPriority() {
    return definition.getPriority();
  }

  @Override
  public String getScope() {
    return definition.getScope();
  }

  @Override
  public RuleResult evaluate(RuleContext context) {
    long start = System.nanoTime();
    try {
      if (!definition.isEnabled()) {
        return RuleResult.builder()
            .ruleCode(getCode())
            .ruleName(getName())
            .category(getCategory())
            .triggered(false)
            .triggeredAt(LocalDateTime.now())
            .elapsedMs(elapsedMs(start))
            .build();
      }

      Map<String, Object> facts = context.getFacts();
      Object rowValue = definition.getRowDimension() == null ? null : facts.get(definition.getRowDimension());
      Object colValue =
          definition.getColumnDimension() == null ? null : facts.get(definition.getColumnDimension());

      int rowIndex = matchBucketIndex(definition.getRowBuckets(), rowValue, context);
      int colIndex = matchBucketIndex(definition.getColumnBuckets(), colValue, context);

      Map<String, Object> actions = null;
      if (rowIndex >= 0 && colIndex >= 0
          && definition.getCells() != null
          && definition.getCells().containsKey(CrossDecisionTableDefinition.cellKey(rowIndex, colIndex))) {
        actions = definition.getCells().get(CrossDecisionTableDefinition.cellKey(rowIndex, colIndex));
      }
      if (actions == null || actions.isEmpty()) {
        actions = definition.getDefaultActions();
      }
      if (actions == null || actions.isEmpty()) {
        return RuleResult.builder()
            .ruleCode(getCode())
            .ruleName(getName())
            .category(getCategory())
            .triggered(false)
            .triggeredAt(LocalDateTime.now())
            .elapsedMs(elapsedMs(start))
            .build();
      }

      Map<String, Object> enriched = new LinkedHashMap<>(actions);
      enriched.put("_rowIndex", rowIndex);
      enriched.put("_colIndex", colIndex);
      return buildResultFromActions(enriched, start);
    } catch (Exception e) {
      log.warn("[LiteRule-CrossTable] 交叉决策表 {} 评估异常: {}", getCode(), e.getMessage());
      return RuleResult.builder()
          .ruleCode(getCode())
          .triggered(false)
          .description("评估异常: " + e.getMessage())
          .triggeredAt(LocalDateTime.now())
          .elapsedMs(elapsedMs(start))
          .build();
    }
  }

  /** 按分桶顺序匹配，返回首个命中的桶索引；无命中返回 -1 */
  private int matchBucketIndex(
      List<CrossDecisionTableDefinition.Bucket> buckets, Object value, RuleContext context) {
    if (buckets == null) {
      return -1;
    }
    for (int i = 0; i < buckets.size(); i++) {
      CrossDecisionTableDefinition.Bucket bucket = buckets.get(i);
      if (bucket == null) {
        continue;
      }
      // 空条件 = 兜底桶（恒真）
      if (bucket.getCondition() == null
          || bucket.getCondition().isBlank()
          || "*".equals(bucket.getCondition().trim())) {
        return i;
      }
      if (ConditionMatcher.match(
          definition.getRowDimension(), bucket.getCondition(), value, context, evaluator)) {
        return i;
      }
    }
    return -1;
  }

  /** 根据 actions 构建规则结果（键约定与决策表一致） */
  private RuleResult buildResultFromActions(Map<String, Object> actions, long startNano) {
    String severityCode =
        actions.get("severity") == null ? "INFO" : String.valueOf(actions.get("severity"));
    RuleSeverity severity = RuleSeverity.fromCode(severityCode);
    if (severity == null) {
      severity = RuleSeverity.INFO;
    }

    String title = actions.get("title") == null ? getName() : String.valueOf(actions.get("title"));
    String description =
        actions.get("description") == null ? "" : String.valueOf(actions.get("description"));
    String currentValue =
        actions.get("currentValue") == null ? null : String.valueOf(actions.get("currentValue"));

    return RuleResult.builder()
        .ruleCode(getCode())
        .ruleName(getName())
        .category(getCategory())
        .triggered(true)
        .severity(severity)
        .title(title)
        .description(description)
        .currentValue(currentValue)
        .scope(definition.getScope())
        .triggeredAt(LocalDateTime.now())
        .drilldownAvailable(true)
        .elapsedMs(elapsedMs(startNano))
        .build();
  }

  private long elapsedMs(long startNano) {
    return (System.nanoTime() - startNano) / NANOS_PER_MILLI;
  }
}
