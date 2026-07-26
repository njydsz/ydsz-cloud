package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.entity.TrashItem;

/**
 * 回收站仓储接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TrashItemRepository {

    TrashItem save(TrashItem trashItem);

    TrashItem findById(String id);

    TrashItem findByFileNodeId(String fileNodeId);

    List<TrashItem> findActiveTrash(String userId);

    List<TrashItem> findExpiredItems(int limit);

    void update(TrashItem trashItem);

    void deleteById(String id);

    int countActiveTrash(String userId);
}
