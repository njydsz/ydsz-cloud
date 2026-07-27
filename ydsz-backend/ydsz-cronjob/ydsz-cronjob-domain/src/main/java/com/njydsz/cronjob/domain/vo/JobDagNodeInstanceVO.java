package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobDagNodeInstance 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDagNodeInstanceVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String dagInstanceId;
    private String dagId;
    private String jobId;
    private String jobKey;
    private String nodeStatus;
    private String logId;
    private Integer retryCount;
    private Integer maxRetries;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String resultJson;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}