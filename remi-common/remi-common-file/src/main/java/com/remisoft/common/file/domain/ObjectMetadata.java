package com.remisoft.common.file.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对象元信息
 * <p>封装文件对象在存储层的元数据，包括大小、类型、ETag、最后修改时间等。
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMetadata {

    /**
     * 对象名称（存储键）
     */
    private String objectName;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 内容类型（MIME Type）
     */
    private String contentType;

    /**
     * ETag（云存储分配的 对象唯一标识）
     */
    private String eTag;

    /**
     * 最后修改时间
     */
    private LocalDateTime lastModified;

    /**
     * 是否为目录（目录以 / 结尾）
     */
    private Boolean isDirectory;

    /**
     * 存储类别（标准存储/低频存储/归档存储等）
     */
    private String storageClass;
}