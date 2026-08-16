package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * LiteRule 规则模板 DO
 *
 * <p>映射 ydsz_rule_template 表，存储规则模板市场中的预置模板。 用户可从模板一键导入生成规则定义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_template")
public class RuleTemplate extends MpBaseEntity<String> {

  /** 模板编码，业务唯一 */
  private String templateCode;

  /** 模板名称 */
  private String templateName;

  /** 分类编码 */
  private String category;

  /** 模板描述 */
  private String description;

  /** 预置条件表达式 */
  private String conditionExpression;

  /** 预置严重度表达式 */
  private String severityExpression;

  /** 默认严重级别 */
  private String defaultSeverity;

  /** 告警标题模板 */
  private String titleTemplate;

  /** 告警描述模板 */
  private String descriptionTemplate;

  /** 优先级，数值越小优先级越高 */
  private Integer priority;

  /** 适用范围 */
  private String scope;

  /** 所属行业 */
  private String industry;

  /** 标签，逗号分隔 */
  private String tags;
}
