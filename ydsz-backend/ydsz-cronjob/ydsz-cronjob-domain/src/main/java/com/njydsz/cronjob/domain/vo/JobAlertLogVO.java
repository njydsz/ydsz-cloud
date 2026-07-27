package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobAlertLog 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobAlertLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String alertCode;
    private String sourceType;
    private String ruleId;
    private String ruleName;
    private String jobId;
    private String jobKey;
    private String alertType;
    private String alertLevel;
    private String triggerValue;
    private Long threshold;
    private String channels;
    private String alertStatus;
    private String errorMessage;
    private String traceId;
    private String triggerLogId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}