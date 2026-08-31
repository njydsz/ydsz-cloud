package com.njydsz.literule.domain.api;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 决策表定义（DMN 风格）
 *
 * <p>由若干条件列、动作列与决策行组成，配合 {@link HitPolicy} 决定如何挑选匹配行。 持久化于 {@code ydsz_rule_decision_table}（见
 * V044/V045）。
 *
 * <p>结构示例：
 *
 * <pre>
 * {
 *   "tableCode": "DT_PROJECT_RISK",
 *   "tableName": "项目风险等级决策表",
 *   "hitPolicy": "FIRST",
 *   "conditionColumns": [
 *       {"name":"evmRedCount","label":"EVM 红灯数","type":"number"},
 *       {"name":"metricValue","label":"指标值","type":"number"}
 *   ],
 *   "actionColumns": [
 *       {"name":"severity","label":"严重度","type":"string"},
 *       {"name":"title","label":"标题","type":"string"}
 *   ],
 *   "rows": [
 *       {"conditions":{"evmRedCount":">=3"},"actions":{"severity":"RED","title":"EVM 严重偏离"}},
 *       {"conditions":{"metricValue":"<0.05"},"actions":{"severity":"YELLOW","title":"指标值过低"}}
 *   ],
 *   "defaultActions": {"severity":"INFO","title":"正常"}
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
public class DecisionTableDefinition implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 表编码（唯一） */
  private String tableCode;

  /** 表名称 */
  private String tableName;

  /** 描述 */
  private String description;

  /** 类别（如 EVM / COST / RISK） */
  private String category;

  /** 命中策略，默认 FIRST */
  @Builder.Default private HitPolicy hitPolicy = HitPolicy.FIRST;

  /** 条件列定义 */
  private List<Column> conditionColumns;

  /** 动作列定义 */
  private List<Column> actionColumns;

  /** 决策行 */
  private List<Row> rows;

  /** 默认动作（未匹配时使用） */
  private Map<String, Object> defaultActions;

  /** 是否启用 */
  @Builder.Default private boolean enabled = true;

  /** 优先级（数值越小越先执行） */
  @Builder.Default private int priority = 100;

  /** 影响范围（用于场景过滤） */
  private String scope;

  /** 当前版本号 */
  @Builder.Default private int version = 1;

  /** 列定义 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 列字段名（事实键名） */
    private String name;

    /** 列显示名 */
    private String label;

    /** 列类型：number/string/boolean */
    private String type;
  }

  /** 决策行 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Row implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 条件映射：key=列名，value=条件表达式
     *
     * <p>支持以下形式：
     *
     * <ul>
     *   <li>字面值：{@code "3"} / {@code "RED"} / {@code "true"}（值相等即匹配）
     *   <li>比较表达式：{@code ">=3"} / {@code "<0.05"} / {@code "!=null"}
     *   <li>区间：{@code "[0.05,0.15)"}（左闭右开）
     *   <li>枚举：{@code "RED|YELLOW"}（OR）
     *   <li>LiteExpr 表达式：{@code "expr:>amount*0.1"}（以 {@code expr:} 前缀）
     * </ul>
     */
    private Map<String, String> conditions;

    /** 动作映射：key=列名，value=输出值 */
    private Map<String, Object> actions;

    /** 行优先级（用于 PRIORITY 策略，数值越小越高） */
    @Builder.Default private int priority = 100;
  }
}
