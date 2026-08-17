package com.njydsz.nextwiki.domain.repository;

import com.njydsz.nextwiki.infra.entity.StorageQuotaDO;

/**
 * 存储配额仓储接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface StorageQuotaRepository {

  /**
   * 保存存储配额记录（新增或更新）。
   *
   * @param quota 待持久化的配额实体（含维度、上限、已用量等）
   * @return 持久化后的配额（回填主键）
   */
  StorageQuotaDO save(StorageQuotaDO quota);

  /**
   * 按 ID 查询配额记录。
   *
   * @param id 配额记录 ID
   * @return 配额实体，不存在时返回 null
   */
  StorageQuotaDO findById(String id);

  /**
   * 按维度（scopeType + scopeId）查询配额记录，用于上传/删除前的用量校验。
   *
   * @param scopeType 配额维度（user/tenant/project）
   * @param scopeId 维度 ID（用户/租户/项目 ID）
   * @return 配额实体，不存在时返回 null
   */
  StorageQuotaDO findByScope(String scopeType, String scopeId);

  /** 原子增加已使用量 */
  int addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);

  /** 原子减少已使用量 */
  int subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);
}
