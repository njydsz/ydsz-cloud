package com.njydsz.nextwiki.domain.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.infra.entity.FileNodeDO;
import com.njydsz.nextwiki.infra.entity.TrashItemDO;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.TrashStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;

/**
 * NextWiki 回收站领域服务。
 *
 * <p>负责回收站条目的纯领域逻辑：状态校验、状态迁移、事件发布。 不直接依赖 Repository，所有数据访问由 server 层编排。
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

  private final ApplicationEventPublisher eventPublisher;

  private static final int RETENTION_DAYS = TrashItemDO.DEFAULT_RETENTION_DAYS;

  /**
   * 将文件移入回收站：构造回收站条目（纯领域对象创建，不涉及持久化）。
   *
   * <p>server 层负责将返回的 {@link TrashItemDO} 通过 repository 持久化。
   *
   * @param FileNodeDO 待删除的文件节点
   * @param userId 操作人 ID
   * @return 新建的回收站条目（未持久化）
   */
  public TrashItemDO moveToTrash(FileNodeDO FileNodeDO, String userId) {
    LocalDateTime now = LocalDateTime.now();
    TrashItemDO TrashItemDO =
        TrashItemDO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
            .fileNodeId(FileNodeDO.getId())
            .originalName(FileNodeDO.getName())
            .originalPath(FileNodeDO.getPath())
            .originalParentId(FileNodeDO.getParentId())
            .nodeType(FileNodeDO.getNodeType())
            .size(FileNodeDO.getSize())
            .deletedTime(now)
            .purgeTime(now.plusDays(RETENTION_DAYS))
            .status(TrashStatus.IN_TRASH.getCode())
            .revision(0)
            .deleted(0)
            .build();

    TrashItemDO.setCreatedBy(userId);
    TrashItemDO.setCreatedAt(now);
    TrashItemDO.setUpdatedBy(userId);
    TrashItemDO.setUpdatedAt(now);

    return TrashItemDO;
  }

  /**
   * 从回收站恢复：校验状态合法性、执行状态迁移、发布恢复事件。
   *
   * <p>server 层负责通过 repository 查询 {@link TrashItemDO} 与 {@link FileNodeDO} 并传入本方法， 再通过 repository 持久化状态变更。
   *
   * @param TrashItemDO 待恢复的回收站条目
   * @param FileNodeDO 对应的文件节点（可能为 {@code null}，表示原节点已不存在）
   * @param userId 操作人 ID
   * @throws BusinessException 状态不允许恢复时抛出 {@link NextwikiExceptionCode#TRASH_INVALID_STATUS}
   */
  public void restore(TrashItemDO TrashItemDO, FileNodeDO FileNodeDO, String userId) {
    TrashStatus currentStatus = TrashStatus.fromCode(TrashItemDO.getStatus());
    if (currentStatus == null || !currentStatus.canTransitTo(TrashStatus.RESTORED)) {
      throw BusinessException.of(NextwikiExceptionCode.TRASH_INVALID_STATUS)
          .data("trashItemId", TrashItemDO.getId())
          .data("status", TrashItemDO.getStatus());
    }

    TrashItemDO.setStatus(TrashStatus.RESTORED.getCode());
    TrashItemDO.setUpdatedBy(userId);
    TrashItemDO.setUpdatedAt(LocalDateTime.now());

    eventPublisher.publishEvent(
        FileOperatedEvent.builder()
            .operation(FileOperatedEvent.OP_RESTORE)
            .fileNodeId(TrashItemDO.getFileNodeId())
            .fileName(TrashItemDO.getOriginalName())
            .nodeType(TrashItemDO.getNodeType())
            .operatorId(userId)
            .operatedAt(LocalDateTime.now())
            .build());

    log.info(
        "[TrashDomainService] 恢复文件: trashItemId={}, fileNodeId={}",
        TrashItemDO.getId(),
        TrashItemDO.getFileNodeId());
  }

  /**
   * 永久删除：执行状态迁移。
   *
   * <p>server 层负责通过 repository 查询 {@link TrashItemDO} 并传入本方法， 再通过 repository 持久化状态变更与物理删除文件节点。
   *
   * @param TrashItemDO 待永久删除的回收站条目
   * @param userId 操作人 ID
   */
  public void purge(TrashItemDO TrashItemDO, String userId) {
    TrashItemDO.setStatus(TrashStatus.PURGED.getCode());
    TrashItemDO.setUpdatedBy(userId);
    TrashItemDO.setUpdatedAt(LocalDateTime.now());

    log.info(
        "[TrashDomainService] 永久删除: trashItemId={}, fileNodeId={}",
        TrashItemDO.getId(),
        TrashItemDO.getFileNodeId());
  }

  /**
   * 批量清理过期条目：对已过保留期的回收站条目执行状态迁移（供定时任务调用）。
   *
   * <p>server 层负责通过 repository 查询过期条目并传入本方法， 再通过 repository 持久化状态变更。
   *
   * @param expiredItems 已过保留期、待清理的回收站条目列表（非 {@code null}）
   * @param userId 操作人 ID（通常为系统用户）
   * @return 成功清理的条目数
   */
  public int cleanupExpiredItems(List<TrashItemDO> expiredItems, String userId) {
    int cleaned = 0;
    for (TrashItemDO item : expiredItems) {
      try {
        purge(item, userId);
        cleaned++;
      } catch (Exception e) {
        log.error("[TrashDomainService] 自动清理失败: trashItemId={}", item.getId(), e);
      }
    }
    log.info("[TrashDomainService] 自动清理过期条目: count={}", cleaned);
    return cleaned;
  }
}
