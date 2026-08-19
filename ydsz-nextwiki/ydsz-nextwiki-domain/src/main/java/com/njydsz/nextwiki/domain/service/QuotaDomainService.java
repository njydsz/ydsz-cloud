package com.njydsz.nextwiki.domain.service;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * NextWiki 配额领域服务。
 *
 * <p>租户/用户的存储配额管理，提供纯领域逻辑（配额校验、配额计算）。
 *
 * <p><b>S3-P2-6 改进：文件类型配额控制</b>
 *
 * <p>在全局容量配额基础上，支持按文件类型（图片/视频/文档等）分别限额， 满足企业差异化配额管理需求（如限制单个用户图片总空间 5GB、视频总空间 20GB）。
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
public class QuotaDomainService {

  /** 默认用户配额：10GB */
  private static final long DEFAULT_USER_QUOTA = 10L * 1024 * 1024 * 1024;

  /** 默认用户文件数上限：10000 */
  private static final int DEFAULT_USER_FILE_LIMIT = 10000;

  /** 默认用户图片配额：5GB */
  private static final long DEFAULT_IMAGE_QUOTA = 5L * 1024 * 1024 * 1024;

  /** 默认用户视频配额：20GB */
  private static final long DEFAULT_VIDEO_QUOTA = 20L * 1024 * 1024 * 1024;

  /** 默认用户文档配额：10GB */
  private static final long DEFAULT_DOCUMENT_QUOTA = 10L * 1024 * 1024 * 1024;

  /**
   * 校验是否有足够空间上传。
   *
   * <p>S3-P2-6：在全局配额校验基础上，增加按文件类型的专项配额校验。
   *
   * <h4>校验顺序：</h4>
   *
   * <ol>
   *   <li>校验配额记录是否存在
   *   <li>校验全局容量配额（总空间）
   *   <li>校验全局文件数量上限
   *   <li>校验文件类型配额（如果存在 fileType 参数）
   * </ol>
   *
   * @param quota 配额实体（由 server 层查询后传入）
   * @param requiredBytes 本次上传所需字节数
   * @param fileType 文件类型（image/video/document/other 等，可为 {@code null} 表示不校验类型配额）
   * @throws BusinessException 配额不足或文件数超限时抛出
   */
  public void checkQuota(StorageQuotaDTO quota, long requiredBytes, String fileType) {
    if (quota == null) {
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_INSUFFICIENT)
          .data("reason", "配额记录不存在");
    }

    // 全局容量校验
    checkGlobalQuota(quota, requiredBytes);

    // 全局文件数校验
    checkFileCountLimit(quota);

