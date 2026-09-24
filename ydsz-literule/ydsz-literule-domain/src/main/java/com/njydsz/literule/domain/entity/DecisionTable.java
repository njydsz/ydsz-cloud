package com.njydsz.literule.domain.entity;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.handler.JsonTypeHandler;

/**
 * 决策表实体。
 *
 * <p>对应 {@code ydsz_rule_decision_table} 表，以行-列矩阵形式描述条件与动作的映射关系。
 * 每行代表一条规则，条件列（{@code conditionColumns}）定义输入事实的匹配条件，动作列（{@code actionColumns}）
 * 定义命中时输出的动作参数。命中策略（{@code hitPolicy}）支持 UNIQUE / FIRST / PRIORITY / COLLECT / ANY 五种模式，
 * 默认 FIRST（命中即停）。
 *
 * <p>决策行（{@code rows}）为 JSON 列表，每行包含条件值与动作值键值对；{@code defaultActions} 定义无行命中时的兜底输出。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
// YDIZ-WARN-001 允许保留：Lombok @SuperBuilder 与泛型继承产生 unchecked 警告
@SuppressWarnings("unchecked")
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ydsz_rule_decision_table", autoResultMap = true)
public class DecisionTable extends MpBaseEntity<String> {

  /** 决策表编码 */
  private String tableCode;

  /** 决策表名称 */
  private String tableName;

  /** 描述 */
  private String description;

  /** 类别 */
  private String category;

  /** 条件列定义 */
  @TableField(typeHandler = JsonTypeHandler.class)
  private List<Map<String, Object>> conditionColumns;

  /** 动作列定义 */
  @TableField(typeHandler = JsonTypeHandler.class)
  private List<Map<String, Object>> actionColumns;

  /** 决策行 */
  @TableField(typeHandler = JsonTypeHandler.class)
  private List<Map<String, Object>> rows;

  /** 默认动作 */
  @TableField(typeHandler = JsonTypeHandler.class)
  private Map<String, Object> defaultActions;

  /** 命中策略：UNIQUE/FIRST/PRIORITY/COLLECT/ANY，默认 FIRST */
  private String hitPolicy;

  /** 是否启用 */
  private Boolean isEnabled;

  /** 优先级 */
  private Integer priority;

  /** 版本 */
  private Integer version;
}
