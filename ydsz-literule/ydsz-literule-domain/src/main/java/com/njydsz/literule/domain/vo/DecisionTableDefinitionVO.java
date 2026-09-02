package com.njydsz.literule.domain.vo;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 决策表定义视图对象（VO）。
 *
 * <p>用于前端配置与展示决策表的结构化定义，包含表头信息、条件列/动作列定义、 行数据（条件→动作映射）及默认动作。决策表以行列方式组织规则，便于业务人员维护。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class DecisionTableDefinitionVO {

  /** 决策表编码（业务唯一标识） */
  private String tableCode;

  /** 决策表名称（展示用） */
  private String tableName;

  /** 决策表描述 */
  private String description;

  /** 分类编码 */
  private String category;

  /** 条件列定义列表（每列代表一个条件维度，如字段名/类型） */
  private List<Object> conditionColumns;

  /** 动作列定义列表（每列代表一个输出动作） */
  private List<Object> actionColumns;

  /** 决策表行数据（每行是条件组合到动作输出的映射） */
  private List<Object> rows;

  /** 默认动作（无行命中时执行，列名 → 值） */
  private Map<String, Object> defaultActions;

  /** 适用范围（限定规则可生效的场景） */
  private String scope;

  /** 节点名称（树形/视图展示用） */
  private String name;

  /** 节点标签（展示标签） */
  private String label;

  /** 类型（节点/表类型标识） */
  private String type;

  /** 条件键值对（条件列名 → 表达式） */
  private Map<String, String> conditions;

  /** 动作键值对（动作列名 → 值） */
  private Map<String, Object> actions;
}
