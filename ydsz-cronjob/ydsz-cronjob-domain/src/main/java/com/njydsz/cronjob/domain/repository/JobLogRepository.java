package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * 任务执行日志 Repository（domain 层契约）。
 *
 * <p>定义任务执行日志的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobLogVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobLogRepository {

  /**
   * 查询超时但未结束的 RUNNING 日志（P2-4）。
   *
   * @param now 当前时间
   * @param limit 单批最多扫描条数
   * @return 超时日志 VO 列表
   */
  List<JobLogVO> findTimedOutLogs(LocalDateTime now, int limit);

  /**
   * 查询正在执行但已达到 SLA 80% 预警线的日志（P2-F2 SLA 预警）。
   *
   * @param now 当前时间
   * @param limit 最多返回条数
   * @return 达到 SLA 80% 预警线的日志 VO 列表
   */
  List<JobLogVO> findApproachingSlaLogs(LocalDateTime now, int limit);

  /**
   * 标记指定日志为超时。
   *
   * @param id 日志 ID
   * @param endTime 结束时间
   * @param durationMs 耗时（毫秒）
   * @param errorMessage 错误信息
   * @return 受影响行数
   */
  int markTimeout(String id, LocalDateTime endTime, long durationMs, String errorMessage);

  /**
   * 查询慢任务执行日志。
   *
   * @param since 时间窗口起点
   * @param limit 单批最多扫描条数
   * @return 待标记的慢任务日志 VO 列表
   */
  List<JobLogVO> findSlowLogs(LocalDateTime since, int limit);

  /**
   * 标记指定日志为慢任务。
   *
   * @param logId 任务日志 ID
   * @param slowThresholdMs 慢任务阈值快照（毫秒）
   * @return 受影响行数
   */
  int markSlow(String logId, long slowThresholdMs);

  /**
   * P1-3: 查询指定节点上 RUNNING 状态的日志（故障转移用）。
   *
   * @param nodeId 节点 ID
   * @return RUNNING 日志 VO 列表
   */
  List<JobLogVO> findRunningByNode(String nodeId);

  /**
   * P1-3: 标记指定节点上 RUNNING 日志为 FAILED（节点掉线故障转移）。
   *
   * @param nodeId 节点 ID
   * @param now 当前时间
   * @return 受影响行数
   */
  int markFailedByNodeOffline(String nodeId, LocalDateTime now);

  /**
   * P1-4: 查询所有 RUNNING 状态日志的执行节点 ID（去重）。
   *
   * @return 有 RUNNING 任务的节点 ID 列表
   */
  List<String> findRunningNodeIds();

  /**
   * P3-2: 统计指定任务在时间窗口内的执行次数和失败次数。
   *
   * @param jobId 任务 ID
   * @param since 时间窗口起点
   * @return Map 包含 total 和 failed 字段
   */
  Map<String, Object> countByJobIdSince(String jobId, LocalDateTime since);

  /**
   * P3-2: 统计指定任务在时间窗口内的 P95 耗时。
   *
   * @param jobId 任务 ID
   * @param since 时间窗口起点
   * @return P95 耗时（毫秒）
   */
  Optional<Long> findDurationP95(String jobId, LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的执行次数和失败次数。
   *
   * <p>供全局 FAIL_RATE 告警规则（{@code jobId=NULL}）使用。全局规则无具体 jobId，
   * 需按时间窗口聚合全量任务的失败率。
   *
   * @param since 时间窗口起点
   * @return Map 包含 total 和 failed 字段
   */
  Map<String, Object> countSince(LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的 P95 耗时。
   *
   * <p>供全局 DURATION_P95 告警规则（{@code jobId=NULL}）使用。仅统计 SUCCESS 状态执行。
   *
   * @param since 时间窗口起点
   * @return P95 耗时（毫秒）；无成功记录时返回空
   */
  Optional<Long> findDurationP95Global(LocalDateTime since);

  /**
   * P2-2: 批量清理过期任务日志（硬删除，释放磁盘空间）。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  // ===== Web 层查询方法（Controller 停止 Mapper 直注） =====

  /**
   * 统计指定状态且起始时间之后的日志数量。
   *
   * @param status 执行状态（SUCCESS / FAILED / RUNNING），可为 null 表示不限
   * @param startAfter 起始时间之后（含），可为 null 表示不限
   * @return 日志数量
   */
  long countByStatusAfter(String status, LocalDateTime startAfter);

  /**
   * 查询最近的失败日志列表。
   *
   * @param limit 返回条数上限
   * @return 失败日志 VO 列表（按开始时间倒序）
   */
  List<JobLogVO> findRecentFailures(int limit);

  /**
   * 查询指定任务 KEY 的执行日志列表。
   *
   * @param jobKey 任务 KEY（精确匹配）
   * @param limit 返回条数上限
   * @return 日志 VO 列表（按创建时间倒序）
   */
  List<JobLogVO> findByJobKey(String jobKey, int limit);

  /**
   * 查询指定任务 KEY 的最新一条执行日志。
   *
   * @param jobKey 任务 KEY（精确匹配）
   * @return 最新日志 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobLogVO> findLatestByJobKey(String jobKey);

  /**
   * 统计指定时间范围内的执行日志数量（按 start_time）。
   *
   * <p>P0-FIX：原方法仅在 infra impl 中实现且被 JobStatsController 调用，但接口未声明
   * （impl 上 @Override 不成立），补充声明到接口使 DDD 契约完整。
   *
   * @param start 起始时间（含）
   * @param end 结束时间（含）
   * @return 日志数量
   */
  long countByTimeRange(LocalDateTime start, LocalDateTime end);
}
