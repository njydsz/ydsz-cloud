package com.njydsz.nextwiki.server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.event.FileOperatedEvent;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.nextwiki.domain.service.TrashDomainService;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;

/**
 * 回收站应用服务。
 *
 * <p>编排回收站操作：数据访问（repository）+ 领域逻辑（domain service）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashApplicationService {

  /** 回收站领域服务（domain 层，不依赖 Spring） */
  private final TrashDomainService trashDomainService;

  private final TrashItemRepository trashItemRepository;

  private final FileNodeRepository fileNodeRepository;

  /** Spring 事件发布器（用于发布领域服务返回的事件） */
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 查询用户回收站列表。
   *
   * @param userId 用户 ID
   * @return 回收站项目列表（可能为空，非 {@code null}）
   * @complexity O(1)（一次按用户查询）
   * @note 只读，无事务边界
   */
  public List<TrashItemVO> listTrash(String userId) {
    return trashItemRepository.findActiveTrash(userId);
  }

  /**
   * 从回收站恢复单个文件到原位置（逻辑恢复，文件实体转回可用状态）。
   *
   * @param trashItemId 回收站项目 ID
   * @param userId 操作者 ID（需为该项目所有者）
   * @throws 由 {@link TrashDomainService} 在项目不存在/状态非法时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次恢复写入）
   * @note 恢复后文件重新出现在原目录；若原目录已不存在由领域服务决定处理
   */
  @Transactional(rollbackFor = Exception.class)
  public void restore(String trashItemId, String userId) {
    TrashItemVO trashItem = trashItemRepository.findById(trashItemId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.TRASH_NOT_FOUND)
            .data("trashItemId", trashItemId));

    FileNodeVO node = fileNodeRepository.findById(trashItem.getFileNodeId()).orElse(null);

    TrashItemDTO trashDTO = trashItemToDTO(trashItem);
    // DDD 合规：domain 层返回事件，由应用层发布
    FileOperatedEvent restoreEvent = trashDomainService.restore(trashDTO, node, userId);
    eventPublisher.publishEvent(restoreEvent);

    if (node != null) {
      fileNodeRepository.restore(trashItem.getFileNodeId());
    }
    trashItemRepository.update(trashDTO);
  }

  /**
   * 批量从回收站恢复文件到原位置（逐条恢复，允许部分失败由底层处理）。
   *
   * @param trashItemIds 回收站项目 ID 列表
   * @param userId 操作者 ID
   * @throws 由 {@link TrashDomainService} 在参数非法时抛出的业务异常（单条失败策略见领域服务）
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(trashItemIds.size())
   * @note 委托 {@link TrashDomainService} 实现
   */
  @Transactional(rollbackFor = Exception.class)
  public void batchRestore(List<String> trashItemIds, String userId) {
    for (String id : trashItemIds) {
      try {
        restore(id, userId);
      } catch (Exception e) {
        log.error("[TrashApplicationService] 批量恢复失败: trashItemId={}", id, e);
      }
    }
  }

  /**
   * 永久删除回收站中的单个文件（不可恢复，通常同时清理物理存储对象）。
   *
   * @param trashItemId 回收站项目 ID
   * @param userId 操作者 ID
   * @throws 由 {@link TrashDomainService} 在项目不存在时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次物理删除 + 一次记录移除）
   * @note 操作不可逆，调用前需前端二次确认
   */
  @Transactional(rollbackFor = Exception.class)
  public void purge(String trashItemId, String userId) {
    TrashItemVO trashItem = trashItemRepository.findById(trashItemId)
        .orElseThrow(() -> BusinessException.of(NextwikiExceptionCode.TRASH_NOT_FOUND)
            .data("trashItemId", trashItemId));

    TrashItemDTO trashDTO = trashItemToDTO(trashItem);
    trashDomainService.purge(trashDTO, userId);

    fileNodeRepository.physicalDelete(trashItem.getFileNodeId());
    trashItemRepository.update(trashDTO);
  }

  /**
   * 清空用户回收站（永久删除全部回收站文件，不可逆）。
   *
   * @param userId 操作者 ID
   * @throws 由 {@link TrashDomainService} 在无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(n)（n 为回收站文件数）
   * @note 操作不可逆；委托 {@link TrashDomainService} 实现
   */
  @Transactional(rollbackFor = Exception.class)
  public void emptyTrash(String userId) {
    List<TrashItemVO> items = trashItemRepository.findActiveTrash(userId);
    for (TrashItemVO item : items) {
      try {
        TrashItemDTO trashDTO = trashItemToDTO(item);
        trashDomainService.purge(trashDTO, userId);
        fileNodeRepository.physicalDelete(item.getFileNodeId());
        trashItemRepository.update(trashDTO);
      } catch (Exception e) {
        log.error("[TrashApplicationService] 清空回收站失败: trashItemId={}", item.getId(), e);
      }
    }
    log.info("[TrashApplicationService] 清空回收站: userId={}, count={}", userId, items.size());
  }

  /**
   * 回收站容量统计。
   *
   * @param userId 用户 ID
   * @return 活跃条目数量
   * @complexity O(1)（一次聚合查询）
   * @note 只读，无事务边界
   */
  public int countActiveTrash(String userId) {
    return trashItemRepository.countActiveTrash(userId);
  }

  private TrashItemDTO trashItemToDTO(TrashItemVO vo) {
    TrashItemDTO dto = new TrashItemDTO();
    dto.setId(vo.getId());
    dto.setFileNodeId(vo.getFileNodeId());
    dto.setOriginalName(vo.getOriginalName());
    dto.setOriginalPath(vo.getOriginalPath());
    dto.setOriginalParentId(vo.getOriginalParentId());
    dto.setNodeType(vo.getNodeType());
    dto.setSize(vo.getSize());
    dto.setDeletedTime(vo.getDeletedTime());
    dto.setPurgeTime(vo.getPurgeTime());
    dto.setStatus(vo.getStatus());
    return dto;
  }
}
