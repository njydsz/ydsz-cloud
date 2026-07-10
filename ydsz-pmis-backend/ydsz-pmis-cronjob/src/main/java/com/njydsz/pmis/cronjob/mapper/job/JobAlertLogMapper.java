package com.njydsz.pmis.cronjob.mapper.job;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.cronjob.entity.job.JobAlertLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务告警日志 Mapper（P5 告警 + 监控）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface JobAlertLogMapper extends BaseMapper<JobAlertLogDO> {

    /**
     * 查询指定规则在时间窗口内已发送的告警日志（用于去重判断）。
     *
     * @param ruleId  规则 ID
     * @param since   时间窗口起点
     * @return 告警日志列表
     */
    @Select("SELECT id, rule_id, rule_name, job_id, job_key, alert_type, alert_level, "
            + "trigger_value, threshold, channels, status, error_message, "
            + "trace_id, trigger_log_id, tenant_id, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_alert_log "
            + "WHERE rule_id = #{ruleId} AND created_at >= #{since} AND deleted = 0")
    List<JobAlertLogDO> selectByRuleIdSince(@Param("ruleId") String ruleId,
                                             @Param("since") LocalDateTime since);

    /**
     * 查询指定任务在时间窗口内的告警记录（任务详情页展示）。
     *
     * @param jobId 任务 ID
     * @param since 时间窗口起点
     * @return 告警日志列表
     */
    @Select("SELECT id, rule_id, rule_name, job_id, job_key, alert_type, alert_level, "
            + "trigger_value, threshold, channels, status, error_message, "
            + "trace_id, trigger_log_id, tenant_id, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_job_alert_log "
            + "WHERE job_id = #{jobId} AND created_at >= #{since} AND deleted = 0 "
            + "ORDER BY created_at DESC")
    List<JobAlertLogDO> selectByJobIdSince(@Param("jobId") String jobId,
                                            @Param("since") LocalDateTime since);

    /**
     * P2-2: 批量清理过期告警日志（硬删除）。
     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_job_alert_log "
            + "WHERE id IN ("
            + "  SELECT id FROM pmis_job_alert_log "
            + "  WHERE created_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);
}
