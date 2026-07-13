package com.njydsz.pmis.nextwiki.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;

/**
 * 存储配额实体
 * <p>
 * 按用户/租户/项目维度设置存储上限，上传时校验配额。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("nw_storage_quota")
public class StorageQuota extends BaseEntity<String> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 配额维度：user / tenant / project */
    private String scopeType;

    /** 维度ID（用户ID / 租户ID / 项目ID） */
    private String scopeId;

    /** 配额上限（字节） */
    private Long quotaLimit;

    /** 已使用量（字节） */
    private Long quotaUsed;

    /** 文件数量上限 */
    private Integer fileCountLimit;

    /** 已使用文件数量 */
    private Integer fileCountUsed;

    /**
     * 检查是否有足够空间
     */
    public boolean hasSpace(long requiredBytes) {
        if (quotaLimit == null || quotaLimit <= 0) {
            return true;
        }
        long used = quotaUsed != null ? quotaUsed : 0;
        return used + requiredBytes <= quotaLimit;
    }

    /**
     * 检查是否有足够文件数量
     */
    public boolean hasFileCountSlot() {
        if (fileCountLimit == null || fileCountLimit <= 0) {
            return true;
        }
        int used = fileCountUsed != null ? fileCountUsed : 0;
        return used < fileCountLimit;
    }
}
