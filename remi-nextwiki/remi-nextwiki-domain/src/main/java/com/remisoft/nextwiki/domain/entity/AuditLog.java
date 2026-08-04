package com.remisoft.nextwiki.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 文件操作审计日志实体（P2-6）
 * <p>
 * 持久化文件操作审计记录，支持查询和导出。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_audit_log")
public class AuditLog extends MpBaseEntity<String> implements Serializable {

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

    /** 额外参数 */
    private String extra;

    /** 操作结果：success / failure */
    private String result;

    /** 失败原因 */
    private String errorMessage;
}
