package com.njydsz.project.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 文件存储视图对象
 *
 * <p>用于 Controller 层返回文件上传 / 存储信息，屏蔽实体层内部字段。
 * 对应实体 {@link com.njydsz.common.file.domain.FileStorage}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FileStorageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文件唯一标识 */
    private String id;

    /** 文件访问地址 */
    private String url;

    /** 原始文件名 */
    private String fileName;

    /** 存储端生成的唯一对象名 */
    private String uuidName;

    /** 重命名后的文件名 */
    private String fileRename;

    /** 文件扩展名（小写） */
    private String suffix;

    /** 文件大小（字节） */
    private Long size;

    /** 文件分类 */
    private String type;

    /** MIME Type */
    private String mimeType;

    /** 上传时间 */
    private LocalDateTime uploadAt;

    /** 文件来源 */
    private String source;
}
