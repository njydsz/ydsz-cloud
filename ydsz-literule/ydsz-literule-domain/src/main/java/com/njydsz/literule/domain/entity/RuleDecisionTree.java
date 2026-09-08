package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 规则决策树实体
 *
 * <p>决策树规则：root_node 字段为嵌套 JSON 结构，描述树形决策过程。 节点类型：CONDITION（条件）/ ACTION（动作）/ DEFAULT（默认分支）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SuppressWarnings("unchecked") // @SuperBuilder 与泛型继承（MpBaseEntity<String>）产生的 unchecked 警告，同 MpBaseIdEntity 无法在源码层面修复
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ydsz_rule_decision_tree", autoResultMap = true)
public class RuleDecisionTree extends MpBaseEntity<String> {

  /** 规则编码 */
  private String ruleCode;

  /** 规则名称 */
  private String ruleName;

  /** 规则分类 */
  private String category;

  /** 规则描述 */
  private String description;

  /** 根节点 JSON（嵌套结构） */
  private String rootNode;

  /** 优先级（数字越小越优先） */
  private Integer priority;

  /** 是否启用 */
  @TableField("enabled")
  private Boolean isEnabled;

  /** 适用范围 */
  private String scope;

  /** 版本号 */
  private Integer version;

  /** 供应商侧追踪 ID */
  private String providerTraceId;
}
