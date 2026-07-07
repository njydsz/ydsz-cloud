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
            + "       l.trigger_type, l.created_at, l.deleted "
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
}
