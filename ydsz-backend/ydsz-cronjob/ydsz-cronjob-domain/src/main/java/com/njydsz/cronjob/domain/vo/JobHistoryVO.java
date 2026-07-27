package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobHistory 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobHistoryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobId;
    private Integer version;
    private String snapshot;
    private String changeType;
    private String beforeSnapshot;
    private String changeRemark;
    private String jobName;
    private String jobKey;
    private String handler;
    private String cronExpression;
    private String paramsJson;
    private String remark;
    private String changedBy;
    private LocalDateTime changedAt;
    private Integer historyDeleted;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}