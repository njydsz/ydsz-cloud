package com.njydsz.literule.server.impl;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.dto.DecisionTreeDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleSeverity;
import com.njydsz.literule.domain.expression.ExpressionEngine;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 决策树规则：基于树形条件判断结构逐层求值，到达叶子节点返回结果
 *
 * <p>典型应用场景：复杂的多层条件判断，如项目风险分级、审批路由决策。
 *
 * <p>决策树由内部节点（条件判断）和叶子节点（决策结果）构成：
 *
 * <ul>
 *   <li>内部节点：包含 LiteExpr 条件表达式，true 走 trueBranch，false 走 falseBranch
 *   <li>叶子节点：包含严重度、标题、描述等决策结果
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>
 * DecisionTreeRule rule = DecisionTreeRule.builder()
 *     .code("RISK_LEVEL")
 *     .name("项目风险分级")
 *     .category("RISK")
 *     .evaluator(evaluator)
 *     .root(DecisionNode.condition("budgetUsedRatio > 0.9",
 *         DecisionNode.leaf(RuleSeverity.RED, "严重超支", "预算使用率超过90%"),
 *         DecisionNode.condition("budgetUsedRatio > 0.7",
 *             DecisionNode.leaf(RuleSeverity.YELLOW, "中度超支", "预算使用率超过70%"),
 *             DecisionNode.leaf(RuleSeverity.INFO, "正常", "预算使用正常")
 *         )
 *     ))
 *     .build();
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public class DecisionTreeRule implements Rule {

    /** 纳秒到毫秒的换算系数（1 毫秒 = 100 万纳秒） */
  private static final long NANOS_PER_MILLI = 1_000_000L;

  private final String code;
  private final String name;
  private final String category;
  private final int priority;
  private final String scope;
  private final DecisionNode root;
  private final ExpressionEngine evaluator;

  public DecisionTreeRule(
      String code,
      String name,
      String category,
      int priority,
      String scope,
      DecisionNode root,
      ExpressionEngine evaluator) {
    this.code = code;
    this.name = name;
    this.category = category;
    this.priority = priority;
    this.scope = scope;
    this.root = root;
    this.evaluator = evaluator;
  }

  /**
   * 从 DecisionTreeDefinitionDTO 构造决策树规则
   *
   * @param def 决策树定义
   * @param evaluator 表达式求值器
   * @return DecisionTreeRule 实例
   * @since 26.09.01
   */
  public static DecisionTreeRule from(DecisionTreeDefinitionDTO def, ExpressionEngine evaluator) {
    return new DecisionTreeRule(
        def.getRuleCode(),
        def.getRuleName(),
        def.getCategory(),
        def.getPriority(),
        def.getScope(),
        convertNode(def.getRoot()),
        evaluator);
  }

  /**
   * 递归转换 Definition 节点为内部 DecisionNode
   *
   * @param src 源节点
   * @return 内部节点
   */
  private static DecisionNode convertNode(DecisionTreeDefinitionDTO.DecisionNode src) {
    if (src == null) {
      return null;
    }
    return DecisionNode.builder()
        .conditionExpression(src.getConditionExpression())
        .severity(parseSeverity(src.getSeverity()))
        .title(src.getTitle())
        .description(src.getDescription())
        .leaf(src.isLeaf())
        .trueBranch(convertNode(src.getTrueBranch()))
        .falseBranch(convertNode(src.getFalseBranch()))
        .build();
  }

  /** 解析严重度字符串（容错处理） */
  private static RuleSeverity parseSeverity(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    return RuleSeverity.fromCode(code);
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getCategory() {
    return category;
  }

  @Override
  public int getPriority() {
    return priority > 0 ? priority : DEFAULT_PRIORITY;
  }

  @Override
  public String getScope() {
    return scope;
  }

  @Override
  public RuleResultVO evaluate(RuleContextVO context) {
    long start = System.nanoTime();
    try {
      DecisionResult result = traverse(root, context, new StringBuilder());
      if (result == null) {
        return RuleResultVO.builder()
            .ruleCode(code)
            .triggered(false)
            .triggeredAt(LocalDateTime.now())
            .elapsedMs((System.nanoTime() - start) / NANOS_PER_MILLI)
            .build();
      }
      return RuleResultVO.builder()
          .ruleCode(code)
          .ruleName(name)
          .category(category)
          .triggered(true)
          .severity(result.severity)
          .title(result.title)
          .description(result.description)
          .triggeredAt(LocalDateTime.now())
          .elapsedMs((System.nanoTime() - start) / NANOS_PER_MILLI)
          .build();
    } catch (Exception e) {
      log.warn("[LiteRule-DecisionTree] 决策树 {} 评估异常: {}", code, e.getMessage());
      return RuleResultVO.builder()
          .ruleCode(code)
          .triggered(false)
          .triggeredAt(LocalDateTime.now())
          .elapsedMs((System.nanoTime() - start) / NANOS_PER_MILLI)
          .build();
    }
  }

  /** 递归遍历决策树 */
  private DecisionResult traverse(DecisionNode node, RuleContextVO context, StringBuilder path) {
    if (node == null) {
      return null;
    }

    if (node.isLeaf()) {
      return new DecisionResult(node.severity, node.title, node.description);
    }

    // 条件节点
    try {
      boolean matched = evaluator.evalBoolean(node.conditionExpression, context);
      path.append(node.conditionExpression).append("=").append(matched).append(" → ");
      return traverse(matched ? node.trueBranch : node.falseBranch, context, path);
    } catch (Exception e) {
      log.warn(
          "[LiteRule-DecisionTree] 条件求值异常: expr='{}', error={}",
          node.conditionExpression,
          e.getMessage());
      // 条件求值失败走 false 分支
      return traverse(node.falseBranch, context, path);
    }
  }

  /** 决策结果（内部传输对象） */
  private record DecisionResult(RuleSeverity severity, String title, String description) {}

  /** 决策树节点 */
  @Data
  @Builder
  public static class DecisionNode {
    /** 条件表达式（仅条件节点使用） */
    private String conditionExpression;

    /** true 分支子节点 */
    private DecisionNode trueBranch;

    /** false 分支子节点 */
    private DecisionNode falseBranch;

    /** 严重度（仅叶子节点使用） */
    private RuleSeverity severity;

    /** 标题（仅叶子节点使用） */
    private String title;

    /** 描述（仅叶子节点使用） */
    private String description;

    /** 是否为叶子节点 */
    private boolean leaf;

    /** 创建条件节点
     * @param conditionExpression LiteExpr 条件表达式
     * @param trueBranch 条件为 true 时的子节点
     * @param falseBranch 条件为 false 时的子节点
     * @return 条件决策节点
     */
    public static DecisionNode condition(
        String conditionExpression, DecisionNode trueBranch, DecisionNode falseBranch) {
      return DecisionNode.builder()
          .conditionExpression(conditionExpression)
          .trueBranch(trueBranch)
          .falseBranch(falseBranch)
          .leaf(false)
          .build();
    }

    /** 创建叶子节点
     * @param severity 决策严重度
     * @param title 决策标题
     * @param description 决策描述
     * @return 叶子决策节点
     */
    public static DecisionNode leaf(RuleSeverity severity, String title, String description) {
      return DecisionNode.builder()
          .severity(severity)
          .title(title)
          .description(description)
          .leaf(true)
          .build();
    }
  }
}
