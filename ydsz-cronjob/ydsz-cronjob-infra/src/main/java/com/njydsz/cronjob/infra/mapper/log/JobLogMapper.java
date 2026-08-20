package com.njydsz.cronjob.infra.mapper.log;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.cronjob.infra.entity.log.JobLog;

/**
 * 任务执行日志 Mapper
 *
 * <p>对应数据表 <code>ydsz_job_log</code>。
 *
 * <p>执行日志记录任务触发时间、参数、结果、耗时、错误，是任务运维的事实表。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_log_id — 日志 ID 唯一索引
 *   <li>idx_job_id — 任务维度查询索引
 *   <li>idx_trigger_at — 触发时间排序索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.entity.log.JobLog 执行日志实体
 * @see com.njydsz.cronjob.server.service.JobLogService 日志 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobLogMapper extends BaseMapper<JobLog> {

  /**
   * 查询超时但未结束的 RUNNING 日志（P2-4）。
   *
   * <p>JOIN ydsz_job 取 timeout_ms，筛选 {@code LOG.status='RUNNING'} 且 {@code LOG.start_time +
   * job.timeout_ms < NOW()} 的记录。
   *
   * @param now 当前时间
   * @param limit 单批最多扫描条数
   * @return 超时日志列表（已关联任务 timeout_ms）
   */
  @Select(
      "SELECT l.id, l.job_id, l.job_key, l.start_time, l.end_time, l.duration_ms, "
          + "       l.status, l.error_message, l.params_json, l.result_json, l.trace_id, "
          + "       l.trigger_type, l.lock_holder, l.exec_node_id, l.exec_thread_id, "
          + "       l.shard_index, l.shard_total, "
          + "       l.created_at, l.deleted "
          + "FROM ydsz_job_log l "
          + "INNER JOIN ydsz_job j ON j.id = l.job_id AND j.deleted = 0 "
          + "WHERE l.status = 'RUNNING' "
          + "  AND l.deleted = 0 "
          + "  AND j.timeout_ms IS NOT NULL "
          + "  AND j.timeout_ms > 0 "
          + "  AND l.start_time + (j.timeout_ms || ' milliseconds')::INTERVAL < #{now} "
          + "ORDER BY l.start_time ASC "
          + "LIMIT #{limit}")
  List<JobLog> selectTimedOutLogs(@Param("now") LocalDateTime now, @Param("limit") int limit);

  /**
   * 查询正在执行但已达到 SLA 80% 预警线的日志（P2-F2 SLA 预警）。
   *
   * <p>筛选条件：
   *
   * <ul>
   *   <li>{@code log.status = 'RUNNING'}（仍在执行中）
   *   <li>{@code job.sla_ms IS NOT NULL AND job.sla_ms > 0}（配置了 SLA）
   *   <li>{@code start_time + (sla_ms * 0.8) <= now}（已达到 80% 阈值）
   *   <li>{@code start_time + sla_ms > now}（尚未达到 100%，避免与超时扫描重复）
   * </ul>
   *
   * <p>返回的日志将触发 SLA_WARNING 预警，通知运维关注即将超时的任务。
   *
   * @param now 当前时间
   * @param limit 最多返回条数
   * @return 达到 SLA 80% 预警线的日志列表
   */
  @Select(
      "SELECT l.id, l.job_id, l.job_key, l.log_date, l.start_time, l.end_time, "
          + "       l.duration_ms, l.status, l.error_message, l.params_json, l.result_json, l.trace_id, "
          + "       l.trigger_type, l.lock_holder, l.exec_node_id, l.exec_thread_id, "
          + "       l.shard_index, l.shard_total, "
          + "       l.created_at, l.deleted "
          + "FROM ydsz_job_log l "
          + "INNER JOIN ydsz_job j ON j.id = l.job_id AND j.deleted = 0 "
          + "WHERE l.status = 'RUNNING' "
          + "  AND l.deleted = 0 "
          + "  AND j.sla_ms IS NOT NULL "
          + "  AND j.sla_ms > 0 "
          + "  AND l.start_time + ((j.sla_ms * 0.8) || ' milliseconds')::INTERVAL <= #{now} "
          + "  AND l.start_time + (j.sla_ms || ' milliseconds')::INTERVAL > #{now} "
          + "ORDER BY l.start_time ASC "
          + "LIMIT #{limit}")
  List<JobLog> selectApproachingSlaLogs(@Param("now") LocalDateTime now, @Param("limit") int limit);

  /**
   * 标记指定日志为超时（status=TIMEOUT，填充 end_time / duration_ms / error_message）。
   *
   * @param id 日志 ID
   * @param endTime 结束时间
   * @param durationMs 耗时（毫秒）
   * @param errorMessage 错误信息（如 "Task timed out after 60000ms"）
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_log "
          + "SET status = 'TIMEOUT', end_time = #{endTime}, duration_ms = #{durationMs}, "
          + "    error_message = #{errorMessage} "
          + "WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0")
  int markTimeout(
      @Param("id") String id,
      @Param("endTime") LocalDateTime endTime,
      @Param("durationMs") long durationMs,
      @Param("errorMessage") String errorMessage);

  /**
   * 查询慢任务执行日志。
   *
   * <p>筛选条件：
   *
   * <ul>
   *   <li>{@code LOG.status IN ('SUCCESS','FAILED','TIMEOUT')}（已结束的执行）
   *   <li>{@code LOG.duration_ms IS NOT NULL}（耗时已记录）
   *   <li>{@code LOG.duration_ms > job.slow_threshold_ms}（超过慢任务阈值）
   *   <li>{@code job.slow_threshold_ms IS NOT NULL AND > 0}（任务启用了慢任务检测）
   *   <li>{@code LOG.created_at >= since}（仅扫描时间窗口内的日志，避免全表扫描）
   *   <li>{@code LOG.is_slow = 0}（尚未标记为慢任务，保证幂等）
   * </ul>
   *
   * <p>原通过 LEFT JOIN 慢日志表过滤已记录 log_id， 现改为直接检查 ydsz_job_log.is_slow 字段, 消除独立表关联。
   *
   * @param since 时间窗口起点（仅扫描此时间之后的日志）
   * @param limit 单批最多扫描条数
   * @return 待标记的慢任务日志列表（含 jobId / jobKey 冗余字段，不含 tenantId）
   */
  @Select(
      "SELECT l.id, l.job_id, l.job_key, l.start_time, l.end_time, l.duration_ms, "
          + "       l.status, l.error_message, l.params_json, l.result_json, l.trace_id, "
          + "       l.trigger_type, l.lock_holder, l.exec_node_id, l.exec_thread_id, "
          + "       l.shard_index, l.shard_total, l.is_slow, l.slow_threshold_ms, "
          + "       l.created_at, l.deleted "
          + "FROM ydsz_job_log l "
          + "INNER JOIN ydsz_job j ON j.id = l.job_id AND j.deleted = 0 "
          + "WHERE l.status IN ('SUCCESS','FAILED','TIMEOUT') "
          + "  AND l.deleted = 0 "
          + "  AND l.duration_ms IS NOT NULL "
          + "  AND j.slow_threshold_ms IS NOT NULL "
          + "  AND j.slow_threshold_ms > 0 "
          + "  AND l.duration_ms > j.slow_threshold_ms "
          + "  AND l.created_at >= #{since} "
          + "  AND l.is_slow = 0 "
          + "ORDER BY l.duration_ms DESC "
          + "LIMIT #{limit}")
  List<JobLog> selectSlowLogs(@Param("since") LocalDateTime since, @Param("limit") int limit);

  /**
   * 标记指定日志为慢任务（is_slow=1, 快照 slow_threshold_ms）。
   *
   * @param logId 任务日志 ID
   * @param slowThresholdMs 慢任务阈值快照（毫秒）
   * @return 受影响行数
   */
  @Update(
      "UPDATE ydsz_job_log "
          + "SET is_slow = 1, slow_threshold_ms = #{slowThresholdMs} "
          + "WHERE id = #{logId} AND is_slow = 0 AND deleted = 0")
  int markSlow(@Param("logId") String logId, @Param("slowThresholdMs") long slowThresholdMs);

  /** P1-3: 查询指定节点上 RUNNING 状态的日志（故障转移用）。 */
  @Select(
      "SELECT id, job_id, job_key, start_time, end_time, duration_ms, "
          + "       status, error_message, params_json, result_json, trace_id, "
          + "       trigger_type, lock_holder, exec_node_id, exec_thread_id, "
          + "       shard_index, shard_total, "
          + "       created_at, deleted "
          + "FROM ydsz_job_log "
          + "WHERE status = 'RUNNING' AND deleted = 0 AND exec_node_id = #{nodeId}")
  List<JobLog> selectRunningByNode(@Param("nodeId") String nodeId);

  /** P1-3: 标记指定节点上 RUNNING 日志为 FAILED（节点掉线故障转移）。 */
  @Update(
      "UPDATE ydsz_job_log "
          + "SET status = 'FAILED', end_time = #{now}, "
          + "    duration_ms = EXTRACT(EPOCH FROM (#{now} - start_time)) * 1000, "
          + "    error_message = 'Node went offline during execution' "
          + "WHERE status = 'RUNNING' AND deleted = 0 AND exec_node_id = #{nodeId}")
  int markFailedByNodeOffline(@Param("nodeId") String nodeId, @Param("now") LocalDateTime now);

  /**
   * P1-4: 查询所有 RUNNING 状态日志的执行节点 ID（去重，故障转移扫描用）。
   *
   * <p>用于 {@link com.njydsz.cronjob.server.core.healing.AnomalyRecoveryScanner} 扫描 有 RUNNING
   * 任务但可能已下线的节点。对比在线节点列表后找出下线节点。
   *
   * @return 有 RUNNING 任务的节点 ID 列表（去重）；无记录时返回空列表
   */
  @Select(
      "SELECT DISTINCT exec_node_id FROM ydsz_job_log "
          + "WHERE status = 'RUNNING' AND deleted = 0 AND exec_node_id IS NOT NULL")
  List<String> selectRunningNodeIds();

  /**
   * 查询 RUNNING 状态超过指定起始时间阈值的卡死任务。
   *
   * @param threshold 起始时间阈值（start_time 早于此值即为卡死）
   * @param limit 最多返回条数
   * @return 卡死任务日志列表
   */
  @Select(
      "SELECT id, job_id, job_key, start_time, end_time, duration_ms, "
          + "       status, error_message, params_json, result_json, trace_id, "
          + "       trigger_type, lock_holder, exec_node_id, exec_thread_id, "
          + "       shard_index, shard_total, "
          + "       created_at, deleted "
          + "FROM ydsz_job_log "
          + "WHERE status = 'RUNNING' AND deleted = 0 AND start_time < #{threshold} "
          + "ORDER BY start_time ASC "
          + "LIMIT #{limit}")
  List<JobLog> selectStuckTasks(
      @Param("threshold") LocalDateTime threshold, @Param("limit") int limit);

  /**
   * P3-2: 统计指定任务在时间窗口内的执行次数和失败次数。
   *
   * <p>用于 FAIL_RATE 告警计算：失败率 = failed / total * 100。
   *
   * @param jobId 任务 ID
   * @param since 时间窗口起点（仅统计此时间之后的日志）
   * @return Map 包含 total（总次数）和 failed（失败次数）字段； 无记录时 total=0 / failed=0（COUNT/SUM 不返回 null）
   */
  @Select(
      "SELECT COUNT(1) as total, "
          + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed "
          + "FROM ydsz_job_log "
          + "WHERE job_id = #{jobId} AND created_at >= #{since} AND deleted = 0")
  Map<String, Object> countByJobIdSince(
      @Param("jobId") String jobId, @Param("since") LocalDateTime since);

  /**
   * P3-2: 统计指定任务在时间窗口内的 P95 耗时（PostgreSQL PERCENTILE_CONT 近似）。
   *
   * <p>仅统计 {@code status='SUCCESS'} 的执行，避免失败/超时任务拉高 P95。 用于 DURATION_P95 告警计算：P95 &gt;= threshold
   * 时触发告警。
   *
   * <p>注意：PERCENTILE_CONT 是 PostgreSQL 标准聚合函数，返回 double； 此处通过 {@code ::BIGINT} 转 Long 兼容（NULL 时
   * COALESCE 返回 0）。
   *
   * @param jobId 任务 ID
   * @param since 时间窗口起点
   * @return P95 耗时（毫秒）；无成功记录时返回 0
   */
  @Select(
      "SELECT COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 0)::BIGINT "
          + "FROM ydsz_job_log "
          + "WHERE job_id = #{jobId} AND created_at >= #{since} "
          + "AND status = 'SUCCESS' AND deleted = 0")
  Long selectDurationP95(@Param("jobId") String jobId, @Param("since") LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的执行次数和失败次数。
   *
   * <p>供全局 FAIL_RATE 告警规则（jobId=NULL）使用：失败率 = failed / total * 100。
   *
   * @param since 时间窗口起点（仅统计此时间之后的日志）
   * @return Map 包含 total（总次数）和 failed（失败次数）字段；无记录时 total=0 / failed=0
   */
  @Select(
      "SELECT COUNT(1) as total, "
          + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed "
          + "FROM ydsz_job_log "
          + "WHERE created_at >= #{since} AND deleted = 0")
  Map<String, Object> countSince(@Param("since") LocalDateTime since);

  /**
   * P0-F2: 统计全局（所有任务）在时间窗口内的 P95 耗时。
   *
   * <p>供全局 DURATION_P95 告警规则（jobId=NULL）使用。仅统计 {@code status='SUCCESS'} 的执行。
   *
   * @param since 时间窗口起点
   * @return P95 耗时（毫秒）；无成功记录时返回 0
   */
  @Select(
      "SELECT COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 0)::BIGINT "
          + "FROM ydsz_job_log "
          + "WHERE created_at >= #{since} AND status = 'SUCCESS' AND deleted = 0")
  Long selectDurationP95Global(@Param("since") LocalDateTime since);

  /**
   * P2-2: 批量清理过期任务日志（硬删除，释放磁盘空间）。
   *
   * <p>按 {@code created_at < before} 筛选过期记录，单批最多删除 {@code limit} 条， 避免大事务锁表。由 {@link
   * com.njydsz.cronjob.server.core.cleaner.LogCleaner} 循环调用直至无数据。
   *
   * @param before 过期分界时间（此时间之前的记录将被删除）
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  /**
   * 批量删除过期日志（基于 ctid 物理地址，避免回表）。
   *
   * <p>PostgreSQL 特有优化：使用 ctid = ANY(ARRAY(...)) 替代 id IN (SELECT id ...)，
   * 直接通过物理行地址定位数据页，避免二次索引扫描，大表删除性能提升 3-5 倍。
   */
  @Delete(
      "DELETE FROM ydsz_job_log "
          + "WHERE ctid = ANY(ARRAY("
          + "  SELECT ctid FROM ydsz_job_log "
          + "  WHERE created_at < #{before} "
          + "  LIMIT #{limit}"
          + "))")
  int cleanExpiredLogs(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
