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
 * @since 26.09.01
 */
public interface JobLogRepository {

  /**
   * 查询超时但未结束的 RUNNING 日志（P2-4）。
   *
   * @param now 当前时间（用于判断是否超时）
   * @param limit 最多返回条数
   * @return 已超时的 RUNNING 日志 VO 列表（按开始时间升序）
   */
  List<JobLogVO> findTimedOutLogs(LocalDateTime now, int limit);

  /**
   * 查询正在执行但已达到 SLA 80% 预警线的日志（P2-F2 SLA 预警）。
   *
   * @param now 当前时间（用于判断是否达到 SLA 80% 阈值）
   * @param limit 最多返回条数
   * @return 已达到 SLA 80% 预警线但未超时的 RUNNING 日志 VO 列表
   */
  List<JobLogVO> findApproachingSlaLogs(LocalDateTime now, int limit);

  /**
   * 标记指定日志为超时。
   *
   * @param id 执行日志 ID
   * @param endTime 结束时间
   * @param durationMs 执行耗时（毫秒）
   * @param errorMessage 超时错误信息
   * @return 受影响行数（0 表示非 RUNNING 状态）
   */
  int markTimeout(String id, LocalDateTime endTime, long durationMs, String errorMessage);

  /**
   * 查询慢任务执行日志。
   *
   * @param since 时间窗口起点（仅扫描此时间之后的日志）
   * @param limit 最多返回条数
   * @return 慢任务执行日志 VO 列表（耗时超过阈值），按耗时降序
   */
  List<JobLogVO> findSlowLogs(LocalDateTime since, int limit);

  /**
   * 标记指定日志为慢任务。
   *
   * @param logId 执行日志 ID
   * @param slowThresholdMs 慢任务阈值（毫秒），快照存储
   * @return 受影响行数（0 表示已标记或是非 RUNNING 状态）
   */
  int markSlow(String logId, long slowThresholdMs);

  /**
   * P1-3: 查询指定节点上 RUNNING 状态的日志（故障转移用）。
   *
   * @param nodeId 执行节点 ID
   * @return 该节点上 RUNNING 状态的日志 VO 列表
   */
  List<JobLogVO> findRunningByNode(String nodeId);

  /**
   * P1-3: 标记指定节点上 RUNNING 日志为 FAILED（节点掉线故障转移）。
   *
   * @param nodeId 执行节点 ID
   * @param now 标记为失败的时间
   * @return 实际标记为 FAILED 的日志条数
   */
  int markFailedByNodeOffline(String nodeId, LocalDateTime now);

  /**
   * P1-4: 查询所有 RUNNING 状态日志的执行节点 ID（去重）。
   *
   * @return 有 RUNNING 任务的节点 ID 列表（去重），无记录时返回空列表
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
   * @param jobId 任务 ID
   * @param since 时间窗口起点（仅统计此时间之后的日志）
   * @return P95 耗时（毫秒）；无成功记录时返回 {@code Optional.empty()}
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
   * @param since 时间窗口起点（仅统计此时间之后的日志）
   * @return 全局 P95 耗时（毫秒）；无成功记录时返回 {@code Optional.empty()}
   */
  Optional<Long> findDurationP95Global(LocalDateTime since);

  /**
   * P2-2: 批量清理过期任务日志（硬删除，释放磁盘空间）。
   *
   * @param before 过期时间分界点（删除此时间之前的记录）
   * @param limit 单批最多删除条数
   * @return 实际删除的日志条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  /**
   * 统计指定状态且起始时间之后的日志数量。
   *
   * @param status 日志状态（如 RUNNING、SUCCESS 等）
   * @param startAfter 起始时间过滤（仅统计 start_time 在此时间之后的日志）
   * @return 满足条件的日志数量
   */
  long countByStatusAfter(String status, LocalDateTime startAfter);

  /**
   * 查询最近的失败日志列表。
   *
   * @param limit 最多返回条数
   * @return 最近的失败日志 VO 列表（按创建时间降序）
   */
  List<JobLogVO> findRecentFailures(int limit);

  /**
   * 查询指定任务 KEY 的执行日志列表。
   *
   * @param jobKey 任务 KEY
   * @param limit 最多返回条数
   * @return 该任务 KEY 的执行日志 VO 列表（按创建时间降序）
   */
  List<JobLogVO> findByJobKey(String jobKey, int limit);

  /**
   * 查询指定任务 KEY 的最新一条执行日志。
   *
   * @param jobKey 任务 KEY
   * @return 该任务 KEY 的最新一条执行日志；无记录时返回 {@code Optional.empty()}
   */
  Optional<JobLogVO> findLatestByJobKey(String jobKey);

  /**
   * 统计指定时间范围内的执行日志数量（按 start_time）。
   *
   * @param start 时间范围起点（含）
   * @param end 时间范围终点（含）
   * @return 指定时间范围内的执行日志数量（按 start_time 过滤）
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
