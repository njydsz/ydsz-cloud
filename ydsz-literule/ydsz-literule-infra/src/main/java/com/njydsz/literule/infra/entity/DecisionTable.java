package com.njydsz.literule.infra.entity;

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
 * 决策表实体
 *
 * @author ydsz
 * @since 1.0.0
 */
@Data
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
  private Boolean enabled;

  /** 优先级 */
  private Integer priority;

  /** 版本 */
  private Integer version;
}
