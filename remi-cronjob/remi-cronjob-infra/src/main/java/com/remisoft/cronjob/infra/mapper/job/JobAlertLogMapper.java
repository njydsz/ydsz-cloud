package com.remisoft.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.cronjob.domain.entity.job.JobAlertLog;

/**
 * 任务告警日志 Mapper
 *
 * <p>对应数据表 <code>remi_job_alert_log</code>。
 * <p>告警日志记录每次触发的告警（任务、规则、触发时间、推送渠道、推送结果），用于告警审计与统计。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>idx_job_id — 任务维度查询索引</li>
 *   <li>idx_alert_at — 告警时间排序索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.cronjob.domain.entity.job.JobAlertLog 告警日志实体
 * @see com.remisoft.cronjob.server.service.JobAlertLogService 告警日志 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobAlertLogMapper extends BaseMapper<JobAlertLog> {

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
            + "FROM remi_alert_dispatch "
            + "WHERE rule_id = #{ruleId} AND source_type = 'CRONJOB' "
            + "  AND created_at >= #{since} AND deleted = 0")
    List<JobAlertLog> selectByRuleIdSince(@Param("ruleId") String ruleId,
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
            + "FROM remi_alert_dispatch "
            + "WHERE source_id = #{jobId} AND source_type = 'CRONJOB' "
            + "  AND created_at >= #{since} AND deleted = 0 "
            + "ORDER BY created_at DESC")
    List<JobAlertLog> selectByJobIdSince(@Param("jobId") String jobId,
                                            @Param("since") LocalDateTime since);

    /**
     * P2-2: 批量清理过期告警日志（硬删除, 仅清理 CRONJOB 来源）。
     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条数
     * @return 实际删除条数
     */
    @Delete("DELETE FROM remi_alert_dispatch "
            + "WHERE id IN ("
            + "  SELECT id FROM remi_alert_dispatch "
            + "  WHERE source_type = 'CRONJOB' AND created_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int cleanExpiredLogs(@Param("before") LocalDateTime before,
                         @Param("limit") int limit);
}
