package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;
import com.njydsz.common.jdbc.handler.JsonTypeHandler;

/**
 * 规则决策树实体。
 *
 * <p>对应 {@code ydsz_rule_decision_tree} 表，以嵌套 JSON 结构（{@code rootNode}）描述树形决策过程。
 * 节点类型分为条件节点（CONDITION，按表达式结果走左/右子树）、动作节点（ACTION，命中后执行预设动作）
 * 和默认节点（DEFAULT，兜底分支）。
 *
 * <p>算法复杂度：树深度为 D、叶节点数为 N 时，单次评估时间复杂度为 O(D)，即沿一条路径逐层判断即可得出结论。
 * 评估流程：从根节点出发，根据 facts 计算 CONDITION 节点的布尔表达式，满足则进入"是"子节点，否则进入"否"子节点，
 * 直到到达 ACTION 叶节点执行对应动作。
 *
 * @author ydsz
 * @since 26.09.24
 */
// YDIZ-WARN-001 允许保留：Lombok @Data 与 JPA 继承共用，父类字段泛型擦除
@SuppressWarnings("unchecked")
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
  @TableField(typeHandler = JsonTypeHandler.class)
  private String rootNode;

  /** 优先级（数字越小越优先） */
  private Integer priority;

  /** 是否启用 */
  private Boolean isEnabled;

  /** 适用范围 */
  private String scope;

  /** 版本号 */
  private Integer version;

  /** 供应商侧追踪 ID */
  private String providerTraceId;
}
