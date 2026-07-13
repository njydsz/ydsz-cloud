package com.njydsz.pmis.nextwiki.domain.repository;

import com.njydsz.pmis.nextwiki.domain.entity.TrashItem;

import java.util.List;

/**
 * 回收站仓储接口
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
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
