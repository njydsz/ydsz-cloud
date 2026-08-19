package com.njydsz.literule.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;

/**
 * LiteRule 规则版本历史 DO
 *
 * <p>映射 ydsz_rule_version_history 表，存储规则变更的版本快照。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_version_history")
public class RuleVersionHistory extends MpBaseIdEntity<String> {

  /** 规则编码 */
  private String ruleCode;

  /** 版本号 */
  private Integer version;

  /** 该版本的规则定义 JSON 快照 */
  private String definitionJson;

  /** 变更说明 */
  private String changeDesc;

  /** 操作人 */
  private String operator;
}
