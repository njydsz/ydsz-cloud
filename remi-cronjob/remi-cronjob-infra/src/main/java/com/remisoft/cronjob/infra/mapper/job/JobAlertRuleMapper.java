package com.remisoft.cronjob.infra.mapper.job;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.cronjob.domain.entity.job.JobAlertRule;

/**
 * 任务告警规则 Mapper
 *
 * <p>对应数据表 <code>remi_job_alert_rule</code>。
 * <p>告警规则定义任务失败/超时/连续失败等条件触发告警（IM/短信/邮件），按租户/任务维度配置。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_job_rule — (任务+规则名) 唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.cronjob.domain.entity.job.JobAlertRule 告警规则实体
 * @see com.remisoft.cronjob.server.service.JobAlertRuleService 告警规则 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface JobAlertRuleMapper extends BaseMapper<JobAlertRule> {

    /**
     * 查询所有启用的告警规则（启动时加载到内存）。
     *
     * @return 启用的规则列表
     */
    @Select("SELECT id, rule_name, job_id, job_key, alert_type, alert_level, "
            + "threshold, time_window_minutes, channels, receivers, "
            + "cooldown_minutes, enabled, source_type, last_alert_at, tenant_id, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM remi_job_alert_rule "
            + "WHERE deleted = 0 AND enabled = 1")
    List<JobAlertRule> selectAllEnabled();

    /**
     * 查询指定任务绑定的告警规则（含全局规则）。
     *
     * <p>对单任务告警触发时使用：先匹配 jobId 专属规则，再叠加 job_id IS NULL 的全局规则。
     *
     * @param jobId 任务 ID
     * @return 规则列表（含专属 + 全局）
     */
    @Select("SELECT id, rule_name, job_id, job_key, alert_type, alert_level, "
            + "threshold, time_window_minutes, channels, receivers, "
            + "cooldown_minutes, enabled, source_type, last_alert_at, tenant_id, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM remi_job_alert_rule "
            + "WHERE deleted = 0 AND enabled = 1 "
            + "AND (job_id = #{jobId} OR job_id IS NULL)")
    List<JobAlertRule> selectByJobIdOrGlobal(@Param("jobId") String jobId);

    /**
     * P3-2: 按告警类型查询启用的规则（周期性扫描使用）。
     *
     * <p>用于 FAIL_RATE / DURATION_P95 等需要周期性统计的告警类型，
     * 由 {@code AlertScanner} 定时调用。
     *
     * @param alertType 告警类型字符串（如 "FAIL_RATE" / "DURATION_P95"）
     * @return 启用的规则列表
     */
    @Select("SELECT id, rule_name, job_id, job_key, alert_type, alert_level, "
            + "threshold, time_window_minutes, channels, receivers, "
            + "cooldown_minutes, enabled, source_type, last_alert_at, tenant_id, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM remi_job_alert_rule "
            + "WHERE alert_type = #{alertType} AND enabled = 1 AND deleted = 0")
    List<JobAlertRule> selectByAlertType(@Param("alertType") String alertType);

    /**
     * 查询指定任务的 SLA 来源告警规则（source_type='SLA'）。
     *
     * <p>用于 SLA CRUD 代理查询：通过 alert_rule 表管理 SLA 规则。
     *
     * @param jobId 任务 ID
     * @return SLA 来源的告警规则列表
     */
    @Select("SELECT id, rule_name, job_id, job_key, alert_type, alert_level, "
            + "threshold, time_window_minutes, channels, receivers, "
            + "cooldown_minutes, enabled, source_type, last_alert_at, tenant_id, "
            + "created_by, created_at, updated_by, updated_at, deleted "
            + "FROM remi_job_alert_rule "
            + "WHERE job_id = #{jobId} AND source_type = 'SLA' AND deleted = 0")
    List<JobAlertRule> selectSlaRulesByJobId(@Param("jobId") String jobId);

    /**
     * 原子更新最后告警时间（CAS 语义，避免并发重复告警）。
     *
     * <p>仅当 {@code last_alert_at IS NULL} 或 {@code last_alert_at < cooldownBefore} 时更新成功。
     * 用于冷却窗口去重，保证分布式环境下同一规则不重复告警。
     *
     * @param ruleId         规则 ID
     * @param newAlertAt     新的告警时间
     * @param cooldownBefore 冷却窗口起点（NOW - cooldownMinutes）
     * @return 受影响行数（1=可告警；0=在冷却期内）
     */
    @Update("UPDATE remi_job_alert_rule "
            + "SET last_alert_at = #{newAlertAt}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{ruleId} AND deleted = 0 "
            + "AND (last_alert_at IS NULL OR last_alert_at < #{cooldownBefore})")
    int updateLastAlertAtIfNotInCooldown(@Param("ruleId") String ruleId,
                                         @Param("newAlertAt") LocalDateTime newAlertAt,
                                         @Param("cooldownBefore") LocalDateTime cooldownBefore);
}
