package com.remisoft.nextwiki.domain.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.remisoft.common.cache.constant.CacheConstants;
import com.remisoft.common.core.constant.SystemConstants;
import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.nextwiki.domain.entity.StorageQuota;
import com.remisoft.nextwiki.domain.enums.NextwikiExceptionCode;
import com.remisoft.nextwiki.domain.repository.StorageQuotaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki 配额领域服务。
 * <p>租户/用户的存储配额管理。
 *
 * @author remi-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class QuotaDomainService {

    private final StorageQuotaRepository quotaRepository;

    /** 默认用户配额：10GB */
    private static final long DEFAULT_USER_QUOTA = 10L * 1024 * 1024 * 1024;

    /** 默认用户文件数上限：10000 */
    private static final int DEFAULT_USER_FILE_LIMIT = 10000;

    /**
     * 校验是否有足够空间上传
     */
    public void checkQuota(String scopeType, String scopeId, long requiredBytes) {
        StorageQuota quota = getOrCreateQuota(scopeType, scopeId);

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
     * 增加已使用量（上传成功后调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addUsage(String scopeType, String scopeId, long bytes, int fileCount) {
        int affected = quotaRepository.addUsage(scopeType, scopeId, bytes, fileCount);
        if (affected == 0) {
            throw BusinessException.of(NextwikiExceptionCode.QUOTA_INSUFFICIENT)
                    .data("scopeType", scopeType)
                    .data("scopeId", scopeId);
        }
        log.info("[QuotaDomainService] 增加用量: scope={}, bytes={}, fileCount={}",
                scopeType + ":" + scopeId, formatSize(bytes), fileCount);
    }

    /**
     * 减少已使用量（删除后调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void subtractUsage(String scopeType, String scopeId, long bytes, int fileCount) {
        quotaRepository.subtractUsage(scopeType, scopeId, bytes, fileCount);
        log.info("[QuotaDomainService] 减少用量: scope={}, bytes={}, fileCount={}",
                scopeType + ":" + scopeId, formatSize(bytes), fileCount);
    }

    /**
     * 设置配额
     * <p>
     * 配额变更可能影响后续上传/删除时的权限与容量校验链路，因此清除
     * {@code nextwiki:file:acl} 全部缓存条目，强制下一次校验重新走数据库。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = CacheConstants.NEXTWIKI_FILE_ACL_CACHE, allEntries = true)
    public StorageQuota setQuota(String scopeType, String scopeId, Long limit, Integer fileCountLimit, String userId) {
        StorageQuota quota = quotaRepository.findByScope(scopeType, scopeId);
        if (quota == null) {
            quota = StorageQuota.builder()
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
        return quotaRepository.save(quota);
    }

    /**
     * 查询配额使用情况
     */
    public StorageQuota getQuotaInfo(String scopeType, String scopeId) {
        return getOrCreateQuota(scopeType, scopeId);
    }

    // ==================== 私有方法 ====================

    private StorageQuota getOrCreateQuota(String scopeType, String scopeId) {
        StorageQuota quota = quotaRepository.findByScope(scopeType, scopeId);
        if (quota == null) {
            // 创建默认配额
            long defaultLimit = DEFAULT_USER_QUOTA;
            int defaultFileLimit = DEFAULT_USER_FILE_LIMIT;
            if ("tenant".equals(scopeType)) {
                defaultLimit = 100L * 1024 * 1024 * 1024; // 租户 100GB
                defaultFileLimit = 100000;
            }
            quota = StorageQuota.builder()
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
            quota = quotaRepository.save(quota);
        }
        return quota;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
