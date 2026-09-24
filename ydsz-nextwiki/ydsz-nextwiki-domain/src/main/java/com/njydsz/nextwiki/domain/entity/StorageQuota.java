package com.njydsz.nextwiki.domain.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 存储配额实体。
 *
 * <p>按用户/租户/项目维度设置存储上限，上传时校验配额。每条记录通过 {@link #scopeType} 标识配额维度
 * （user/tenant/project），{@link #scopeId} 对应维度 ID，{@link #quotaLimit} 和 {@link #quotaUsed}
 * 分别记录字节级配额上限和已用量，{@link #fileCountLimit} 和 {@link #fileCountUsed} 记录文件数量维度的上限和已用量。
 * 为 null 或 0 表示不限制。
 *
 * <p><b>表名：</b>{@code ydsz_wiki_storage_quota}
 *
 * @author ydsz
 * @since 26.09.24
 */@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName("ydsz_wiki_storage_quota")
public class StorageQuota extends MpBaseEntity<String> implements Serializable {

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
   * 检查是否有足够空间。
   *
   * @param requiredBytes 所需字节数
   * @return 剩余空间足够时返回 true（未设上限时恒为 true）
   */
  public boolean hasSpace(long requiredBytes) {
    if (quotaLimit == null || quotaLimit <= 0) {
      return true;
    }
    long used = quotaUsed != null ? quotaUsed : 0;
    return used + requiredBytes <= quotaLimit;
  }

  /**
   * 检查是否有足够文件数量。
   *
   * @return 剩余文件数量配额大于 0 时返回 true（未设上限时恒为 true）
   */
  public boolean hasFileCountSlot() {
    if (fileCountLimit == null || fileCountLimit <= 0) {
      return true;
    }
    int used = fileCountUsed != null ? fileCountUsed : 0;
    return used < fileCountLimit;
  }
}
