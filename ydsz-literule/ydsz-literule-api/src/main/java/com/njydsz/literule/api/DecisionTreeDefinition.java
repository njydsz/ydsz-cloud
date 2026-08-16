package com.njydsz.literule.api;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 决策树规则定义（DTO）
 *
 * <p>由嵌套的 {@link DecisionNode} 构成树形条件判断结构：
 *
 * <ul>
 *   <li>内部节点：包含 LiteExpr 条件表达式，true 走 trueBranch，false 走 falseBranch
 *   <li>叶子节点：包含 severity / title / description 决策结果
 * </ul>
 *
 * <p>持久化于 {@code ydsz_rule_decision_tree}（见 V048，root_node 字段存储 JSON）， 由 {@code
 * DecisionTreeConfigProvider} SPI 加载， 通过 {@link
 * com.njydsz.literule.server.impl.DecisionTreeRule#from(DecisionTreeDefinition,
 * com.njydsz.literule.api.expression.ExpressionEngine)} 转换为可执行规则。
 *
 * <p>JSON 示例：
 *
 * <pre>
 * {
 *   "ruleCode": "RISK_LEVEL",
 *   "ruleName": "项目风险分级",
 *   "category": "RISK",
 *   "root": {
 *     "conditionExpression": "budgetUsedRatio > 0.9",
 *     "leaf": false,
 *     "trueBranch": {
 *       "leaf": true,
 *       "severity": "RED",
 *       "title": "严重超支",
 *       "description": "预算使用率超过 90%"
 *     },
 *     "falseBranch": {
 *       "conditionExpression": "budgetUsedRatio > 0.7",
 *       "leaf": false,
 *       "trueBranch": {"leaf": true, "severity": "YELLOW", "title": "中度超支", "description": "预算使用率超过 70%"},
 *       "falseBranch": {"leaf": true, "severity": "INFO", "title": "正常", "description": "预算使用正常"}
 *     }
 *   }
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionTreeDefinition implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码（唯一） */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 类别（如 RISK / GENERAL） */
  private String category;

  /** 描述 */
  private String description;

  /** 根节点 */
  private DecisionNode root;

  /** 是否启用 */
  @Builder.Default private boolean enabled = true;

  /** 优先级（数值越小越先执行） */
  @Builder.Default private int priority = Rule.DEFAULT_PRIORITY;

  /** 影响范围（用于场景过滤） */
  private String scope;

  /** 当前版本号 */
  @Builder.Default private int version = 1;

  /**
   * 决策树节点（内部节点 / 叶子节点）
   *
   * <p>当 {@link #leaf} 为 true 时表示叶子节点，使用 severity/title/description； 为 false 时表示条件节点，使用
   * conditionExpression/trueBranch/falseBranch。
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DecisionNode implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 条件表达式（仅条件节点使用，LiteExpr 返回 boolean） */
    private String conditionExpression;

    /** true 分支子节点 */
    private DecisionNode trueBranch;

    /** false 分支子节点 */
    private DecisionNode falseBranch;

    /** 严重度字符串（仅叶子节点使用，"RED"/"YELLOW"/"INFO"） */
    private String severity;

    /** 标题（仅叶子节点使用） */
    private String title;

    /** 描述（仅叶子节点使用） */
    private String description;

    /** 是否为叶子节点 */
    @Builder.Default private boolean leaf = false;
  }
}
