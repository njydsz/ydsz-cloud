package com.njydsz.cronjob.domain.entity.job;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 执行产物记录（P2-8 执行产物管理）。
 *
 * <p>记录任务执行产生的文件/数据产物，支持产物查询、下载和清理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_artifact")
public class JobArtifactDO extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String jobId;

    /** 执行日志 ID */
    private String logId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 产物名称 */
    private String artifactName;

    /** 产物类型: FILE / REPORT / DATA / LOG */
    private String artifactType;

    /** 存储路径（文件系统路径或对象存储 URL） */
    private String storagePath;

    /** 产物大小（字节） */
    private Long sizeBytes;

    /** 内容类型（MIME type） */
    private String contentType;

    /** 产物元数据 JSON */
    private String metadata;

    /** 过期时间（null=不过期） */
    private LocalDateTime expireAt;
}
