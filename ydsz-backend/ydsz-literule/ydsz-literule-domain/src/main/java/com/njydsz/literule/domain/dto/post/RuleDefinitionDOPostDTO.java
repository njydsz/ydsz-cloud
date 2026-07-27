package com.njydsz.literule.domain.dto.post;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleDefinitionDO 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleDefinitionDOPostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String ruleCode;
    private String ruleName;
    private String category;
    private String categoryPath;
    private String owner;
    private String description;
    private String conditionExpression;
    private String severityExpression;
    private String defaultSeverity;
    private String titleTemplate;
    private String descriptionTemplate;
    private Integer priority;
    private Boolean enabled;
    private String scope;
    private String mutexGroup;
    private Boolean drilldownAvailable;
    private Integer version;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private Double canaryRatio;
    private String canaryConditions;
    private String canaryConditionExpression;
    private String canarySeverityExpression;
}