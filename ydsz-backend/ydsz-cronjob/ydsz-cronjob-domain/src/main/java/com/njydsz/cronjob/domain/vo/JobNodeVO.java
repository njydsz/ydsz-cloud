package com.njydsz.cronjob.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobNode 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobNodeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String nodeId;
    private String appName;
    private String host;
    private Integer port;
    private LocalDateTime lastHeartbeat;
    private String nodeStatus;
    private BigDecimal cpuUsage;
    private BigDecimal memUsagePct;
    private Integer runningCount;
    private String tags;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}