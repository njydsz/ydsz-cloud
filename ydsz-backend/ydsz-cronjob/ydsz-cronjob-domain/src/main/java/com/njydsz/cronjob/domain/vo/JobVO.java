package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * Job 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobName;
    private String jobGroup;
    private String jobKey;
    private String handler;
    private String cronExpression;
    private String scheduleType;
    private Long fixedRateMs;
    private Long fixedDelayMs;
    private String paramsJson;
    private String jobRemark;
    private LocalDateTime nextFireTime;
    private LocalDateTime lastFireTime;
    private Long fireCount;
    private Long successCount;
    private Long failCount;
    private Long lockTtlMs;
    private Long timeoutMs;
    private Long slowThresholdMs;
    private String misfirePolicy;
    private Integer shardTotal;
    private String jobType;
    private Integer maxRetries;
    private Long retryIntervalMs;
    private String retryBackoff;
    private String blockStrategy;
    private Integer consecutiveFailCount;
    private Integer maxConsecutiveFails;
    private Integer autoResumeAfterMinutes;
    private Integer priority;
    private Integer version;
    private String timezone;
    private String cluster;
    private Integer canaryRatio;
    private String canaryHandler;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}