package com.njydsz.literule.server.debug;

import lombok.Builder;
import lombok.Data;

/**
 * 断点定义（F1 断点调试器）
 *
 * <p>断点按规则编码（ruleCode）分组，支持三种维度：
 *
 * <ul>
 *   <li><b>规则级断点</b>（expression 为 null）：规则评估开始前挂起，用于"断在某条规则上"
 *   <li><b>表达式节点级断点</b>（expression 非空）：表达式求值到指定节点类型时挂起，
 *       支持 COMPARISON / LOGICAL / ARITHMETIC / VARIABLE / FUNCTION_CALL / TERNARY
 *   <li><b>条件断点</b>（condition 非空）：命中断点后还需条件表达式（LiteExpr）求值为 true 才挂起
 * </ul>
 *
 * <p>示例：
 *
 * <pre>{@code
 * Breakpoint.builder()
 *     .ruleCode("RISK_001")
 *     .nodeType("COMPARISON")      // 表达式节点级
 *     .condition("amount > 1000")  // 条件断点
 *     .build();
 * }</pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@Builder
public class Breakpoint {

  /** 断点 ID（全局唯一） */
  private String id;

  /** 规则编码（断点分组 key） */
  private String ruleCode;

  /**
   * 断点维度：规则级（null）或表达式节点级（非 null）。
   *
   * <p>表达式节点级断点的取值（与 {@code ExprTraceBuilder.TraceNode.type()} 对齐）：
   * COMPARISON / LOGICAL / ARITHMETIC / VARIABLE / FUNCTION_CALL / TERNARY
   */
  private String nodeType;

  /** 表达式文本（可选，精确匹配节点 exprText；null 表示匹配该节点类型的全部节点） */
  private String expression;

  /** 条件断点表达式（可选，LiteExpr；满足才挂起） */
  private String condition;

  /** 是否启用（默认 true） */
  @Builder.Default private boolean enabled = true;

  /** 命中次数统计 */
  @Builder.Default private long hitCount = 0;

  /** 命中次数阈值（可选，命中达到该次数后才挂起；0=不限制） */
  @Builder.Default private int hitLimit = 0;

  /** 创建人 */
  private String createdBy;

  /**
   * 是否为规则级断点
   *
   * @return true=规则级断点（评估开始前挂起）
   */
  public boolean isRuleLevel() {
    return expression == null && nodeType == null;
  }
}
