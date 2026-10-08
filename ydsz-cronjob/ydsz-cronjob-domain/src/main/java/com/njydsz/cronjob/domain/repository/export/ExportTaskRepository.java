package com.njydsz.cronjob.domain.repository.export;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.entity.export.ExportTask;

/**
 * 异步导出任务仓储接口（YDIZ-DDD-006：接口定义在 domain/repository/）。
 *
 * @author ydsz-team
 * @since 26.10.13
 */
public interface ExportTaskRepository {

  /**
   * 保存或更新导出任务。
   *
   * @param task 任务实体
   * @return true=保存成功
   */
  boolean saveOrUpdate(ExportTask task);

  /**
   * 根据 ID 查询任务。
   *
   * @param id 任务 ID
   * @return 任务 Optional
   */
  Optional<ExportTask> findById(String id);

  /**
   * 乐观锁更新状态。
   *
   * @param task 任务实体（需携带 revision）
   * @return true=更新成功
   */
  boolean updateWithVersion(ExportTask task);

  /**
   * 查询用户的导出任务列表（按创建时间倒序）。
   *
   * @param tenantId 租户 ID
   * @param userId   用户 ID
   * @param limit    返回数量上限
   * @return 任务列表
   */
  List<ExportTask> listByUser(String tenantId, String userId, int limit);

  /**
   * 查询超时的 PROCESSING 任务（用于 Worker 故障恢复）。
   *
   * @param threshold 超时阈值
   * @return 超时任务列表
   */
  List<ExportTask> findTimeoutProcessing(LocalDateTime threshold);

  /**
   * 删除过期的已完成任务。
   *
   * @param expireBefore 过期时间阈值
   * @return 删除条数
   */
  int deleteExpired(java.time.LocalDateTime expireBefore);
}
