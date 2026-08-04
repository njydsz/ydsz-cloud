package com.remisoft.nextwiki.domain.event;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/**
 * 文件操作领域事件
 * <p>
 * 所有文件操作（上传、删除、移动、重命名、分享等）均发布此事件，
 * 由异步监听器驱动后续管线：索引同步、缩略图生成、审计记录、通知推送。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Builder
public class FileOperatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作类型 */
    private String operation;

    /** 文件节点ID */
    private String fileNodeId;

    /** 文件名 */
    private String fileName;

    /** 节点类型 */
    private String nodeType;

    /** 存储对象键 */
    private String storageKey;

    /** 存储桶名称 */
    private String bucketName;

    /** 操作人ID */
    private String operatorId;

    /** 操作时间 */
    private LocalDateTime operatedAt;

    /** 额外参数（如移动操作的目标路径） */
    private String extra;

    /** 操作类型常量 */
    public static final String OP_UPLOAD = "UPLOAD";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_MOVE = "MOVE";
    public static final String OP_RENAME = "RENAME";
    public static final String OP_SHARE = "SHARE";
    public static final String OP_RESTORE = "RESTORE";
    public static final String OP_VERSION_ROLLBACK = "VERSION_ROLLBACK";
}
