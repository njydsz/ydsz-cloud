package com.njydsz.cronjob.domain.vo;

import java.math.BigDecimal;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobSla 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobSlaVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobId;
    private String jobKey;
    private Long maxDurationMs;
    private BigDecimal maxFailRate;
    private BigDecimal minSuccessRate;
    private String alertLevel;
    private Integer enabled;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}