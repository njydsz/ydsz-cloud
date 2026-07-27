package com.njydsz.literule.domain.vo;

import java.math.BigDecimal;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleScorecard 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleScorecardVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleCode;
    private String ruleName;
    private String category;
    private String description;
    private BigDecimal baseScore;
    private BigDecimal redThreshold;
    private BigDecimal yellowThreshold;
    private String factors;
    private Integer priority;
    private Boolean enabled;
    private String scope;
    private Integer version;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}