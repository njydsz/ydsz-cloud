package com.njydsz.literule.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

/**
 * RuleABPolicy 修改请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleABPolicyPutDTO implements Serializable {

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
}