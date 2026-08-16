package com.njydsz.nextwiki.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.cache.constant.CacheConstants;
import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;

/**
 * NextWiki 配额领域服务。
 *
 * <p>租户/用户的存储配额管理，支持缓存加速读操作。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>读：先从 Redis 缓存读取（key = {@code nextwiki:quota:scopeType:scopeId}）， 未命中则查 DB 并回写缓存（TTL 30 分钟）
 *   <li>写：DB 原子更新成功后立即失效缓存，强制下次读取从 DB 获取最新值
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaDomainService {

  private final StorageQuotaRepository quotaRepository;
  private final RedisStringOps redisStringOps;

  /** 默认用户配额：10GB */
  private static final long DEFAULT_USER_QUOTA = 10L * 1024 * 1024 * 1024;

  /** 默认用户文件数上限：10000 */
  private static final int DEFAULT_USER_FILE_LIMIT = 10000;

  /** 配额缓存 TTL（秒）：30 分钟 */
  private static final long QUOTA_CACHE_TTL = 1800L;

  /**
   * 校验是否有足够空间上传。
   *
   * <p>优先从缓存读取配额信息以加速校验；缓存不存在时查 DB 并回写。
   *
   * @param scopeType 配额作用域类型（user / tenant / project）
   * @param scopeId 配额作用域 ID
   * @param requiredBytes 本次上传所需字节数
   * @throws BusinessException 配额不足或文件数超限时抛出
   */
  public void checkQuota(String scopeType, String scopeId, long requiredBytes) {
    StorageQuota quota = getQuotaFromCacheOrDb(scopeType, scopeId);

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
   * 增加已使用量（上传成功后调用）。
   *
   * <p>DB 原子更新成功后立即失效缓存，确保下次读取获取最新用量。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @param bytes 增加的文件大小（字节）
   * @param fileCount 增加的文件数量
   * @throws BusinessException DB 更新失败（配额不足）时抛出
   */
  @Transactional(rollbackFor = Exception.class)
  public void addUsage(String scopeType, String scopeId, long bytes, int fileCount) {
    int affected = quotaRepository.addUsage(scopeType, scopeId, bytes, fileCount);
    if (affected == 0) {
      throw BusinessException.of(NextwikiExceptionCode.QUOTA_INSUFFICIENT)
          .data("scopeType", scopeType)
          .data("scopeId", scopeId);
    }
    evictQuotaCache(scopeType, scopeId);
    log.info(
        "[QuotaDomainService] 增加用量: scope={}, bytes={}, fileCount={}",
        scopeType + ":" + scopeId,
        formatSize(bytes),
        fileCount);
  }

  /**
   * 减少已使用量（删除后调用）。
   *
   * <p>DB 更新成功后立即失效缓存，确保下次读取获取最新用量。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @param bytes 减少的文件大小（字节）
   * @param fileCount 减少的文件数量
   */
  @Transactional(rollbackFor = Exception.class)
  public void subtractUsage(String scopeType, String scopeId, long bytes, int fileCount) {
    quotaRepository.subtractUsage(scopeType, scopeId, bytes, fileCount);
    evictQuotaCache(scopeType, scopeId);
    log.info(
        "[QuotaDomainService] 减少用量: scope={}, bytes={}, fileCount={}",
        scopeType + ":" + scopeId,
        formatSize(bytes),
        fileCount);
  }

  /**
   * 设置配额（管理员操作）。
   *
   * <p>配额变更后清除配额缓存与 ACL 缓存，强制下一次校验重新加载最新数据。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @param limit 新的存储配额上限（字节）
   * @param fileCountLimit 新的文件数上限
   * @param userId 操作人 ID
   * @return 保存后的配额实体
   */
  @Transactional(rollbackFor = Exception.class)
  @CacheEvict(cacheNames = CacheConstants.NEXTWIKI_FILE_ACL_CACHE, allEntries = true)
  public StorageQuota setQuota(
      String scopeType, String scopeId, Long limit, Integer fileCountLimit, String userId) {
    StorageQuota quota = quotaRepository.findByScope(scopeType, scopeId);
    if (quota == null) {
      quota =
          StorageQuota.builder()
              .scopeType(scopeType)
              .scopeId(scopeId)
              .quotaLimit(limit)
              .quotaUsed(0L)
              .fileCountLimit(fileCountLimit)
              .fileCountUsed(0)
              .revision(0)
              .deleted(0)
              .build();
      quota.setCreatedBy(userId);
    } else {
      quota.setQuotaLimit(limit);
      quota.setFileCountLimit(fileCountLimit);
    }
    quota.setUpdatedBy(userId);
    StorageQuota saved = quotaRepository.save(quota);
    evictQuotaCache(scopeType, scopeId);
    return saved;
  }

  /**
   * 查询配额使用情况（优先从缓存读取）。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @return 配额实体（含用量与上限信息）
   */
  public StorageQuota getQuotaInfo(String scopeType, String scopeId) {
    return getQuotaFromCacheOrDb(scopeType, scopeId);
  }

  // ==================== 私有方法 ====================

  /**
   * 从缓存或 DB 读取配额信息。
   *
   * <p>先尝试 Redis 缓存；未命中则查 DB，如 DB 无记录则创建默认配额并缓存。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @return 配额实体（不为 {@code null}）
   */
  private StorageQuota getQuotaFromCacheOrDb(String scopeType, String scopeId) {
    String cacheKey = buildQuotaCacheKey(scopeType, scopeId);
    try {
      StorageQuota cached = redisStringOps.get(cacheKey, StorageQuota.class);
      if (cached != null) {
        return cached;
      }
    } catch (Exception e) {
      log.warn("[QuotaDomainService] 缓存读取失败，降级到 DB: {}", e.getMessage());
    }

    StorageQuota quota = quotaRepository.findByScope(scopeType, scopeId);
    if (quota == null) {
      quota = createDefaultQuota(scopeType, scopeId);
    }

    cacheQuota(quota);
    return quota;
  }

  /**
   * 创建默认配额。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @return 新建的默认配额实体
   */
  private StorageQuota createDefaultQuota(String scopeType, String scopeId) {
    long defaultLimit = DEFAULT_USER_QUOTA;
    int defaultFileLimit = DEFAULT_USER_FILE_LIMIT;
    if ("tenant".equals(scopeType)) {
      defaultLimit = 100L * 1024 * 1024 * 1024;
      defaultFileLimit = 100000;
    }
    StorageQuota quota =
        StorageQuota.builder()
            .scopeType(scopeType)
            .scopeId(scopeId)
            .quotaLimit(defaultLimit)
            .quotaUsed(0L)
            .fileCountLimit(defaultFileLimit)
            .fileCountUsed(0)
            .revision(0)
            .deleted(0)
            .build();
    quota.setCreatedBy(SystemConstants.SYSTEM_USER_ID);
    quota.setUpdatedBy(SystemConstants.SYSTEM_USER_ID);
    return quotaRepository.save(quota);
  }

  /**
   * 构建配额缓存键。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   * @return 缓存键字符串
   */
  private String buildQuotaCacheKey(String scopeType, String scopeId) {
    return CacheConstants.NEXTWIKI_QUOTA_CACHE + ":" + scopeType + ":" + scopeId;
  }

  /**
   * 将配额信息写入缓存。
   *
   * @param quota 配额实体
   */
  private void cacheQuota(StorageQuota quota) {
    if (quota == null) {
      return;
    }
    String cacheKey = buildQuotaCacheKey(quota.getScopeType(), quota.getScopeId());
    try {
      redisStringOps.set(cacheKey, quota, QUOTA_CACHE_TTL);
    } catch (Exception e) {
      log.warn("[QuotaDomainService] 缓存写入失败: {}", e.getMessage());
    }
  }

  /**
   * 失效配额缓存（写入操作后调用）。
   *
   * @param scopeType 配额作用域类型
   * @param scopeId 配额作用域 ID
   */
  private void evictQuotaCache(String scopeType, String scopeId) {
    String cacheKey = buildQuotaCacheKey(scopeType, scopeId);
    try {
      redisStringOps.del(cacheKey);
    } catch (Exception e) {
      log.warn("[QuotaDomainService] 缓存失效失败: {}", e.getMessage());
    }
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
