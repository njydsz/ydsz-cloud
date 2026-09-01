package com.njydsz.literule.server.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.dto.DecisionTableDefinitionDTO;
import com.njydsz.literule.domain.enums.HitPolicy;
import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.expression.ExpressionEngine;

/**
 * 决策表规则：基于 DMN 风格的表格进行多条件匹配
 *
 * <p>执行流程：
 *
 * <ol>
 *   <li>遍历所有 Row，对每行的 conditions 进行匹配（条件 AND 关系）
 *   <li>按 {@link HitPolicy} 收集命中结果
 *   <li>UNIQUE 多行命中时记录异常（不抛出，仅返回未触发 + 错误描述）
 *   <li>COLLECT/RULE_ORDER 返回所有命中行：主结果取首条，其余存入 {@code collectedResults}
 *   <li>FIRST/ANY/PRIORITY 仅返回首条/优先级最高的命中行
 * </ol>
 *
 * <p>条件表达式解析由 {@link ConditionMatcher} 实现（P0-3 抽取复用），支持字面值、比较、区间、枚举、LiteExpr 表达式。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
public class DecisionTableRule implements Rule {

  /** 纳秒到毫秒的换算系数 */
  private static final long NANOS_PER_MILLI = 1_000_000L;

  private final DecisionTableDefinitionDTO definition;
  private final ExpressionEngine evaluator;

  public DecisionTableRule(DecisionTableDefinitionDTO definition, ExpressionEngine evaluator) {
    this.definition = definition;
    this.evaluator = evaluator;
  }

  @Override
  public String getCode() {
    return definition.getTableCode();
  }

  @Override
  public String getName() {
    return definition.getTableName();
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
  public RuleResultVO evaluate(RuleContextVO context) {
    long start = System.nanoTime();
    try {
      List<DecisionTableDefinitionDTO.Row> matchedRows = new ArrayList<>();
      for (DecisionTableDefinitionDTO.Row row : definition.getRows()) {
        if (row.getConditions() == null || row.getConditions().isEmpty()) {
          matchedRows.add(row);
          continue;
        }
        boolean allMatch = true;
        for (Map.Entry<String, String> entry : row.getConditions().entrySet()) {
          String column = entry.getKey();
          String condExpr = entry.getValue();
          Object factValue = context.getFacts().get(column);
          if (!ConditionMatcher.match(column, condExpr, factValue, context, evaluator)) {
            allMatch = false;
            break;
          }
        }
        if (allMatch) {
          matchedRows.add(row);
        }
      }

      // 无命中：使用默认动作；若无默认动作则返回未触发
      if (matchedRows.isEmpty()) {
        if (definition.getDefaultActions() == null || definition.getDefaultActions().isEmpty()) {
          return RuleResultVO.builder()
              .ruleCode(getCode())
              .ruleName(getName())
              .category(getCategory())
              .triggered(false)
              .triggeredAt(LocalDateTime.now())
              .elapsedMs(elapsedMs(start))
              .build();
        }
        return buildResultFromActions(definition.getDefaultActions(), start);
      }

      HitPolicy policy =
          definition.getHitPolicy() == null ? HitPolicy.FIRST : definition.getHitPolicy();

      // UNIQUE 多命中 → 报错
      if (policy == HitPolicy.UNIQUE && matchedRows.size() > 1) {
        log.warn(
            "[LiteRule-DecisionTable] 决策表 {} UNIQUE 策略命中多行: count={}",
            getCode(),
            matchedRows.size());
        return RuleResultVO.builder()
            .ruleCode(getCode())
            .ruleName(getName())
            .category(getCategory())
            .triggered(false)
            .description("决策表 UNIQUE 策略命中多行: " + matchedRows.size())
            .triggeredAt(LocalDateTime.now())
            .elapsedMs(elapsedMs(start))
            .build();
      }

      // 按策略挑选
      DecisionTableDefinitionDTO.Row chosen;
      if (policy == HitPolicy.PRIORITY) {
        chosen =
            matchedRows.stream()
                .min(Comparator.comparingInt(DecisionTableDefinitionDTO.Row::getPriority))
                .orElse(matchedRows.get(0));
      } else if (policy == HitPolicy.COLLECT) {
        // COLLECT 策略：按优先级升序排序，主结果取首条，
        // 其余匹配行作为独立 RuleResultVO 收集到 collectedResults
        List<DecisionTableDefinitionDTO.Row> sorted = new ArrayList<>(matchedRows);
        sorted.sort(Comparator.comparingInt(DecisionTableDefinitionDTO.Row::getPriority));
        chosen = sorted.get(0);
        RuleResultVO mainResult = buildResultFromActions(chosen.getActions(), start);
        mainResult.setCollectedResults(buildCollectedResults(sorted, start));
        // 兼容下游：actions 中保留 _matchedCount 供旧消费者使用
        mainResult.setDescription(appendCollectInfo(mainResult.getDescription(), sorted.size()));
        return mainResult;
      } else if (policy == HitPolicy.RULE_ORDER) {
        // RULE_ORDER 策略：按行在表中的出现顺序，主结果取首条，
        // 其余匹配行作为独立 RuleResultVO 收集到 collectedResults
        chosen = matchedRows.get(0);
        RuleResultVO mainResult = buildResultFromActions(chosen.getActions(), start);
        mainResult.setCollectedResults(buildCollectedResults(matchedRows, start));
        mainResult.setDescription(
            appendCollectInfo(mainResult.getDescription(), matchedRows.size()));
        return mainResult;
      } else {
        // FIRST / ANY → 首条
        chosen = matchedRows.get(0);
      }

      Map<String, Object> actions = new LinkedHashMap<>(chosen.getActions());
      actions.put("_matchedCount", matchedRows.size());

      return buildResultFromActions(actions, start);
    } catch (Exception e) {
      log.warn("[LiteRule-DecisionTable] 决策表 {} 评估异常: {}", getCode(), e.getMessage());
      return RuleResultVO.builder()
          .ruleCode(getCode())
          .triggered(false)
          .description("评估异常: " + e.getMessage())
          .triggeredAt(LocalDateTime.now())
          .elapsedMs(elapsedMs(start))
          .build();
    }
  }

  /**
   * 根据 actions 构建规则结果
   *
   * <p>actions 中约定键：
   *
   * <ul>
   *   <li>{@code severity} — 严重度编码（INFO/YELLOW/RED），缺省 INFO
   *   <li>{@code title} — 标题
   *   <li>{@code description} — 详细描述
   *   <li>{@code currentValue} — 当前值（参考）
   * </ul>
   */
  private RuleResultVO buildResultFromActions(Map<String, Object> actions, long startNano) {
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

    return RuleResultVO.builder()
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

  /**
   * 构建 COLLECT/RULE_ORDER 策略的全部匹配行结果列表
   *
   * <p>每行独立构建一个 {@link RuleResultVO}，保留行优先级与动作信息， 主结果（列表首项）与外层返回的主结果内容一致。
   *
   * @param matchedRows 已按策略排序的匹配行
   * @param startNano 评估起始纳秒时间
   * @return 匹配行结果列表（至少 1 项）
   */
  private List<RuleResultVO> buildCollectedResults(
      List<DecisionTableDefinitionDTO.Row> matchedRows, long startNano) {
    List<RuleResultVO> results = new ArrayList<>(matchedRows.size());
    for (DecisionTableDefinitionDTO.Row row : matchedRows) {
      results.add(buildResultFromActions(row.getActions(), startNano));
    }
    return results;
  }

  /**
   * 在描述末尾追加 COLLECT/RULE_ORDER 命中计数信息
   *
   * @param description 原始描述
   * @param count 匹配行数
   * @return 拼接后的描述；原始描述为空时仅返回计数信息
   */
  private String appendCollectInfo(String description, int count) {
    String info = "[matchedCount=" + count + "]";
    return (description == null || description.isEmpty()) ? info : description + " " + info;
  }

  public DecisionTableDefinitionDTO getDefinition() {
    return definition;
  }
}
