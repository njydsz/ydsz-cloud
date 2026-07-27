package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobDagInstance 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDagInstanceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String dagId;
    private String dagKey;
    private String instanceStatus;
    private String triggerType;
    private String triggerBy;
    private String triggerTraceId;
    private String contextJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String errorMessage;
    private Integer totalNodes;
    private Integer successNodes;
    private Integer failedNodes;
    private Integer skippedNodes;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}