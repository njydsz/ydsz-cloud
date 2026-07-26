package com.njydsz.nextwiki.server.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.service.TrashDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 回收站应用服务
 * <p>
 * 编排回收站列表、恢复、永久删除、清空操作，协调领域服务。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashApplicationService {

    private final TrashDomainService trashDomainService;

    public List<TrashItem> listTrash(String userId) {
        return trashDomainService.listTrash(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restore(String trashItemId, String userId) {
        trashDomainService.restore(trashItemId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchRestore(List<String> trashItemIds, String userId) {
        trashDomainService.batchRestore(trashItemIds, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void purge(String trashItemId, String userId) {
        trashDomainService.purge(trashItemId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void emptyTrash(String userId) {
        trashDomainService.emptyTrash(userId);
    }
}
