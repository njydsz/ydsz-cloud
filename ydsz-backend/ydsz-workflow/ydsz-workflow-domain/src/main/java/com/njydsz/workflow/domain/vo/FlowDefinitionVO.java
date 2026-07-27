package com.njydsz.workflow.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * FlowDefinition 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FlowDefinitionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String flowCode;
    private String flowName;
    private String category;
    private String flowVersion;
    private String modelValue;
    private String formCustom;
    private String formPath;
    private Integer activityStatus;
    private Integer isPublish;
    private String listenerType;
    private String listenerPath;
    private String ext;
    private String description;
    private String providerTraceId;
    private Integer canaryPercent;
    private String canaryStatus;
    private String canaryStrategy;
    private String canaryRolloutLog;
    private String lockedBy;
    private LocalDateTime lockedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}