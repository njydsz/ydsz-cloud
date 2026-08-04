package com.remisoft.workflow.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * FlowCc 视图对象。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class FlowCcVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String instanceId;
    private String taskId;
    private String nodeCode;
    private String nodeName;
    private String flowCode;
    private String flowName;
    private String businessKey;
    private String ccUserId;
    private String ccUserName;
    private String ccType;
    private String triggerUserId;
    private String triggerUserName;
    private String title;
    private String content;
    private String readStatus;
    private LocalDateTime readAt;
    private String providerTraceId;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}