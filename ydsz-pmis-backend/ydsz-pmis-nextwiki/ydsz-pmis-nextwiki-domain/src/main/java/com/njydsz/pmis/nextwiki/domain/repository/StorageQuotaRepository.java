package com.njydsz.pmis.nextwiki.domain.repository;

import com.njydsz.pmis.nextwiki.domain.entity.StorageQuota;

/**
 * 存储配额仓储接口
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface StorageQuotaRepository {

    StorageQuota save(StorageQuota quota);

    StorageQuota findById(String id);

    StorageQuota findByScope(String scopeType, String scopeId);

    /**
     * 原子增加已使用量
     */
    void addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);

    /**
     * 原子减少已使用量
     */
    void subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);
}
