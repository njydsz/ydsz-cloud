package com.njydsz.nextwiki.server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.service.TrashDomainService;

/**
 * 回收站应用服务。
 *
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
   * 查询用户回收站列表。
   *
   * @param userId 用户 ID
   * @return 回收站项目列表 {@link TrashItem}（可能为空，非 {@code null}）
   * @complexity O(1)（一次按用户查询）
   * @note 只读，无事务边界
   */
  public List<TrashItem> listTrash(String userId) {
    return trashDomainService.listTrash(userId);
  }

  /**
   * 从回收站恢复单个文件到原位置（逻辑恢复，文件实体转回可用状态）。
   *
   * @param trashItemId 回收站项目 ID
   * @param userId 操作者 ID（需为该项目所有者）
   * @return 无返回值
   * @throws 由 {@link TrashDomainService} 在项目不存在/无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次恢复写入）
   * @note 恢复后文件重新出现在原目录；若原目录已不存在由领域服务决定处理
   */
  @Transactional(rollbackFor = Exception.class)
  public void restore(String trashItemId, String userId) {
    trashDomainService.restore(trashItemId, userId);
  }

  /**
   * 批量从回收站恢复文件到原位置（逐条恢复，允许部分失败由底层处理）。
   *
   * @param trashItemIds 回收站项目 ID 列表
   * @param userId 操作者 ID
   * @return 无返回值
   * @throws 由 {@link TrashDomainService} 在参数非法时抛出的业务异常（单条失败策略见领域服务）
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(trashItemIds.size())
   * @note 委托 {@link TrashDomainService} 实现
   */
  @Transactional(rollbackFor = Exception.class)
  public void batchRestore(List<String> trashItemIds, String userId) {
    trashDomainService.batchRestore(trashItemIds, userId);
  }

  /**
   * 永久删除回收站中的单个文件（不可恢复，通常同时清理物理存储对象）。
   *
   * @param trashItemId 回收站项目 ID
   * @param userId 操作者 ID
   * @return 无返回值
   * @throws 由 {@link TrashDomainService} 在项目不存在/无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次物理删除 + 一次记录移除）
   * @note 操作不可逆，调用前需前端二次确认
   */
  @Transactional(rollbackFor = Exception.class)
  public void purge(String trashItemId, String userId) {
    trashDomainService.purge(trashItemId, userId);
  }

  /**
   * 清空用户回收站（永久删除全部回收站文件，不可逆）。
   *
   * @param userId 操作者 ID
   * @return 无返回值
   * @throws 由 {@link TrashDomainService} 在无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(n)（n 为回收站文件数）
   * @note 操作不可逆；委托 {@link TrashDomainService} 实现
   */
  @Transactional(rollbackFor = Exception.class)
  public void emptyTrash(String userId) {
    trashDomainService.emptyTrash(userId);
  }
}
