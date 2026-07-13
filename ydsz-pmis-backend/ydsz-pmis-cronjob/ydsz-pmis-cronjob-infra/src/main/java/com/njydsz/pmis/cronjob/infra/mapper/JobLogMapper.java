package com.njydsz.pmis.cronjob.infra.mapper.log;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.domain.entity.log.JobLogDO;

/**
 * 任务日志 Mapper
 *
 * <p>对应 pmis_job_log 表，归档每次任务执行的开始/结束/耗时/状态/结果，供执行历史查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobLogMapper extends BaseMapper<JobLogDO> {

    /**
     * 查询超时但未结束的 RUNNING 日志（P2-4）。
     *
     * <p>JOIN pmis_job 取 timeout_ms，筛选 {@code log.status='RUNNING'}
     * 且 {@code log.start_time + job.timeout_ms < NOW()} 的记录。
     *
     * @param now   当前时间
     * @param limit 单批最多扫描条数
     * @return 超时日志列表（已关联任务 timeout_ms）
     */
    @Select("SELECT l.id, l.job_id, l.job_key, l.start_time, l.end_time, l.duration_ms, "
            + "       l.status, l.error_message, l.params_json, l.result_json, l.trace_id, "
            + "       l.trigger_type, l.lock_holder, l.exec_node_id, l.exec_thread_id, "
            + "       l.shard_index, l.shard_total, "
            + "       l.created_at, l.deleted "
            + "FROM pmis_job_log l "
            + "INNER JOIN pmis_job j ON j.id = l.job_id AND j.deleted = 0 "
            + "WHERE l.status = 'RUNNING' "
            + "  AND l.deleted = 0 "
            + "  AND j.timeout_ms IS NOT NULL "
            + "  AND j.timeout_ms > 0 "
            + "  AND l.start_time + (j.timeout_ms || ' milliseconds')::INTERVAL < #{now} "
            + "ORDER BY l.start_time ASC "
            + "LIMIT #{limit}")
    List<JobLogDO> selectTimedOutLogs(@Param("now") LocalDateTime now,
                                      @Param("limit") int limit);

    /**
     * 标记指定日志为超时（status=TIMEOUT，填充 end_time / duration_ms / error_message）。
     *
     * @param id           日志 ID
     * @param endTime      结束时间
     * @param durationMs   耗时（毫秒）
     * @param errorMessage 错误信息（如 "Task timed out after 60000ms"）
     * @return 受影响行数
     */
    @Update("UPDATE pmis_job_log "
            + "SET status = 'TIMEOUT', end_time = #{endTime}, duration_ms = #{durationMs}, "
            + "    error_message = #{errorMessage} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0")
    int markTimeout(@Param("id") String id,
                    @Param("endTime") LocalDateTime endTime,
                    @Param("durationMs") long durationMs,
                    @Param("errorMessage") String errorMessage);

    /**
     * 查询慢任务执行日志（P6-3, P2-1-merge 重构）。
     *
     * <p>筛选条件：
     * <ul>
     *   <li>{@code log.status IN ('SUCCESS','FAILED','TIMEOUT')}（已结束的执行）</li>
     *   <li>{@code log.duration_ms IS NOT NULL}（耗时已记录）</li>
     *   <li>{@code log.duration_ms > job.slow_threshold_ms}（超过慢任务阈值）</li>
     *   <li>{@code job.slow_threshold_ms IS NOT NULL AND > 0}（任务启用了慢任务检测）</li>
     *   <li>{@code log.created_at >= since}（仅扫描时间窗口内的日志，避免全表扫描）</li>
     *   <li>{@code log.is_slow = 0}（尚未标记为慢任务，保证幂等）</li>
     * </ul>
     *
     * <p>P2-1-merge: 原通过 LEFT JOIN pmis_job_slow_log 过滤已记录的 log_id,
     * 现改为直接检查 pmis_job_log.is_slow 字段, 消除独立表关联。
     *
     * @param since 时间窗口起点（仅扫描此时间之后的日志）
     * @param limit 单批最多扫描条数
     * @return 待标记的慢任务日志列表（含 jobId / jobKey 冗余字段，不含 tenantId）
     */
    @Select("SELECT l.id, l.job_id, l.job_key, l.start_time, l.end_time, l.duration_ms, "
            + "       l.status, l.error_message, l.params_json, l.result_json, l.trace_id, "
            + "       l.trigger_type, l.lock_holder, l.exec_node_id, l.exec_thread_id, "
            + "       l.shard_index, l.shard_total, l.is_slow, l.slow_threshold_ms, "
            + "       l.created_at, l.deleted "
            + "FROM pmis_job_log l "
            + "INNER JOIN pmis_job j ON j.id = l.job_id AND j.deleted = 0 "
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
    List<JobLogDO> selectSlowLogs(@Param("since") LocalDateTime since,
                                    @Param("limit") int limit);

    /**
     * P2-1-merge: 标记指定日志为慢任务（is_slow=1, 快照 slow_threshold_ms）。
     *
     * <p>替代原 SlowTaskDetector 向 pmis_job_slow_log 插入记录的逻辑。
     *
     * @param logId            任务日志 ID
     * @param slowThresholdMs  慢任务阈值快照（毫秒）
     * @return 受影响行数
     */
    @Update("UPDATE pmis_job_log "
            + "SET is_slow = 1, slow_threshold_ms = #{slowThresholdMs} "
            + "WHERE id = #{logId} AND is_slow = 0 AND deleted = 0")
    int markSlow(@Param("logId") String logId,
                 @Param("slowThresholdMs") long slowThresholdMs);

    /**
     * P1-3: 查询指定节点上 RUNNING 状态的日志（故障转移用）。
     */
    @Select("SELECT id, job_id, job_key, start_time, end_time, duration_ms, "
            + "       status, error_message, params_json, result_json, trace_id, "
            + "       trigger_type, lock_holder, exec_node_id, exec_thread_id, "
            + "       shard_index, shard_total, "
            + "       created_at, deleted "
            + "FROM pmis_job_log "
            + "WHERE status = 'RUNNING' AND deleted = 0 AND exec_node_id = #{nodeId}")
    List<JobLogDO> selectRunningByNode(@Param("nodeId") String nodeId);

    /**
     * P1-3: 标记指定节点上 RUNNING 日志为 FAILED（节点掉线故障转移）。
     */
    @Update("UPDATE pmis_job_log "
            + "SET status = 'FAILED', end_time = #{now}, "
            + "    duration_ms = EXTRACT(EPOCH FROM (#{now} - start_time)) * 1000, "
            + "    error_message = 'Node went offline during execution' "
            + "WHERE status = 'RUNNING' AND deleted = 0 AND exec_node_id = #{nodeId}")
    int markFailedByNodeOffline(@Param("nodeId") String nodeId,
                                 @Param("now") LocalDateTime now);

    /**
     * P1-4: 查询所有 RUNNING 状态日志的执行节点 ID（去重，故障转移扫描用）。
     *
     * <p>用于 {@link com.njydsz.pmis.cronjob.server.core.dispatch.FailoverScanner} 扫描
     * 有 RUNNING 任务但可能已下线的节点。对比在线节点列表后找出下线节点。
     *
     * @return 有 RUNNING 任务的节点 ID 列表（去重）；无记录时返回空列表
     */
    @Select("SELECT DISTINCT exec_node_id FROM pmis_job_log "
            + "WHERE status = 'RUNNING' AND deleted = 0 AND exec_node_id IS NOT NULL")
    List<String> selectRunningNodeIds();

    /**
     * P3-2: 统计指定任务在时间窗口内的执行次数和失败次数。
     *
     * <p>用于 FAIL_RATE 告警计算：失败率 = failed / total * 100。
     *
     * @param jobId 任务 ID
     * @param since 时间窗口起点（仅统计此时间之后的日志）
     * @return Map 包含 total（总次数）和 failed（失败次数）字段；
     *         无记录时 total=0 / failed=0（COUNT/SUM 不返回 null）
     */
    @Select("SELECT COUNT(1) as total, "
            + "SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed "
            + "FROM pmis_job_log "
            + "WHERE job_id = #{jobId} AND created_at >= #{since} AND deleted = 0")
    Map<String, Object> countByJobIdSince(@Param("jobId") String jobId,
                                            @Param("since") LocalDateTime since);

    /**
     * P3-2: 统计指定任务在时间窗口内的 P95 耗时（PostgreSQL PERCENTILE_CONT 近似）。
     *
     * <p>仅统计 {@code status='SUCCESS'} 的执行，避免失败/超时任务拉高 P95。
     * 用于 DURATION_P95 告警计算：P95 &gt;= threshold 时触发告警。
     *
     * <p>注意：PERCENTILE_CONT 是 PostgreSQL 标准聚合函数，返回 double；
     * 此处通过 {@code ::BIGINT} 转 Long 兼容（NULL 时 COALESCE 返回 0）。
     *
     * @param jobId 任务 ID
     * @param since 时间窗口起点
     * @return P95 耗时（毫秒）；无成功记录时返回 0
     */
    @Select("SELECT COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY duration_ms), 0)::BIGINT "
            + "FROM pmis_job_log "
            + "WHERE job_id = #{jobId} AND created_at >= #{since} "
            + "AND status = 'SUCCESS' AND deleted = 0")
    Long selectDurationP95(@Param("jobId") String jobId,
                            @Param("since") LocalDateTime since);

    /**
     * P2-2: 批量清理过期任务日志（硬删除，释放磁盘空间）。
     *
     * <p>按 {@code created_at < before} 筛选过期记录，单批最多删除 {@code limit} 条，
     * 避免大事务锁表。由 {@link com.njydsz.pmis.cronjob.server.core.cleaner.LogCleaner} 循环调用直至无数据。
     *
     * @param before 过期分界时间（此时间之前的记录将被删除）
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_log "
            + "WHERE id IN ("
            + "  SELECT id FROM pmis_job_log "
            + "  WHERE created_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);

    /**
     * P2-9: 全文搜索任务日志（PostgreSQL tsvector全文索引）。
     *
     * <p>在 error_message + result_json + job_key 上构建 tsvector 进行全文检索，
     * 支持 |（OR）、&amp;（AND）、!（NOT）操作符。
     *
     * @param query  搜索关键词（如 'timeout &amp; error'）
     * @param limit  最多返回条数
     * @return 匹配的日志列表
     */
    @Select("SELECT id, job_id, job_key, start_time, end_time, duration_ms, "
            + "       status, error_message, params_json, result_json, trace_id, "
            + "       trigger_type, lock_holder, exec_node_id, exec_thread_id, "
            + "       shard_index, shard_total, "
            + "       created_at, deleted "
            + "FROM pmis_job_log "
            + "WHERE deleted = 0 "
            + "  AND to_tsvector('english', coalesce(error_message,'') || ' ' || coalesce(result_json,'') || ' ' || coalesce(job_key,'')) "
            + "      @@ to_tsquery('english', #{query}) "
            + "ORDER BY ts_rank(to_tsvector('english', coalesce(error_message,'') || ' ' || coalesce(result_json,'') || ' ' || coalesce(job_key,'')), "
            + "                  to_tsquery('english', #{query})) DESC "
            + "LIMIT #{limit}")
    List<JobLogDO> fullTextSearch(@Param("query") String query,
                                   @Param("limit") int limit);
}
