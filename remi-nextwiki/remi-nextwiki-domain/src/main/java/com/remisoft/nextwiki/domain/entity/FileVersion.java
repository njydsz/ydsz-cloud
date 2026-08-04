package com.remisoft.nextwiki.domain.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 文件版本历史实体
 * <p>
 * 每次文件更新时生成一条版本记录，支持版本回溯和差异对比。
 * 版本号从 1 开始递增，{@link #versionNumber} 与 {@link FileNode#getCurrentVersion()} 对应。
 *
 * <p><b>版本保留策略：</b>
 * <ul>
 *   <li>默认保留最近 20 个版本</li>
 *   <li>超过限制时自动清理最旧版本（同时删除底层存储对象）</li>
 *   <li>已分享/星标的文件版本不自动清理</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_file_version")
public class FileVersion extends MpBaseEntity<String> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联的文件节点ID */
    private String fileNodeId;

    /** 版本号（从 1 开始递增） */
    private Integer versionNumber;

    /** 该版本的存储对象键 */
    private String storageKey;

    /** 该版本的文件大小（字节） */
    private Long size;

    /** 该版本的文件 SHA-256 哈希 */
    private String fileHash;

    /** 该版本的 MIME 类型 */
    private String mimeType;

    /** 版本说明（用户自定义的版本备注） */
    private String remark;

    /** 变更类型：create / update / rollback */
    private String changeType;

    /** 是否为当前活跃版本 */
    private Boolean isActive;
}
