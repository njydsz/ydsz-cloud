package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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
     * 查询慢任务执行日志（P6-3）。
     *
     * <p>筛选条件：
     * <ul>
     *   <li>{@code log.status IN ('SUCCESS','FAILED','TIMEOUT')}（已结束的执行）</li>
     *   <li>{@code log.duration_ms IS NOT NULL}（耗时已记录）</li>
     *   <li>{@code log.duration_ms > job.slow_threshold_ms}（超过慢任务阈值）</li>
     *   <li>{@code job.slow_threshold_ms IS NOT NULL AND > 0}（任务启用了慢任务检测）</li>
     *   <li>{@code log.created_at >= since}（仅扫描时间窗口内的日志，避免全表扫描）</li>
     *   <li>LEFT JOIN pmis_job_slow_log 过滤已记录的 log_id（保证幂等）</li>
     * </ul>
     *
     * @param since 时间窗口起点（仅扫描此时间之后的日志）
     * @param limit 单批最多扫描条数
     * @return 待记录的慢任务日志列表（含 jobId / jobKey 冗余字段，不含 tenantId）
     */
    @Select("SELECT l.id, l.job_id, l.job_key, l.start_time, l.end_time, l.duration_ms, "
            + "       l.status, l.error_message, l.params_json, l.result_json, l.trace_id, "
            + "       l.trigger_type, l.lock_holder, l.exec_node_id, l.exec_thread_id, "
            + "       l.shard_index, l.shard_total, "
            + "       l.created_at, l.deleted "
            + "FROM pmis_job_log l "
            + "INNER JOIN pmis_job j ON j.id = l.job_id AND j.deleted = 0 "
            + "LEFT JOIN pmis_job_slow_log s ON s.log_id = l.id AND s.deleted = 0 "
            + "WHERE l.status IN ('SUCCESS','FAILED','TIMEOUT') "
            + "  AND l.deleted = 0 "
            + "  AND l.duration_ms IS NOT NULL "
            + "  AND j.slow_threshold_ms IS NOT NULL "
            + "  AND j.slow_threshold_ms > 0 "
            + "  AND l.duration_ms > j.slow_threshold_ms "
            + "  AND l.created_at >= #{since} "
            + "  AND s.id IS NULL "
            + "ORDER BY l.duration_ms DESC "
            + "LIMIT #{limit}")
    List<JobLogDO> selectSlowLogs(@Param("since") LocalDateTime since,
                                    @Param("limit") int limit);

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
}
