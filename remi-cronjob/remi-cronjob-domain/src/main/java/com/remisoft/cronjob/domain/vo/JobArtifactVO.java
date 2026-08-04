package com.remisoft.cronjob.domain.vo;

import java.time.LocalDateTime;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * JobArtifact 视图对象。
 *
 * <p>用于 Controller 层返回任务产物数据，对应实体 {@link com.remisoft.cronjob.domain.entity.job.JobArtifact}。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class JobArtifactVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    private String id;

    /** 任务 ID（关联 remi_job.id） */
    private String jobId;

    /** 执行日志 ID（关联 remi_job_log.id） */
    private String logId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 产物名称 */
    private String artifactName;

    /** 产物类型 */
    private String artifactType;

    /** 存储路径（文件存储服务中的实际位置） */
    private String storagePath;

    /** 产物大小（字节） */
    private Long sizeBytes;

    /** 内容类型（MIME，如 application/zip） */
    private String contentType;

    /** 元数据（JSON，扩展属性） */
    private String metadata;

    /** 过期时间（NULL=不过期） */
    private LocalDateTime expireAt;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