    // 文件类型专项配额校验（S3-P2-6 新增）
    if (fileType != null && !fileType.isEmpty()) {
      checkFileTypeQuota(quota, requiredBytes, fileType);
    }
  }

  /**
   * 校验是否有足够空间上传（不校验文件类型配额，兼容旧接口）。
   *
   * <p>纯领域逻辑：校验配额是否足够，不涉及数据访问。
   *
   * @param quota 配额实体（由 server 层查询后传入）
   * @param requiredBytes 本次上传所需字节数
   * @throws BusinessException 配额不足或文件数超限时抛出
   */
  public void checkQuota(StorageQuotaDTO quota, long requiredBytes) {
    checkQuota(quota, requiredBytes, null);
  }

  /**
   * 校验全局容量配额。
   *
   * @param quota 配额实体
   * @param requiredBytes 所需字节数
   * @throws BusinessException 容量不足时抛出
   */
  private void checkGlobalQuota(StorageQuotaDTO quota, long requiredBytes) {
    if (!hasSpace(quota, requiredBytes)) {
      long used = quota.getQuotaUsed() != null ? quota.getQuotaUsed() : 0;
      long limit = quota.getQuotaLimit() != null ? quota.getQuotaLimit() : 0;
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_INSUFFICIENT)
          .data("quotaType", "global")
          .data("used", formatSize(used))
          .data("limit", formatSize(limit))
          .data("required", formatSize(requiredBytes));
    }
  }

  /**
   * 校验文件数量上限。
   *
   * @param quota 配额实体
   * @throws BusinessException 文件数超限时抛出
   */
  private void checkFileCountLimit(StorageQuotaDTO quota) {
    if (!hasFileCountSlot(quota)) {
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_FILE_LIMIT)
          .data("limit", quota.getFileCountLimit());
    }
  }

  /**
   * 校验文件类型专项配额（S3-P2-6 新增）。
   *
   * <p>根据文件后缀判断类型，分别校验对应配额限制。 若未配置某类型配额（类型配额限制为 null 或 ≤ 0），则跳过该校验。
   *
   * @param quota 配额实体
   * @param requiredBytes 所需字节数
   * @param fileType 文件类型（image/video/document/other）
   * @throws BusinessException 类型配额不足时抛出
   */
  private void checkFileTypeQuota(StorageQuotaDTO quota, long requiredBytes, String fileType) {
    // 获取该类型已使用量（从配额实体的扩展字段中读取，若不存在则为 0）
    Long typeUsed = getFileTypeUsed(quota, fileType);
    // 获取该类型配额限制（从配额实体的扩展字段中读取）
    Long typeLimit = getFileTypeLimit(quota, fileType);

    // 未配置类型配额 → 跳过
    if (typeLimit == null || typeLimit <= 0) {
      return;
    }

    if (typeUsed + requiredBytes > typeLimit) {
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_FILE_TYPE_LIMIT)
          .data("quotaType", "fileType")
          .data("fileType", fileType)
          .data("used", formatSize(typeUsed))
          .data("limit", formatSize(typeLimit))
          .data("required", formatSize(requiredBytes));
    }
  }

  /**
   * 获取指定文件类型的已使用量。
   *
   * <p>优先从配额实体的扩展字段（{@code fileTypeUsedMap}）读取； 若不存在则使用默认配额使用量的按比例估算。
   *
   * @param quota 配额实体
   * @param fileType 文件类型
   * @return 已使用字节数（不会返回 {@code null}）
   */
  private Long getFileTypeUsed(StorageQuotaDTO quota, String fileType) {
    if (quota.getFileTypeUsedMap() != null && quota.getFileTypeUsedMap().containsKey(fileType)) {
      return quota.getFileTypeUsedMap().get(fileType);
    }
    // 默认返回 0（表示未单独追踪该类型用量）
    return 0L;
  }

  /**
   * 获取指定文件类型的配额限制。
   *
   * <p>优先从配额实体的扩展字段（{@code fileTypeLimitMap}）读取； 若不存在则使用全局默认值。
   *
   * @param quota 配额实体
   * @param fileType 文件类型
   * @return 配额限制字节数（null 表示未配置）
   */
  private Long getFileTypeLimit(StorageQuotaDTO quota, String fileType) {
    if (quota.getFileTypeLimitMap() != null && quota.getFileTypeLimitMap().containsKey(fileType)) {
      return quota.getFileTypeLimitMap().get(fileType);
    }
    // 使用默认类型配额
    return getDefaultFileTypeLimit(fileType);
  }

  /**
   * 获取默认文件类型配额限制。
   *
   * <p>未单独配置类型配额时使用的默认值。
   *
   * @param fileType 文件类型
   * @return 默认配额限制（null 表示无限制）
   */
  private Long getDefaultFileTypeLimit(String fileType) {
    if (fileType == null) {
      return null;
    }
    switch (fileType.toLowerCase()) {
      case "image":
        return DEFAULT_IMAGE_QUOTA;
      case "video":
        return DEFAULT_VIDEO_QUOTA;
      case "document":
        return DEFAULT_DOCUMENT_QUOTA;
      default:
        return null; // 其他类型不限制专项配额
    }
  }

  // ==================== 静态工具方法 ====================

  /** 检查是否有足够空间 */
  public static boolean hasSpace(StorageQuotaDTO quota, long requiredBytes) {
    if (quota.getQuotaLimit() == null || quota.getQuotaLimit() <= 0) {
      return true;
    }
    long used = quota.getQuotaUsed() != null ? quota.getQuotaUsed() : 0;
    return used + requiredBytes <= quota.getQuotaLimit();
  }

  /** 检查是否有足够文件数量 */
  public static boolean hasFileCountSlot(StorageQuotaDTO quota) {
    if (quota.getFileCountLimit() == null || quota.getFileCountLimit() <= 0) {
      return true;
    }
    int used = quota.getFileCountUsed() != null ? quota.getFileCountUsed() : 0;
    return used < quota.getFileCountLimit();
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
  public StorageQuotaDTO buildDefaultQuota(String scopeType, String scopeId) {
    long defaultLimit = DEFAULT_USER_QUOTA;
    int defaultFileLimit = DEFAULT_USER_FILE_LIMIT;
    if ("tenant".equals(scopeType)) {
      defaultLimit = 100L * 1024 * 1024 * 1024;
      defaultFileLimit = 100000;
    }
    return StorageQuotaDTO.builder()
        .scopeType(scopeType)
        .scopeId(scopeId)
        .quotaLimit(defaultLimit)
        .quotaUsed(0L)
        .fileCountLimit(defaultFileLimit)
        .fileCountUsed(0)
        .build();
  }

  /**
   * 格式化文件大小为可读字符串。
   *
   * @param bytes 字节数
   * @return 可读大小字符串（如 "1.5 GB"）
   */
  public static String formatSize(long bytes) {
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
