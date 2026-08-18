package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobTaskVO;

/**
 * MapReduce 子任务 Repository（domain 层契约）。
 *
 * <p>定义 MapReduce 子任务的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobTaskVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobTaskRepository {

  /**
   * 根据日志 ID 查询子任务列表。
   *
   * @param logId 日志 ID
   * @return 子任务 VO 列表
   */
  List<JobTaskVO> findByLogId(String logId);

  /**
   * 根据日志 ID 查询 PENDING 状态的子任务列表。
   *
   * @param logId 日志 ID
   * @return PENDING 子任务 VO 列表
   */
  List<JobTaskVO> findPendingByLogId(String logId);

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
