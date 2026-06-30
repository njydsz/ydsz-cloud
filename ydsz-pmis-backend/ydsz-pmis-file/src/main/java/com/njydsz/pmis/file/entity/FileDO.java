package com.njydsz.pmis.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 文件元信息实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_file")
public class FileDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统生成的文件名 */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 对象 key / 存储路径 */
    private String filePath;

    /** 存储桶 */
    private String bucket;

    /** MIME 类型 */
    private String contentType;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件 SHA-256 */
    private String fileHash;

    /** 业务类型 */
    private String bizType;

    /** 业务单据 ID */
    private String bizId;

    /** 存储类型: MINIO/LOCAL/OSS */
    private String storageType;

    /** 访问 URL */
    private String accessUrl;

    /** URL 过期时间 */
    private LocalDateTime urlExpireAt;

    /** 上传人 ID */
    private Long uploaderId;

    /** 上传人姓名 */
    private String uploaderName;

    /** 租户 ID */
    private Long tenantId;

    /** 描述 */
    private String description;
}
