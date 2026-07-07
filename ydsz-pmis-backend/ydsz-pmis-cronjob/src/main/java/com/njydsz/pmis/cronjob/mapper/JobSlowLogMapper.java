package com.njydsz.pmis.cronjob.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.JobSlowLogDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 慢任务诊断日志 Mapper（P6-3）。
 *
 * <p>对应 pmis_job_slow_log 表，记录执行耗时超过 {@code pmis_job.slow_threshold_ms} 的任务执行。
 * 与 {@link JobLogMapper} 的区别：
 * <ul>
 *   <li>job_log 记录全部执行（RUNNING/SUCCESS/FAILED/TIMEOUT），用于审计</li>
 *   <li>slow_log 仅记录慢执行，用于性能趋势分析与优化决策</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobSlowLogMapper extends BaseMapper<JobSlowLogDO> {

    /**
     * 检查指定 job_log 是否已记录到 slow_log（幂等判断）。
     *
     * @param logId 任务日志 ID（pmis_job_log.id）
     * @return 已记录条数（&gt;0 表示已存在）
     */
    @Select("SELECT COUNT(1) FROM pmis_job_slow_log "
            + "WHERE log_id = #{logId} AND deleted = 0")
    int countByLogId(@Param("logId") String logId);

    /**
     * 查询指定任务在时间窗口内的慢执行记录（任务详情页展示）。
     *
     * @param jobId 任务 ID
     * @param since 时间窗口起点
     * @return 慢任务日志列表（按耗时降序）
     */
    @Select("SELECT id, job_id, job_key, log_id, duration_ms, slow_threshold_ms, "
            + "params_json, error_message, trace_id, tenant_id, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_slow_log "
            + "WHERE job_id = #{jobId} AND created_at >= #{since} AND deleted = 0 "
            + "ORDER BY duration_ms DESC")
    List<JobSlowLogDO> selectByJobIdSince(@Param("jobId") String jobId,
                                           @Param("since") LocalDateTime since);
}
