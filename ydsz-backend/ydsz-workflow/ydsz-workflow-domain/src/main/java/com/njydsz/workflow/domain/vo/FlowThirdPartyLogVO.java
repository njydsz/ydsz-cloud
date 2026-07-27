package com.njydsz.workflow.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * FlowThirdPartyLog 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowThirdPartyLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String platform;
    private String eventType;
    private String processInstanceId;
    private String businessType;
    private String businessId;
    private String callbackData;
    private String handleStatus;
    private String errorMsg;
    private String syncBackStatus;
    private String syncBackMsg;
    private Integer retryCount;
    private LocalDateTime lastRetriedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}