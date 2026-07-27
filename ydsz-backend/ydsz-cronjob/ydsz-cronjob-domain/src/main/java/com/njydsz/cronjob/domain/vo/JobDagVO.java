package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobDag 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobDagVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String dagKey;
    private String dagName;
    private String dagDefinition;
    private String dagStatus;
    private String triggerType;
    private String cronExpression;
    private Integer maxConcurrentInstances;
    private String failStrategy;
    private String description;
    private Long timeoutMs;
    private LocalDateTime nextFireTime;
    private LocalDateTime lastFireTime;
    private Long fireCount;
    private Long successCount;
    private Long failCount;
    private Integer version;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}