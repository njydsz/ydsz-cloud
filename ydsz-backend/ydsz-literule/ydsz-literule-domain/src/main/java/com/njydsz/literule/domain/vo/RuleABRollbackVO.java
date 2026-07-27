package com.njydsz.literule.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleABRollback 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleABRollbackVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleCode;
    private String triggerReason;
    private BigDecimal errorRate;
    private Long sampleSize;
    private Boolean fromCanary;
    private String operator;
    private String notifyStatus;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}