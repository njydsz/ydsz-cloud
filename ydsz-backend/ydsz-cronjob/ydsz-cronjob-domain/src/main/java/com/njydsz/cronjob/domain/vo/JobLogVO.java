package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobLog 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobId;
    private String jobKey;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private String errorMessage;
    private String paramsJson;
    private String resultJson;
    private String traceId;
    private String triggerType;
    private String lockHolder;
    private String execNodeId;
    private Long execThreadId;
    private Integer shardIndex;
    private Integer shardTotal;
    private Integer isSlow;
    private Long slowThresholdMs;
    private LocalDateTime queueTime;
    private LocalDateTime dispatchTime;
    private LocalDateTime handlerInitTime;
    private LocalDateTime handlerEndTime;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}