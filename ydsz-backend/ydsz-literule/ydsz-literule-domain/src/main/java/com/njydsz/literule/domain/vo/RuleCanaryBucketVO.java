package com.njydsz.literule.domain.vo;

import java.time.LocalDate;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * RuleCanaryBucket 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class RuleCanaryBucketVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String ruleCode;
    private String bucketType;
    private Long bucketCount;
    private LocalDate statDate;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}