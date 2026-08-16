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

  /**
   * 保存回收站条目（新增或更新）。
   *
   * @param trashItem 待持久化的回收站条目（含原节点信息、删除/清理时间等）
   * @return 持久化后的回收站条目（回填主键）
   */
  TrashItem save(TrashItem trashItem);

  /**
   * 按 ID 查询回收站条目。
   *
   * @param id 回收站条目 ID
   * @return 回收站条目，不存在时返回 null
   */
  TrashItem findById(String id);

  /**
   * 按原文件节点 ID 查询对应的回收站条目。
   *
   * @param fileNodeId 原文件节点 ID
   * @return 回收站条目，不存在时返回 null
   */
  TrashItem findByFileNodeId(String fileNodeId);

  /**
   * 查询某用户的全部未清理回收站条目（用于回收站列表）。
   *
   * @param userId 用户 ID
   * @return 活跃回收站条目列表，无记录时返回空列表
   */
  List<TrashItem> findActiveTrash(String userId);

  /**
   * 分页查询已超过保留期、待永久清理的回收站条目（供定时任务批量清理）。
   *
   * @param limit 单次最多返回条数（避免一次性加载过多）
   * @return 待清理条目列表，无记录时返回空列表
   */
  List<TrashItem> findExpiredItems(int limit);

  /**
   * 更新回收站条目（如状态迁移为 restored/purged）。
   *
   * @param trashItem 待更新的条目（需含主键）
   */
  void update(TrashItem trashItem);

  /**
   * 物理删除回收站条目（彻底清理时调用）。
   *
   * @param id 回收站条目 ID
   */
  void deleteById(String id);

  /**
   * 统计某用户的活跃回收站条目数量。
   *
   * @param userId 用户 ID
   * @return 活跃条目数量
   */
  int countActiveTrash(String userId);
}
