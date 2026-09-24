package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * LiteRule 规则模板。
 *
 * <p>对应 {@code ydsz_rule_template} 表，存储规则模板市场中的预置模板。
 * 模板预置了条件表达式（{@code conditionExpression}）、严重度表达式（{@code severityExpression}）、
 * 告警标题/描述模板（{@code titleTemplate} / {@code descriptionTemplate}）等字段，用户可基于模板一键导入生成完整的规则定义，
 * 降低规则配置门槛。
 *
 * <p>支持按行业（{@code industry}）、分类（{@code category}）和适用范围（{@code scope}）检索。
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
