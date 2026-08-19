package com.njydsz.nextwiki.server.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiEnums.TrashStatus;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

/**
 * NextWiki 回收站领域服务。
 *
 * <p>负责回收站条目的纯领域逻辑：状态校验、状态迁移、事件发布。 不直接依赖 Repository，所有数据访问由 server 层编排。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class TrashDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  private final ApplicationEventPublisher eventPublisher;

  /** 默认保留天数 */
  private static final int RETENTION_DAYS = 30;

  /**
   * 将文件移入回收站：构造回收站条目（纯领域对象创建，不涉及持久化）。
   *
   * <p>server 层负责将返回的 {@link TrashItemDO} 通过 repository 持久化。
   *
   * @param node 待删除的文件节点
   * @param userId 操作人 ID
   * @return 新建的回收站条目（未持久化）
   */
  public TrashItemDTO moveToTrash(FileNodeVO node, String userId) {
    LocalDateTime now = LocalDateTime.now();
    TrashItemDTO trashItem =
        TrashItemDTO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .fileNodeId(node.getId())
            .originalName(node.getName())
            .originalPath(node.getPath())
            .originalParentId(node.getParentId())
            .nodeType(node.getNodeType())
            .size(node.getSize())
            .deletedTime(now)
            .purgeTime(now.plusDays(RETENTION_DAYS))
            .status(TrashStatus.IN_TRASH.getCode())
            .build();

    trashItem.setCreatedBy(userId);
    trashItem.setCreatedAt(now);
    trashItem.setUpdatedBy(userId);
    trashItem.setUpdatedAt(now);

    return trashItem;
  }

  /**
   * 从回收站恢复：校验状态合法性、执行状态迁移、发布恢复事件。
   *
   * <p>server 层负责通过 repository 查询 {@link TrashItemDO} 与 {@link FileNodeVO} 并传入本方法， 再通过 repository 持久化状态变更。
   *
   * @param trashItem 待恢复的回收站条目
   * @param node 对应的文件节点（可能为 {@code null}，表示原节点已不存在）
   * @param userId 操作人 ID
   * @throws BusinessException 状态不允许恢复时抛出 {@link NextwikiExceptionCode#TRASH_INVALID_STATUS}
   */
  public void restore(TrashItemDTO trashItem, FileNodeVO node, String userId) {
    TrashStatus currentStatus = TrashStatus.fromCode(trashItem.getStatus());
    if (currentStatus == null || !currentStatus.canTransitTo(TrashStatus.RESTORED)) {
      throw BusinessException.of(NextwikiExceptionCode.TRASH_INVALID_STATUS)
          .data("trashItemId", trashItem.getId())
          .data("status", trashItem.getStatus());
    }

    trashItem.setStatus(TrashStatus.RESTORED.getCode());
    trashItem.setUpdatedBy(userId);
    trashItem.setUpdatedAt(LocalDateTime.now());

    eventPublisher.publishEvent(
        FileOperatedEvent.builder()
            .operation(FileOperatedEvent.OP_RESTORE)
            .fileNodeId(trashItem.getFileNodeId())
            .fileName(trashItem.getOriginalName())
            .nodeType(trashItem.getNodeType())
            .operatorId(userId)
            .operatedAt(LocalDateTime.now())
            .build());

    log.info(
        "[TrashDomainService] 恢复文件: trashItemId={}, fileNodeId={}",
        trashItem.getId(),
        trashItem.getFileNodeId());
  }

  /**
   * 永久删除：执行状态迁移。
   *
   * <p>server 层负责通过 repository 查询 {@link TrashItemDO} 并传入本方法， 再通过 repository 持久化状态变更与物理删除文件节点。
   *
   * @param trashItem 待永久删除的回收站条目
   * @param userId 操作人 ID
   */
  public void purge(TrashItemDTO trashItem, String userId) {
    trashItem.setStatus(TrashStatus.PURGED.getCode());
    trashItem.setUpdatedBy(userId);
    trashItem.setUpdatedAt(LocalDateTime.now());

    log.info(
        "[TrashDomainService] 永久删除: trashItemId={}, fileNodeId={}",
        trashItem.getId(),
        trashItem.getFileNodeId());
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
  public int cleanupExpiredItems(List<TrashItemDTO> expiredItems, String userId) {
    int cleaned = 0;
    for (TrashItemDTO item : expiredItems) {
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
