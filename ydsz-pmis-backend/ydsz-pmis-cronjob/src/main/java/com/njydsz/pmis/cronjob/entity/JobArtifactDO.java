package com.njydsz.pmis.cronjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 执行产物记录（P2-8 执行产物管理）。
 *
 * <p>记录任务执行产生的文件/数据产物，支持产物查询、下载和清理。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@TableName("pmis_job_artifact")
public class JobArtifactDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 逻辑删除 */
    private Integer deleted;
}
