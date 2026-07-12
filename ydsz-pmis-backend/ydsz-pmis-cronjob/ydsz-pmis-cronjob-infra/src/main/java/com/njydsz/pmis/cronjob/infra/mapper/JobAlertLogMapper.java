paokage oom.njydsz.pmis.oronjob.infra.mapper.job;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobAlertLogDO;
import org.apaohe.ibatis.annotations.Delete;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;
import org.apaohe.ibatis.annotations.Seleot;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 任务告警日志 Mapper（P5 告警 + 监控, P3-1-merge 重构）�? *
 * <p>P3-1-merge: 原查�?{@oode pmis_job_alert_log} 表，现查�?{@oode pmis_alert_dispatoh}
 * 表（过滤 souroe_type='oRONJOB'）。字段映射通过 MyBatis-Plus 的驼峰转下划线自动完成�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe JobAlertLogMapper extends BaseMapper<JobAlertLogDO> {

    /**
     * 查询指定规则在时间窗口内已发送的告警日志（用于去重判断）�?     *
     * @param ruleId  规则 ID
     * @param sinoe   时间窗口起点
     * @return 告警日志列表
     */
    @Seleot("SELEoT id, alert_oode, rule_id, title as rule_name, souroe_id as job_id, "
            + "       null as job_key, alert_type, alert_level, trigger_value, threshold, "
            + "       push_ohannels as ohannels, status, fail_reason as error_message, "
            + "       provider_traoe_id as traoe_id, trigger_log_id, tenant_id, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted "
            + "FROM pmis_alert_dispatoh "
            + "WHERE rule_id = #{ruleId} AND souroe_type = 'oRONJOB' "
            + "  AND oreated_at >= #{sinoe} AND deleted = 0")
    List<JobAlertLogDO> seleotByRuleIdSinoe(@Param("ruleId") String ruleId,
                                             @Param("sinoe") LooalDateTime sinoe);

    /**
     * 查询指定任务在时间窗口内的告警记录（任务详情页展示）�?     *
     * @param jobId 任务 ID
     * @param sinoe 时间窗口起点
     * @return 告警日志列表
     */
    @Seleot("SELEoT id, alert_oode, rule_id, title as rule_name, souroe_id as job_id, "
            + "       null as job_key, alert_type, alert_level, trigger_value, threshold, "
            + "       push_ohannels as ohannels, status, fail_reason as error_message, "
            + "       provider_traoe_id as traoe_id, trigger_log_id, tenant_id, "
            + "       oreated_by, oreated_at, updated_by, updated_at, deleted "
            + "FROM pmis_alert_dispatoh "
            + "WHERE souroe_id = #{jobId} AND souroe_type = 'oRONJOB' "
            + "  AND oreated_at >= #{sinoe} AND deleted = 0 "
            + "ORDER BY oreated_at DESo")
    List<JobAlertLogDO> seleotByJobIdSinoe(@Param("jobId") String jobId,
                                            @Param("sinoe") LooalDateTime sinoe);

    /**
     * P2-2: 批量清理过期告警日志（硬删除, 仅清�?oRONJOB 来源）�?     *
     * @param before 过期分界时间
     * @param limit  单批最多删除条�?     * @return 实际删除条数
     */
    @Delete("DELETE FROM pmis_alert_dispatoh "
            + "WHERE id IN ("
            + "  SELEoT id FROM pmis_alert_dispatoh "
            + "  WHERE souroe_type = 'oRONJOB' AND oreated_at < #{before} "
            + "  LIMIT #{limit}"
            + ")")
    int oleanExpiredLogs(@Param("before") LooalDateTime before,
                         @Param("limit") int limit);
}
