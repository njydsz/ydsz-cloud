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
   *
   * @param now 参数说明
   * @param limit 参数说明
   * @return 返回值说明
   */
  List<JobLogVO> findTimedOutLogs(LocalDateTime now, int limit);

  /**
   * 查询正在执行但已达到 SLA 80% 预警线的日志（P2-F2 SLA 预警）。
   *
   * @param now 参数说明
   * @param limit 参数说明
   * @return 返回值说明
   */
  List<JobLogVO> findApproachingSlaLogs(LocalDateTime now, int limit);

  /**
   * 标记指定日志为超时。
   *
   * @param id 参数说明
   * @param endTime 参数说明
   * @param durationMs 参数说明
   * @param errorMessage 参数说明
   * @return 返回值说明
   */
  int markTimeout(String id, LocalDateTime endTime, long durationMs, String errorMessage);

  /**
   * 查询慢任务执行日志。
   *
   * @param since 参数说明
   * @param limit 参数说明
   * @return 返回值说明
   */
  List<JobLogVO> findSlowLogs(LocalDateTime since, int limit);

  /**
   * 标记指定日志为慢任务。
   *
   * @param logId 参数说明
   * @param slowThresholdMs 参数说明
   * @return 返回值说明
   */
  int markSlow(String logId, long slowThresholdMs);

  /**
   * P1-3: 查询指定节点上 RUNNING 状态的日志（故障转移用）。
   *
   * @param nodeId 参数说明
   * @return 返回值说明
   */
  List<JobLogVO> findRunningByNode(String nodeId);

  /**
   * P1-3: 标记指定节点上 RUNNING 日志为 FAILED（节点掉线故障转移）。
   *
   * @param nodeId 参数说明
   * @param now 参数说明
   * @return 返回值说明
   */
  int markFailedByNodeOffline(String nodeId, LocalDateTime now);

  /**
   * P1-4: 查询所有 RUNNING 状态日志的执行节点 ID（去重）。
   *
   * @return 返回值说明
   */
  List<String> findRunningNodeIds();

  /**
   * 查询 RUNNING 状态超过指定起始时间阈值的卡死任务。
   *
   * @param threshold 起始时间阈值（含）
   * @param limit 最多返回条数
   * @return 卡死任务日志 VO 列表
   */
  List<JobLogVO> findStuckTasks(LocalDateTime threshold, int limit);

  /**
   * P3-2: 统计指定任务在时间窗口内的执行次数和失败次数。
   *
   * @param jobId 任务 ID
   * @param since 时间窗口起点（含）
   * @return 统计结果（执行次数/失败次数）
   */
  Map<String, Object> countByJobIdSince(String jobId, LocalDateTime since);

  /**
   * P3-2: 统计指定任务在时间窗口内的 P95 耗时。
   *
   * @param jobId 参数说明
   * @param since 参数说明
   * @return 返回值说明
   */
  Optional<Long> findDurationP95(String jobId, LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的执行次数和失败次数。
   *
   * @param since 时间窗口起点（含）
   * @return 统计结果（执行次数/失败次数）
   */
  Map<String, Object> countSince(LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的 P95 耗时。
   *
   * @param since 参数说明
   * @return 返回值说明
   */
  Optional<Long> findDurationP95Global(LocalDateTime since);

  /**
   * P2-2: 批量清理过期任务日志（硬删除，释放磁盘空间）。
   *
   * @param before 参数说明
   * @param limit 参数说明
   * @return 返回值说明
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  /**
   * 统计指定状态且起始时间之后的日志数量。
   *
   * @param status 参数说明
   * @param startAfter 参数说明
   * @return 返回值说明
   */
  long countByStatusAfter(String status, LocalDateTime startAfter);

  /**
   * 查询最近的失败日志列表。
   *
   * @param limit 参数说明
   * @return 返回值说明
   */
  List<JobLogVO> findRecentFailures(int limit);

  /**
   * 查询指定任务 KEY 的执行日志列表。
   *
   * @param jobKey 参数说明
   * @param limit 参数说明
   * @return 返回值说明
   */
  List<JobLogVO> findByJobKey(String jobKey, int limit);

  /**
   * 查询指定任务 KEY 的最新一条执行日志。
   *
   * @param jobKey 参数说明
   * @return 返回值说明
   */
  Optional<JobLogVO> findLatestByJobKey(String jobKey);

  /**
   * 统计指定时间范围内的执行日志数量（按 start_time）。
   *
   * @param start 参数说明
   * @param end 参数说明
   * @return 返回值说明
   */
  long countByTimeRange(LocalDateTime start, LocalDateTime end);

  /**
   * 按任务 KEY 和状态分页查询执行日志。
   *
   * @param jobKey 任务 KEY（可为空表示不限）
   * @param status 状态过滤（可为空表示不限）
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @return 分页结果（records=VO列表, total=总条数）
   */
  JobRepository.PageResult<JobLogVO> pageByJobKeyAndStatus(String jobKey, String status, int page, int size);

  /**
   * 按 ID 查询执行日志。
   *
   * @param id 日志 ID
   * @return 日志 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobLogVO> findById(String id);

  /**
   * 按 ID 更新执行日志。
   *
   * @param vo 执行日志 VO（必须含 id）
   * @return 受影响行数
   */
  int updateById(JobLogVO vo);

  /**
   * 统计指定条件的日志数量。
   *
   * @param jobId 任务 ID（可为空）
   * @param status 状态（可为空）
   * @return 日志数量
   */
  long countByJobIdAndStatus(String jobId, String status);

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

  /**
   * 按任务 ID 和时间点查询执行日志（诊断用）。
   *
   * @param jobId 任务 ID
   * @param since 时间起点（含）
   * @return 日志 VO 列表（按创建时间倒序）
   */
  List<JobLogVO> findByJobIdSince(String jobId, LocalDateTime since);

  /**
   * 按任务 KEY 查询最新一条 RUNNING 状态的执行日志。
   *
   * @param jobKey 任务 KEY
   * @return RUNNING 日志；无记录时返回 empty
   */
  Optional<JobLogVO> findLatestByJobKeyAndRunning(String jobKey);
}
