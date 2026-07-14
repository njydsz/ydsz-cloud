package com.njydsz.pmis.nextwiki.domain.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.nextwiki.domain.entity.StorageQuota;
import com.njydsz.pmis.nextwiki.domain.repository.StorageQuotaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储配额领域服务
 * <p>
 * 管理用户/租户级存储配额的校验和更新。
 * 上传时校验配额，删除时释放配额。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
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
            throw BusinessException.builder().key("存储空间不足: " + String.format("已用 %s / 总量 %s / 本次需要 %s", formatSize(used), formatSize(limit), formatSize(requiredBytes))).build();
        }

        if (!quota.hasFileCountSlot()) {
            throw BusinessException.builder().key("文件数量已达上限: " + quota.getFileCountLimit()).build();
        }
    }

    /**
     * 增加已使用量（上传成功后调用）
     */
    public void addUsage(String scopeType, String scopeId, long bytes, int fileCount) {
        int affected = quotaRepository.addUsage(scopeType, scopeId, bytes, fileCount);
        if (affected == 0) {
            throw BusinessException.builder().key(
                    "存储空间不足或文件数量超限（并发竞争）: " + scopeType + ":" + scopeId
            ).build();
        }
        log.info("[QuotaDomainService] 增加用量: scope={}, bytes={}, fileCount={}",
                scopeType + ":" + scopeId, formatSize(bytes), fileCount);
    }

    /**
     * 减少已使用量（删除后调用）
     */
    public void subtractUsage(String scopeType, String scopeId, long bytes, int fileCount) {
        quotaRepository.subtractUsage(scopeType, scopeId, bytes, fileCount);
        log.info("[QuotaDomainService] 减少用量: scope={}, bytes={}, fileCount={}",
                scopeType + ":" + scopeId, formatSize(bytes), fileCount);
    }

    /**
     * 设置配额
     */
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
            quota.setCreatedAt(LocalDateTime.now());
        } else {
            quota.setQuotaLimit(limit);
            quota.setFileCountLimit(fileCountLimit);
        }
        quota.setUpdatedBy(userId);
        quota.setUpdatedAt(LocalDateTime.now());
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
            quota.setCreatedBy("system");
            quota.setCreatedAt(LocalDateTime.now());
            quota.setUpdatedBy("system");
            quota.setUpdatedAt(LocalDateTime.now());
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
