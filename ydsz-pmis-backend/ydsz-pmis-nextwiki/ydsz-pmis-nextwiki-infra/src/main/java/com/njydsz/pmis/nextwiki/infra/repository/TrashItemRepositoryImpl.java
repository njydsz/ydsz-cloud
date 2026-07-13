package com.njydsz.pmis.nextwiki.infra.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.njydsz.pmis.nextwiki.domain.entity.TrashItem;
import com.njydsz.pmis.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.pmis.nextwiki.infra.mapper.TrashItemMapper;

import lombok.RequiredArgsConstructor;

/**
 * 回收站仓储实现
 *
 * @author ydsz-pmis-team
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
        trashItemMapper.updateById(trashItem);
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
