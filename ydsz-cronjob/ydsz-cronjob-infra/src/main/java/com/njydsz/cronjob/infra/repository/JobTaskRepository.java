package com.njydsz.cronjob.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.infra.entity.job.JobTask;

/**
 * MapReduce 子任务 Repository。
 *
 * <p>封装 {@code ydsz_job_task} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobTaskRepository {

  /**
   * 根据日志 ID 查询子任务列表。
   *
   * @param logId 日志 ID
   * @return 子任务列表
   */
  List<JobTask> selectByLogId(String logId);

  /**
   * 根据日志 ID 查询 PENDING 状态的子任务列表。
   *
   * @param logId 日志 ID
   * @return PENDING 子任务列表
   */
  List<JobTask> selectPendingByLogId(String logId);

  /**
   * 根据日志 ID 和状态统计子任务数量。
   *
   * @param logId 日志 ID
   * @param status 状态
   * @return 子任务数量
   */
  int countByLogIdAndStatus(String logId, String status);

  /**
   * 更新子任务状态。
   *
   * @param taskId 子任务 ID
   * @param status 状态
   * @param resultJson 结果 JSON
   * @param errorMessage 错误信息
   * @param updatedAt 更新时间
   * @return 受影响行数
   */
  int updateStatus(
      String taskId,
      String status,
      String resultJson,
      String errorMessage,
      LocalDateTime updatedAt);

  /**
   * 更新子任务执行节点 ID。
   *
   * @param taskId 子任务 ID
   * @param nodeId 节点 ID
   * @param updatedAt 更新时间
   * @return 受影响行数
   */
  int updateExecNodeId(String taskId, String nodeId, LocalDateTime updatedAt);

  /**
   * 清理过期子任务记录。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);
}
