package com.njydsz.literule.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleABPolicy 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleABPolicyVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleCode;
    private Boolean autoRollbackEnabled;
    private String rollbackAction;
    private BigDecimal errorRateThreshold;
    private Integer minSampleSize;
    private Integer checkWindowMinutes;
    private String notifyChannels;
    private String description;
    private LocalDateTime lastEvaluatedAt;
    private LocalDateTime lastRollbackAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}