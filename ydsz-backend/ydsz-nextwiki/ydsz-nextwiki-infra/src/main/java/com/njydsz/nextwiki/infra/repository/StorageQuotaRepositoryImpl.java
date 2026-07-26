package com.njydsz.nextwiki.infra.repository;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.repository.StorageQuotaRepository;
import com.njydsz.nextwiki.infra.mapper.StorageQuotaMapper;

import lombok.RequiredArgsConstructor;

/**
 * 存储配额仓储实现
 *
 * @author ydsz-team
 * @since 1.0.0
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
            if (quota.getRevision() == null) {
                // 兜底：未携带 revision 时退化为普通更新，避免业务阻断
                storageQuotaMapper.updateById(quota);
            } else {
                int affected = storageQuotaMapper.updateWithRevision(quota);
                if (affected == 0) {
                    throw new OptimisticLockingFailureException(
                            "StorageQuota 乐观锁更新失败，id=" + quota.getId()
                                    + ", revision=" + quota.getRevision());
                }
                quota.setRevision(quota.getRevision() + 1);
            }
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
    public int addUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
        return storageQuotaMapper.addUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
    }

    @Override
    public int subtractUsage(String scopeType, String scopeId, long bytesDelta, int fileCountDelta) {
        return storageQuotaMapper.subtractUsage(scopeType, scopeId, bytesDelta, fileCountDelta);
    }
}
