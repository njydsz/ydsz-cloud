package com.njydsz.common.file.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 文件存储管理实体类
 * <p>
 * 存储文件的基本信息：文件标识、名称、大小、类型、上传时间、存储位置等。
 * 业务层基于此实体实现文件元信息管理（数据库 / Redis 缓存）。
 * </p>
 *
 * <p><b>核心字段：</b></p>
 * <ul>
 *   <li>{@link #id} - 文件唯一标识（雪花算法生成）</li>
 *   <li>{@link #parentId} - 父目录标识</li>
 *   <li>{@link #url} - 文件访问地址</li>
 *   <li>{@link #fileName} - 原始文件名（含原始后缀）</li>
 *   <li>{@link #uuidName} - 存储端生成的唯一对象名（防重名 + 防路径遍历）</li>
 *   <li>{@link #fileRename} - 重命名后的文件名</li>
 *   <li>{@link #suffix} - 文件扩展名（小写）</li>
 *   <li>{@link #dirIds} - 所属目录路径标识</li>
 *   <li>{@link #isDir} - 是否为目录（1=是，0=否）</li>
 *   <li>{@link #isImage} / {@link #isVideo} / {@link #isAudio} / {@link #isOffice} / {@link #isCode} - 文件类型标记</li>
 *   <li>{@link #size} - 文件大小（字节）</li>
 *   <li>{@link #type} - 文件分类（如 image、video、code 等）</li>
 *   <li>{@link #uploadAt} - 上传时间</li>
 *   <li>{@link #source} - 文件来源（系统名 / 业务模块）</li>
 *   <li>{@link #mimeType} - MIME Type（如 image/png、application/pdf）</li>
 * </ul>
 *
 * <p><b>安全约束：</b>{@link #uuidName} 应基于 UUID/雪花 ID 重新生成，
 * 不使用用户输入的原始文件名作为存储 Key，避免路径遍历攻击。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class FileStorage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件唯一标识 */
    private String id;

    /** 父目录标识（用于构建目录树） */
    private String parentId;

    /** 文件访问地址 */
    private String url;

    /** 原始文件名（用户上传时的原始文件名） */
    private String fileName;

    /** 存储端生成的唯一对象名（基于 UUID/雪花 ID 重新生成，防路径遍历） */
    private String uuidName;

    /** 重命名后的文件名（业务可定制的展示名） */
    private String fileRename;

    /** 文件扩展名（统一小写） */
    private String suffix;

    /** 所属目录路径标识（多级目录时使用） */
    private String dirIds;

    /** 是否为目录（1=是，0=否） */
    private Integer isDir;

    /** 是否为图片（1=是，0=否） */
    private Integer isImage;

    /** 是否为视频（1=是，0=否） */
    private Integer isVideo;

    /** 是否为音频（1=是，0=否） */
    private Integer isAudio;

    /** 是否为办公文档（1=是，0=否） */
    private Integer isOffice;

    /** 是否为代码文件（1=是，0=否） */
    private Integer isCode;

    /** 文件大小（字节） */
    private Long size;

    /** 文件分类（如 image、video、code 等） */
    private String type;

    /** MIME Type（如 image/png、application/pdf） */
    private String mimeType;

    /** 上传时间 */
    private LocalDateTime uploadAt;

    /** 文件来源（系统名 / 业务模块） */
    private String source;
}
