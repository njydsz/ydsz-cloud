package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseIdEntity;
import com.njydsz.common.jdbc.handler.JsonTypeHandler;

/**
 * LiteRule 规则版本历史。
 *
 * <p>对应 {@code ydsz_rule_version_history} 表，存储规则每次变更时的版本快照。
 * 每次发布或回滚规则定义时，将规则定义的完整 JSON（{@code definitionJson}）固化存库，
 * 配合 {@code changeDesc} 变更说明与 {@code operator} 操作人信息，支持规则内容的全量审计追溯。
 *
 * <p>版本号（{@code version}}）全局递增，与 {@code ruleCode} 联合唯一，保证每条规则的变更链路完整可查。
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
@TableName("ydsz_rule_version_history")
public class RuleVersionHistory extends MpBaseIdEntity<String> {

  /** 规则编码 */
  private String ruleCode;

  /** 版本号 */
  private Integer version;

  /** 该版本的规则定义 JSON 快照 */
  @TableField(typeHandler = JsonTypeHandler.class)
  private String definitionJson;

  /** 变更说明 */
  private String changeDesc;

  /** 操作人 */
  private String operator;
}
