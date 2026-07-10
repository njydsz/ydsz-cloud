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
 * 任务告警日志 Mapper（P5 告警 + 监控, P3-1-merge 重构）。
 *
 * <p>P3-1-merge: 原查询 {@code pmis_job_alert_log} 表，现查询 {@code pmis_alert_dispatch}
 * 表（过滤 source_type='CRONJOB'）。字段映射通过 MyBatis-Plus 的驼峰转下划线自动完成。
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
    @Select("SELECT id, alert_code, rule_id, title as rule_name, source_id as job_id, "
            + "       null as job_key, alert_type, alert_level, trigger_value, threshold, "
            + "       push_channels as channels, status, fail_reason as error_message, "
            + "       provider_trace_id as trace_id, trigger_log_id, tenant_id, "
            + "       created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_alert_dispatch "
            + "WHERE rule_id = #{ruleId} AND source_type = 'CRONJOB' "
            + "  AND created_at >= #{since} AND deleted = 0")
    List<JobAlertLogDO> selectByRuleIdSince(@Param("ruleId") String ruleId,
                                             @Param("since") LocalDateTime since);

    /**
     * 查询指定任务在时间窗口内的告警记录（任务详情页展示）。
     *
     * @param jobId 任务 ID
     * @param since 时间窗口起点
     * @return 告警日志列表
     */
    @Select("SELECT id, alert_code, rule_id, title as rule_name, source_id as job_id, "
            + "       null as job_key, alert_type, alert_level, trigger_value, threshold, "
            + "       push_channels as channels, status, fail_reason as error_message, "
            + "       provider_trace_id as trace_id, trigger_log_id, tenant_id, "
            + "       created_by, created_at, updated_by, updated_at, deleted "
            + "FROM pmis_alert_dispatch "
            + "WHERE source_id = #{jobId} AND source_type = 'CRONJOB' "
            + "  AND created_at >= #{since} AND deleted = 0 "
            + "ORDER BY created_at DESC")
    List<JobAlertLogDO> selectByJobIdSince(@Param("jobId") String jobId,
                                            @Param("since") LocalDateTime since);

    /**
     * P2-2: 批量清理过期告警日志（硬删除, 仅清理 CRONJOB 来源）。
     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_alert_dispatch "
            + "WHERE id IN ("
            + "  SELECT id FROM pmis_alert_dispatch "
            + "  WHERE source_type = 'CRONJOB' AND created_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);
}
