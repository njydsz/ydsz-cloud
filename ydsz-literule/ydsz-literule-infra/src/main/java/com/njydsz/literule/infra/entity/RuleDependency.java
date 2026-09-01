package com.njydsz.literule.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 规则依赖关系（P1-8）
 *
 * <p>对应 ydsz_rule_dependency 表。一条记录表示 rule_code 依赖 depends_on_rule_code：
 *
 * <ul>
 *   <li>dependency_type = EXECUTE：评估前先执行被依赖规则
 *   <li>dependency_type = READ_RESULT：读取被依赖规则的结果
 *   <li>dependency_type = SOFT：仅配置参考，不强制
 * </ul>
 *
 * <p>cascade_on_disable=true 表示被依赖规则被禁用时，本规则也要级联禁用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_dependency")
public class RuleDependency extends MpBaseEntity<String> {

  /** 主规则编码（依赖方） */
  private String ruleCode;

  /** 被依赖的规则编码 */
  private String dependsOnRuleCode;

  /** 依赖类型：EXECUTE / READ_RESULT / SOFT */
  private String dependencyType;

  /** 被依赖规则被禁用时是否级联禁用本规则 */
  private Boolean cascadeOnDisable;

  /** 依赖说明 */
  private String description;
}
