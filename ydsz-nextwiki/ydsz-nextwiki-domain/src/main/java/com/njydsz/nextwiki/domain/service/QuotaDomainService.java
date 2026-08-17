package com.njydsz.nextwiki.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * NextWiki 配额领域服务。
 *
 * <p>租户/用户的存储配额管理，提供纯领域逻辑（配额校验、配额计算）。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>domain 层不直接注入 Repository，数据通过方法参数传入
 *   <li>数据访问由 server 层 Application Service 负责编排
 *   <li>缓存管理由 server 层负责
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class QuotaDomainService {

  /** 默认用户配额：10GB */
  private static final long DEFAULT_USER_QUOTA = 10L * 1024 * 1024 * 1024;

  /** 默认用户文件数上限：10000 */
  private static final int DEFAULT_USER_FILE_LIMIT = 10000;

  /**
   * 校验是否有足够空间上传。
   *
   * <p>纯领域逻辑：校验配额是否足够，不涉及数据访问。
   *
   * @param quota 配额实体（由 server 层查询后传入）
   * @param requiredBytes 本次上传所需字节数
   * @throws BusinessException 配额不足或文件数超限时抛出
   */
  public void checkQuota(StorageQuota quota, long requiredBytes) {
    if (quota == null) {
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_INSUFFICIENT)
          .data("reason", "配额记录不存在");
    }

    if (!quota.hasSpace(requiredBytes)) {
      long used = quota.getQuotaUsed() != null ? quota.getQuotaUsed() : 0;
      long limit = quota.getQuotaLimit() != null ? quota.getQuotaLimit() : 0;
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_INSUFFICIENT)
          .data("used", formatSize(used))
          .data("limit", formatSize(limit))
          .data("required", formatSize(requiredBytes));
    }

    if (!quota.hasFileCountSlot()) {
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_FILE_LIMIT)
          .data("limit", quota.getFileCountLimit());
    }
  }

  /**
   * 构建默认配额实体（首次访问时自动创建）。
   *
   * <p>纯领域逻辑：构建默认配额实体，不涉及数据访问。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @return 新建的默认配额实体（未持久化）
   */
  public StorageQuota buildDefaultQuota(String scopeType, String scopeId) {
    long defaultLimit = DEFAULT_USER_QUOTA;
    int defaultFileLimit = DEFAULT_USER_FILE_LIMIT;
    if ("tenant".equals(scopeType)) {
      defaultLimit = 100L * 1024 * 1024 * 1024;
      defaultFileLimit = 100000;
    }
    return StorageQuota.builder()
        .scopeType(scopeType)
        .scopeId(scopeId)
        .quotaLimit(defaultLimit)
        .quotaUsed(0L)
        .fileCountLimit(defaultFileLimit)
        .fileCountUsed(0)
        .revision(0)
        .deleted(0)
        .build();
  }

  /**
   * 格式化文件大小为可读字符串。
   *
   * @param bytes 字节数
   * @return 可读大小字符串（如 "1.5 GB"）
   */
  private static String formatSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024 * 1024) {
      return String.format("%.1f KB", bytes / 1024.0);
    }
    if (bytes < 1024 * 1024 * 1024) {
      return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
    return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
  }
}
