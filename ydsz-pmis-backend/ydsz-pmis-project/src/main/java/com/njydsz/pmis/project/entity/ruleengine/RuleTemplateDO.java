package com.njydsz.pmis.project.entity.ruleengine;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LiteRule 规则模板 DO
 *
 * <p>映射 pmis_rule_template 表，存储规则模板市场中的预置模板。
 * 用户可从模板一键导入生成规则定义。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@TableName("pmis_rule_template")
public class RuleTemplateDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

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
    private String createdBy;
    private LocalDateTime createdAt;
}
