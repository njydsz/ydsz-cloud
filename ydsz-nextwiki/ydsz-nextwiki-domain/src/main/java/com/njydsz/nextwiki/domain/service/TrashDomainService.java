package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.TrashStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * NextWiki 回收站领域服务。
 * <p>删除/恢复/彻底删除文件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */


@Slf4j
@Service
@RequiredArgsConstructor
public class TrashDomainService {

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final TrashItemRepository trashItemRepository;
    private final FileNodeRepository fileNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final int RETENTION_DAYS = TrashItem.DEFAULT_RETENTION_DAYS;

    /**
     * 将文件移入回收站
     */
    public TrashItem moveToTrash(FileNode fileNode, String userId) {
        LocalDateTime now = LocalDateTime.now();
        TrashItem trashItem = TrashItem.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
                .fileNodeId(fileNode.getId())
                .originalName(fileNode.getName())
                .originalPath(fileNode.getPath())
                .originalParentId(fileNode.getParentId())
                .nodeType(fileNode.getNodeType())
                .size(fileNode.getSize())
                .deletedTime(now)
                .purgeTime(now.plusDays(RETENTION_DAYS))
                .status(TrashStatus.IN_TRASH.getCode())
                .revision(0)
                .deleted(0)
                .build();

        trashItem.setCreatedBy(userId);
        trashItem.setCreatedAt(now);
        trashItem.setUpdatedBy(userId);
        trashItem.setUpdatedAt(now);

        return trashItemRepository.save(trashItem);
    }

    /**
     * 从回收站恢复
     */
    @Transactional(rollbackFor = Exception.class)
    public FileNode restore(String trashItemId, String userId) {
        TrashItem trashItem = trashItemRepository.findById(trashItemId);
        if (trashItem == null) {
            throw BusinessException.of(NextwikiExceptionCode.TRASH_NOT_FOUND).data("trashItemId", trashItemId);
        }

        TrashStatus currentStatus = TrashStatus.fromCode(trashItem.getStatus());
        if (currentStatus == null || !currentStatus.canTransitTo(TrashStatus.RESTORED)) {
            throw BusinessException.of(NextwikiExceptionCode.TRASH_INVALID_STATUS)
                    .data("trashItemId", trashItemId)
                    .data("status", trashItem.getStatus());
        }

        fileNodeRepository.restore(trashItem.getFileNodeId());

        trashItem.setStatus(TrashStatus.RESTORED.getCode());
        trashItem.setUpdatedBy(userId);
        trashItemRepository.update(trashItem);

        FileNode restored = fileNodeRepository.findById(trashItem.getFileNodeId());

        eventPublisher.publishEvent(FileOperatedEvent.builder()
                .operation(FileOperatedEvent.OP_RESTORE)
                .fileNodeId(trashItem.getFileNodeId())
                .fileName(trashItem.getOriginalName())
                .nodeType(trashItem.getNodeType())
                .operatorId(userId)
                .operatedAt(LocalDateTime.now())
                .build());

        log.info("[TrashDomainService] 恢复文件: trashItemId={}, fileNodeId={}", trashItemId, trashItem.getFileNodeId());
        return restored;
    }

    /**
     * 批量恢复
     */
    public void batchRestore(List<String> trashItemIds, String userId) {
        for (String id : trashItemIds) {
            try {
                restore(id, userId);
            } catch (Exception e) {
                log.error("[TrashDomainService] 批量恢复失败: trashItemId={}", id, e);
            }
        }
    }

    /**
     * 永久删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void purge(String trashItemId, String userId) {
        TrashItem trashItem = trashItemRepository.findById(trashItemId);
        if (trashItem == null) {
            throw BusinessException.of(NextwikiExceptionCode.TRASH_NOT_FOUND).data("trashItemId", trashItemId);
        }

        fileNodeRepository.physicalDelete(trashItem.getFileNodeId());

        trashItem.setStatus(TrashStatus.PURGED.getCode());
        trashItem.setUpdatedBy(userId);
        trashItemRepository.update(trashItem);

        log.info("[TrashDomainService] 永久删除: trashItemId={}, fileNodeId={}", trashItemId, trashItem.getFileNodeId());
    }

    /**
     * 清空回收站
     */
    @Transactional(rollbackFor = Exception.class)
    public void emptyTrash(String userId) {
        List<TrashItem> items = trashItemRepository.findActiveTrash(userId);
        for (TrashItem item : items) {
            try {
                purge(item.getId(), userId);
            } catch (Exception e) {
                log.error("[TrashDomainService] 清空回收站失败: trashItemId={}", item.getId(), e);
            }
        }
        log.info("[TrashDomainService] 清空回收站: userId={}, count={}", userId, items.size());
    }

    /**
     * 查询回收站列表
     */
    public List<TrashItem> listTrash(String userId) {
        return trashItemRepository.findActiveTrash(userId);
    }

    /**
     * 自动清理过期条目（定时任务调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredItems() {
        List<TrashItem> expired = trashItemRepository.findExpiredItems(100);
        int cleaned = 0;
        for (TrashItem item : expired) {
            try {
                purge(item.getId(), SystemConstants.SYSTEM_USER_ID);
                cleaned++;
            } catch (Exception e) {
                log.error("[TrashDomainService] 自动清理失败: trashItemId={}", item.getId(), e);
            }
        }
        log.info("[TrashDomainService] 自动清理过期条目: count={}", cleaned);
        return cleaned;
    }

    /**
     * 回收站容量统计
     */
    public int countActiveTrash(String userId) {
        return trashItemRepository.countActiveTrash(userId);
    }
}
