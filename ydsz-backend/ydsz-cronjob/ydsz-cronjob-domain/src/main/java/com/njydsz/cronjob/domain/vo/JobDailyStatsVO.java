package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;
import java.time.LocalDate;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobDailyStats 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDailyStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobId;
    private String jobKey;
    private LocalDate statsDate;
    private Long fireCount;
    private Long successCount;
    private Long failCount;
    private Long timeoutCount;
    private Long avgDurationMs;
    private Long maxDurationMs;
    private Long minDurationMs;
    private Long p95DurationMs;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}