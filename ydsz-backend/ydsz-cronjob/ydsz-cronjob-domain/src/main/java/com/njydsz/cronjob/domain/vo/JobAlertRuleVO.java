package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobAlertRule 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobAlertRuleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleName;
    private String jobId;
    private String jobKey;
    private String alertType;
    private String alertLevel;
    private Long threshold;
    private Integer timeWindowMinutes;
    private String channels;
    private String receivers;
    private Integer cooldownMinutes;
    private Integer enabled;
    private String sourceType;
    private LocalDateTime lastAlertAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}