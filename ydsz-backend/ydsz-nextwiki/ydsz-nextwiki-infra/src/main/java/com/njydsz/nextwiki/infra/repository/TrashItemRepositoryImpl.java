package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.nextwiki.infra.mapper.TrashItemMapper;

import lombok.RequiredArgsConstructor;

/**
 * 回收站仓储实现
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Repository
@RequiredArgsConstructor
public class TrashItemRepositoryImpl implements TrashItemRepository {

    private final TrashItemMapper trashItemMapper;

    @Override
    public TrashItem save(TrashItem trashItem) {
        trashItemMapper.insert(trashItem);
        return trashItem;
    }

    @Override
    public TrashItem findById(String id) {
        return trashItemMapper.selectById(id);
    }

    @Override
    public TrashItem findByFileNodeId(String fileNodeId) {
        return trashItemMapper.findByFileNodeId(fileNodeId);
    }

    @Override
    public List<TrashItem> findActiveTrash(String userId) {
        return trashItemMapper.findActiveTrash(userId);
    }

    @Override
    public List<TrashItem> findExpiredItems(int limit) {
        return trashItemMapper.findExpiredItems(limit);
    }

    @Override
    public void update(TrashItem trashItem) {
        if (trashItem.getRevision() == null) {
            // 兜底：未携带 revision 时退化为普通更新，避免业务阻断
            trashItemMapper.updateById(trashItem);
            return;
        }
        int affected = trashItemMapper.updateWithRevision(trashItem);
        if (affected == 0) {
            throw new OptimisticLockingFailureException(
                    "TrashItem 乐观锁更新失败，id=" + trashItem.getId()
                            + ", revision=" + trashItem.getRevision());
        }
        trashItem.setRevision(trashItem.getRevision() + 1);
    }

    @Override
    public void deleteById(String id) {
        trashItemMapper.deleteById(id);
    }

    @Override
    public int countActiveTrash(String userId) {
        return trashItemMapper.countActiveTrash(userId);
    }
}
