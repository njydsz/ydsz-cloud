package com.njydsz.literule.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleDefinition 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleDefinitionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
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
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private Double canaryRatio;
    private String canaryConditions;
    private String canaryConditionExpression;
    private String canarySeverityExpression;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}