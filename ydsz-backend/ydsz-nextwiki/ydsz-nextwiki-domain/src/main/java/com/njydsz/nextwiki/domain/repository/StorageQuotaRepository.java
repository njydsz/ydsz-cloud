package com.njydsz.nextwiki.domain.repository;

import com.njydsz.nextwiki.domain.entity.StorageQuota;

/**
 * 存储配额仓储接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface StorageQuotaRepository {

    StorageQuota save(StorageQuota quota);

    StorageQuota findById(String id);

    StorageQuota findByScope(String scopeType, String scopeId);

    /**
     * 原子增加已使用量
     */
    int addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);

    /**
     * 原子减少已使用量
     */
    int subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta);
}
