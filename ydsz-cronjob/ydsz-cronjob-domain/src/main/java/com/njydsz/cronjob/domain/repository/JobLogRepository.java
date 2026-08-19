package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * 任务执行日志 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobLogRepository {

  /**
   * 查询超时但未结束的 RUNNING 日志（P2-4）。
   */
  List<JobLogVO> findTimedOutLogs(LocalDateTime now, int limit);

  /**
   * 查询正在执行但已达到 SLA 80% 预警线的日志（P2-F2 SLA 预警）。
   */
  List<JobLogVO> findApproachingSlaLogs(LocalDateTime now, int limit);

  /**
   * 标记指定日志为超时。
   */
  int markTimeout(String id, LocalDateTime endTime, long durationMs, String errorMessage);

  /**
   * 查询慢任务执行日志。
   */
  List<JobLogVO> findSlowLogs(LocalDateTime since, int limit);

  /**
   * 标记指定日志为慢任务。
   */
  int markSlow(String logId, long slowThresholdMs);

  /**
   * P1-3: 查询指定节点上 RUNNING 状态的日志（故障转移用）。
   */
  List<JobLogVO> findRunningByNode(String nodeId);

  /**
   * P1-3: 标记指定节点上 RUNNING 日志为 FAILED（节点掉线故障转移）。
   */
  int markFailedByNodeOffline(String nodeId, LocalDateTime now);

  /**
   * P1-4: 查询所有 RUNNING 状态日志的执行节点 ID（去重）。
   */
  List<String> findRunningNodeIds();

  /**
   * P3-2: 统计指定任务在时间窗口内的执行次数和失败次数。
   */
  Map<String, Object> countByJobIdSince(String jobId, LocalDateTime since);

  /**
   * P3-2: 统计指定任务在时间窗口内的 P95 耗时。
   */
  Optional<Long> findDurationP95(String jobId, LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的执行次数和失败次数。
   */
  Map<String, Object> countSince(LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的 P95 耗时。
   */
  Optional<Long> findDurationP95Global(LocalDateTime since);

  /**
   * P2-2: 批量清理过期任务日志（硬删除，释放磁盘空间）。
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  /**
   * 统计指定状态且起始时间之后的日志数量。
   */
  long countByStatusAfter(String status, LocalDateTime startAfter);

  /**
   * 查询最近的失败日志列表。
   */
  List<JobLogVO> findRecentFailures(int limit);

  /**
   * 查询指定任务 KEY 的执行日志列表。
   */
  List<JobLogVO> findByJobKey(String jobKey, int limit);

  /**
   * 查询指定任务 KEY 的最新一条执行日志。
   */
  Optional<JobLogVO> findLatestByJobKey(String jobKey);

  /**
   * 统计指定时间范围内的执行日志数量（按 start_time）。
   */
  long countByTimeRange(LocalDateTime start, LocalDateTime end);

  /**
   * 新增执行日志。
   *
   * @param vo 执行日志 VO
   * @return 新日志 ID
   */
  String insert(JobLogVO vo);

  /**
   * 按 ID 更新执行日志。
   *
   * @param vo 执行日志 VO（必须含 id）
   * @return 受影响行数
   */
  int update(JobLogVO vo);
}
