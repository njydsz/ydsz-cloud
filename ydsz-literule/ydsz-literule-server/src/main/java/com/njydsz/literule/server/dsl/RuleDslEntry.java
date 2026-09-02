package com.njydsz.literule.server.dsl;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DSL 规则定义条目
 *
 * <p>支持 6 种规则类型（type 字段）：
 *
 * <ul>
 *   <li>{@code expression}（默认）- 表达式规则，配合 condition / severity / title / description
 *   <li>{@code scorecard} - 评分卡规则，配合 base_score / factors / grades / direction 等
 *   <li>{@code decision_table} - 决策表规则，配合 condition_columns / action_columns / rows
 *   <li>{@code decision_tree} - 决策树规则，配合 tree_nodes
 *   <li>{@code script} - 脚本规则，配合 script_language / script_body
 *   <li>{@code static_rule} - 静态规则（无条件，始终触发）
 * </ul>
 *
 * <p>字段命名采用 snake_case（YAML 惯例），解析器会自动映射到 Definition 的 camelCase 字段。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDslEntry implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 规则编码（唯一） */
  private String code;

  /** 规则名称 */
  private String name;

  /**
   * 规则类型
   *
   * <p>可选值：expression / scorecard / decision_table / decision_tree / script / static_rule 默认
   * expression。
   */
  @Builder.Default private String type = "expression";

  /** 规则类别（如 EVM / COST / RISK） */
  private String category;

  /** 分类路径（多级用 / 分隔） */
  private String categoryPath;

  /** 责任人 */
  private String owner;

  /** 规则描述 */
  private String description;

  /** 优先级（数值越小越先执行，默认 100） */
  @Builder.Default private int priority = 100;

  /** 影响范围（用于场景过滤） */
  private String scope;

  /** 互斥组名称 */
  private String mutexGroup;

  /** 是否启用（默认 true） */
  @Builder.Default private boolean enabled = true;

  /** 当前版本号（默认 1） */
  @Builder.Default private int version = 1;

  // ============ expression 类型专用 ============

  /** 条件表达式（LiteExpr 语法，返回 boolean） */
  private String condition;

  /** 严重度表达式（可选，动态决定严重度） */
  private String severityExpression;

  /** 默认严重度（RED / YELLOW / INFO，当 severityExpression 为空时使用） */
  private String severity;

  /** 标题模板（支持 ${var} 占位符） */
  private String title;

  /** 描述模板（支持 ${var} 占位符） */
  private String descriptionTemplate;

  // ============ scorecard 类型专用 ============

  /** 基础分（默认 100） */
  private Double baseScore;

  /** 评分方向：DESCENDING / ASCENDING */
  private String direction;

  /** 最低分（钳制下界） */
  private Double minScore;

  /** 最高分（钳制上界） */
  private Double maxScore;

  /** 红色阈值 */
  private Double redThreshold;

  /** 黄色阈值 */
  private Double yellowThreshold;

  /** 评分因子列表 */
  private List<FactorDsl> factors;

  /** 自定义评级映射 */
  private List<GradeDsl> grades;

  // ============ decision_table 类型专用 ============

  /** 命中策略：FIRST / UNIQUE / PRIORITY / ANY / COLLECT / RULE_ORDER */
  private String hitPolicy;

  /** 条件列定义 */
  private List<Map<String, Object>> conditionColumns;

  /** 动作列定义 */
  private List<Map<String, Object>> actionColumns;

  /** 决策行 */
  private List<Map<String, Object>> rows;

  /** 默认动作 */
  private Map<String, Object> defaultActions;

  // ============ cross_decision_table 类型专用（P0-3） ============

  /** 行维度字段名（从 facts 中取值） */
  private String rowDimension;

  /** 列维度字段名（从 facts 中取值） */
  private String columnDimension;

  /** 行分桶列表（{@code label}/{@code condition}） */
  private List<Map<String, Object>> rowBuckets;

  /** 列分桶列表（{@code label}/{@code condition}） */
  private List<Map<String, Object>> columnBuckets;

  /** 交叉单元格动作映射（key 形如 "0_1"，value 为动作 Map） */
  private Map<String, Map<String, Object>> cells;

  // ============ script 类型专用 ============

  /** 脚本语言：groovy / javascript / python */
  private String scriptLanguage;

  /** 脚本内容 */
  private String scriptBody;

  // ============ 灰度配置（可选） ============

  /** 灰度比例（0.0~1.0，0 表示不启用灰度） */
  private Double canaryRatio;

  /** 灰度条件表达式列表（AND 关系） */
  private List<String> canaryConditions;

  /** 灰度候选版本条件表达式 */
  private String canaryConditionExpression;

  /** 灰度候选版本严重度表达式 */
  private String canarySeverityExpression;

  // ============ 生命周期 ============

  /** 生效时间 */
  private LocalDateTime effectiveFrom;

  /** 失效时间 */
  private LocalDateTime effectiveTo;

  /** 评分因子 DSL */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class FactorDsl implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 条件表达式（命中条件） */
    private String when;

    /** 固定得分（正数加分，负数扣分） */
    private Double score;

    /** 动态分值表达式（与 score 二选一，优先使用） */
    private String scoreExpr;

    /** 权重（默认 1.0） */
    private Double weight;

    /** 因子描述 */
    private String desc;
  }

  /** 评级映射 DSL */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class GradeDsl implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 评级名称（如 A / B / C / D） */
    private String label;

    /** 区间范围 [minScore, maxScore) */
    private List<Double> range;

    /** 对应严重度（RED / YELLOW / INFO） */
    private String severity;
  }
}
