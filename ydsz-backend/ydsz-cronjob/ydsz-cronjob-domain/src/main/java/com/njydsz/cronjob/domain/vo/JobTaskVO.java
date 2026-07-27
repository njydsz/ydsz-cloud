package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobTask 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobTaskVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobId;
    private String logId;
    private String jobKey;
    private String taskName;
    private String taskParams;
    private String taskType;
    private String taskStatus;
    private String result;
    private String errorMessage;
    private String execNodeId;
    private Integer retryCount;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}