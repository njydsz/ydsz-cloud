package com.njydsz.pmis.nextwiki.infra.repository;

import com.njydsz.pmis.nextwiki.domain.entity.StorageQuota;
import com.njydsz.pmis.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.StorageQuotaMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 存储配额仓储实现
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class StorageQuotaRepositoryImpl implements StorageQuotaRepository {

    private final StorageQuotaMapper storageQuotaMapper;

    @Override
    public StorageQuota save(StorageQuota quota) {
        if (quota.getId() == null) {
            storageQuotaMapper.insert(quota);
        } else {
            storageQuotaMapper.updateById(quota);
        }
        return quota;
    }

    @Override
    public StorageQuota findById(String id) {
        return storageQuotaMapper.selectById(id);
    }

    @Override
    public StorageQuota findByScope(String scopeType, String scopeId) {
        return storageQuotaMapper.selectByScope(scopeType, scopeId);
    }

    @Override
    public void addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
        storageQuotaMapper.addUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
    }

    @Override
    public void subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
        storageQuotaMapper.subtractUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
    }
}
