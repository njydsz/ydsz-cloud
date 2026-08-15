package com.njydsz.workflow.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * FlowHisInstance 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowHisInstanceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String flowCode;
    private String flowName;
    private String definitionId;
    private String flowVersion;
    private String businessType;
    private String businessId;
    private String businessNo;
    private String title;
    private String initiatorId;
    private String initiatorName;
    private String currentNodeCode;
    private String currentNodeName;
    private String variable;
    private String flowStatus;
    private Integer activityStatus;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long durationMs;
    private LocalDateTime archivedAt;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
