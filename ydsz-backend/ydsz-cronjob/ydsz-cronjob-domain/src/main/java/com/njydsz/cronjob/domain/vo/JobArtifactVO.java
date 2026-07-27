package com.njydsz.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobArtifact 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobArtifactVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String jobId;
    private String logId;
    private String jobKey;
    private String artifactName;
    private String artifactType;
    private String storagePath;
    private Long sizeBytes;
    private String contentType;
    private String metadata;
    private LocalDateTime expireAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}