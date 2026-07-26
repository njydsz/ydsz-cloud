package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * LiteRule 规则模板 DO
 *
 * <p>映射 ydsz_rule_template 表，存储规则模板市场中的预置模板。
 * 用户可从模板一键导入生成规则定义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_template")
public class RuleTemplateDO extends MpBaseEntity<String> {

    private String templateCode;
    private String templateName;
    private String category;
    private String description;
    private String conditionExpression;
    private String severityExpression;
    private String defaultSeverity;
    private String titleTemplate;
    private String descriptionTemplate;
    private Integer priority;
    private String scope;
    private String industry;
    private String tags;
}
