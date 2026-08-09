package com.njydsz.nextwiki.domain.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 回收站条目实体
 * <p>
 * 记录被逻辑删除的文件/文件夹，支持恢复和自动清理。
 * 默认保留 30 天，超期自动永久删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_trash_item")
public class TrashItem extends MpBaseEntity<String> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原文件节点ID */
    private String fileNodeId;

    /** 原文件名 */
    private String originalName;

    /** 原始路径 */
    private String originalPath;

    /** 原始父节点ID */
    private String originalParentId;

    /** 节点类型：folder / file */
    private String nodeType;

    /** 文件大小（字节） */
    private Long size;

    /** 删除时间 */
    private LocalDateTime deletedTime;

    /** 预计永久删除时间 */
    private LocalDateTime purgeTime;

    /** 状态：in_trash / restored / purged */
    private String status;

    /** 默认保留天数 */
    public static final int DEFAULT_RETENTION_DAYS = 30;
}
