package com.njydsz.nextwiki.infra.repository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.infra.entity.TrashItemDO;
import com.njydsz.nextwiki.domain.repository.TrashItemRepository;
import com.njydsz.nextwiki.infra.mapper.TrashItemMapper;

/**
 * 回收站仓储实现
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TrashItemRepositoryImpl implements TrashItemRepository {

  private final TrashItemMapper trashItemMapper;

  /**
   * 插入回收站条目（文件/文件夹被删除并移入回收站时调用，记录原路径以便恢复）。
   *
   * @param TrashItemDO 待持久化的回收站实体（含 fileNodeId、originalPath、expireTime 等）
   * @return 已落库的回收站实体（含自增主键）
   */
  @Override
  public TrashItemDO save(TrashItemDO TrashItemDO) {
    trashItemMapper.insert(TrashItemDO);
    return TrashItemDO;
  }

  /**
   * 按主键查询回收站条目。
   *
   * @param id 回收站条目主键
   * @return 回收站实体；不存在则返回 null
   */
  @Override
  public TrashItemDO findById(String id) {
    return trashItemMapper.selectById(id);
  }

  /**
   * 按原文件节点 ID 查询其对应的回收站条目。
   *
   * @param fileNodeId 原文件节点 ID
   * @return 命中的回收站实体；不存在则返回 null
   */
  @Override
  public TrashItemDO findByFileNodeId(String fileNodeId) {
    return trashItemMapper.findByFileNodeId(fileNodeId);
  }

  /**
   * 查询某用户的活跃回收站条目列表（未过期、未彻底删除），用于回收站页面展示。
   *
   * @param userId 用户 ID
   * @return 活跃回收站条目列表
   */
  @Override
  public List<TrashItemDO> findActiveTrash(String userId) {
    return trashItemMapper.findActiveTrash(userId);
  }

  /**
   * 查询已过期的回收站条目（用于定时清理任务），limit 限制单次批处理量以避免长事务。
   *
   * @param limit 返回数量上限
   * @return 已过期待清理的回收站条目列表
   */
  @Override
  public List<TrashItemDO> findExpiredItems(int limit) {
    return trashItemMapper.findExpiredItems(limit);
  }

  /**
   * 乐观锁更新回收站条目；未携带 revision 时退化为普通更新，受影响行数为 0 抛出 {@link OptimisticLockingFailureException}，成功后
   * revision 自增 1。
   *
   * @param TrashItemDO 待更新的回收站实体（必须携带 id）
   */
  @Override
  public void update(TrashItemDO TrashItemDO) {
    if (TrashItemDO.getRevision() == null) {
      // 兜底：未携带 revision 时退化为普通更新，避免业务阻断
      trashItemMapper.updateById(TrashItemDO);
      return;
    }
    int affected = trashItemMapper.updateWithRevision(TrashItemDO);
    if (affected == 0) {
      throw new OptimisticLockingFailureException(
          "TrashItem 乐观锁更新失败，id=" + TrashItemDO.getId() + ", revision=" + TrashItemDO.getRevision());
    }
    TrashItemDO.setRevision(TrashItemDO.getRevision() + 1);
  }

  /**
   * 按主键物理删除回收站条目（彻底删除，触发物理文件清理）。
   *
   * @param id 回收站条目主键
   */
  @Override
  public void deleteById(String id) {
    trashItemMapper.deleteById(id);
  }

  /**
   * 统计某用户的活跃回收站条目数量（未过期、未彻底删除），用于回收站角标提示。
   *
   * @param userId 用户 ID
   * @return 活跃回收站条目数
   */
  @Override
  public int countActiveTrash(String userId) {
    return trashItemMapper.countActiveTrash(userId);
  }
}
