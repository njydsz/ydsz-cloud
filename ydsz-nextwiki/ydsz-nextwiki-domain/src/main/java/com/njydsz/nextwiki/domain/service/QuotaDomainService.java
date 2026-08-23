package com.njydsz.nextwiki.domain.service;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * 存储配额领域服务
 *
 * <p>封装存储配额的核心业务逻辑：配额校验、用量统计、超限判定。
 * 本服务为纯领域逻辑组件，不执行任何数据访问；数据由应用层加载后传入。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class QuotaDomainService {

  /** 默认配额上限：5 GB（字节） */
  private static final long DEFAULT_QUOTA_LIMIT = 5L * 1024 * 1024 * 1024;

  /** 默认文件数上限：10000 */
  private static final int DEFAULT_FILE_COUNT_LIMIT = 10000;

  /**
   * 构建默认配额（纯领域逻辑，不执行持久化）。
   *
   * <p>当用户 / 空间 / 租户首次访问配额能力时，由应用层调用本方法生成默认配额记录。
   *
   * @param scopeType 配额维度（user / space / tenant）
   * @param scopeId 维度 ID（用户 ID / 空间 ID / 租户 ID）
   * @return 默认配额 DTO（未持久化）
   */
  public StorageQuotaDTO buildDefaultQuota(String scopeType, String scopeId) {
    StorageQuotaDTO dto = new StorageQuotaDTO();
    dto.setScopeType(scopeType);
    dto.setScopeId(scopeId);
    dto.setQuotaLimit(DEFAULT_QUOTA_LIMIT);
    dto.setQuotaUsed(0L);
    dto.setFileCountLimit(DEFAULT_FILE_COUNT_LIMIT);
    dto.setFileCountUsed(0);
    return dto;
  }

  /**
   * 校验是否超出存储配额（纯领域逻辑）。
   *
   * <p>由应用层传入用户当前配额与使用量，本方法判定写入指定大小文件后是否超限。
   *
   * @param quota 用户配额信息（已由应用层加载）
   * @param fileSize 待写入文件大小（字节）
   * @throws BusinessException 超出配额时抛出
   */
  public void checkQuota(StorageQuotaDTO quota, long fileSize) {
    if (quota == null || quota.getQuotaLimit() == null) {
      throw new BusinessException(NextwikiExceptionCode.QUOTA_NOT_FOUND);
    }

    long capacity = quota.getQuotaLimit();
    long used = quota.getQuotaUsed() != null ? quota.getQuotaUsed() : 0L;

    if (used + fileSize > capacity) {
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_INSUFFICIENT)
          .data("capacity", capacity)
          .data("used", used)
          .data("fileSize", fileSize);
    }

    log.info("[QuotaDomainService] 配额校验通过: capacity={}, used={}, fileSize={}", capacity, used, fileSize);
  }

  /**
   * 计算剩余可用空间（纯领域逻辑）。
   *
   * @param quota 用户配额信息（已由应用层加载）
   * @return 剩余可用字节数
   */
  public long getRemainingSpace(StorageQuotaDTO quota) {
    if (quota == null || quota.getQuotaLimit() == null) {
      return 0L;
    }

    long capacity = quota.getQuotaLimit();
    long used = quota.getQuotaUsed() != null ? quota.getQuotaUsed() : 0L;
    return Math.max(0, capacity - used);
  }

  /**
   * 判断是否已使用超过指定百分比（纯领域逻辑）。
   *
   * @param quota 用户配额信息（已由应用层加载）
   * @param percentage 百分比阈值（0-100）
   * @return {@code true} 表示已使用超过指定百分比
   */
  public boolean isOverPercentage(StorageQuotaDTO quota, int percentage) {
    if (quota == null || quota.getQuotaLimit() == null || quota.getQuotaLimit() == 0) {
      return false;
    }

    long capacity = quota.getQuotaLimit();
    long used = quota.getQuotaUsed() != null ? quota.getQuotaUsed() : 0L;
    double ratio = (double) used / capacity * 100;
    return ratio >= percentage;
  }
}
