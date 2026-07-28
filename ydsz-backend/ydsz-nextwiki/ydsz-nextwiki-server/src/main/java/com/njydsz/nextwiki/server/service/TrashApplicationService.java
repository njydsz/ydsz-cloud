package com.njydsz.nextwiki.server.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.service.TrashDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 回收站应用服务。
 * <p>文件删除/恢复/彻底删除。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class TrashApplicationService {

    /** 回收站领域服务 */
    private final TrashDomainService trashDomainService;

    /**
     * 查询用户回收站文件列表。
     *
     * @param userId 用户 ID
     * @return 回收站项目列表
     */
    public List<TrashItem> listTrash(String userId) {
        return trashDomainService.listTrash(userId);
    }

    /**
     * 从回收站恢复单个文件到原位置。
     *
     * @param trashItemId 回收站项目 ID
     * @param userId      操作者 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void restore(String trashItemId, String userId) {
        trashDomainService.restore(trashItemId, userId);
    }

    /**
     * 批量从回收站恢复文件到原位置。
     *
     * @param trashItemIds 回收站项目 ID 列表
     * @param userId       操作者 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchRestore(List<String> trashItemIds, String userId) {
        trashDomainService.batchRestore(trashItemIds, userId);
    }

    /**
     * 永久删除回收站中的单个文件（不可恢复）。
     *
     * @param trashItemId 回收站项目 ID
     * @param userId      操作者 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void purge(String trashItemId, String userId) {
        trashDomainService.purge(trashItemId, userId);
    }

    /**
     * 清空用户回收站（永久删除所有回收站文件）。
     *
     * @param userId 操作者 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void emptyTrash(String userId) {
        trashDomainService.emptyTrash(userId);
    }
}
