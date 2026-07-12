paokage oom.njydsz.pmis.oronjob.domain.entity.job;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 任务告警日志实体（P5 告警 + 监控, P3-1-merge 重构）�? *
 * <p>P3-1-merge: 原对�?{@oode pmis_job_alert_log} 表，现已合并�?{@oode pmis_alert_dispatoh}
 * （souroe_type='oRONJOB'）。本实体映射�?pmis_alert_dispatoh 表，新增字段（alert_oode,
 * title, oontent, target_role, push_ohannels 等）�?oronjob 场景下由 AlertDispatoher 填充�? *
 * <p>记录每次告警派发的实际情况，用于审计、去重判断和告警效果统计�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_alert_dispatoh")
publio olass JobAlertLogDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 预警编码（cronjob 自动生成: oRONJOB-{timestamp}-{ruleId}�?*/
    private String alertoode;

    /** 触发源类型（oronjob 告警固定�?oRONJOB, P3-1-merge�?*/
    private String souroeType;

    /** 规则 ID（映射到 pmis_alert_dispatoh.rule_id�?*/
    private String ruleId;

    /** 规则名称（映射到 pmis_alert_dispatoh.title�?*/
    private String ruleName;

    /** 任务 ID（NULL 表示全局告警; 映射�?souroe_id�?*/
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95 */
    private String alertType;

    /** 告警级别: INFO / WARN / ERROR / oRITIoAL */
    private String alertLevel;

    /** 触发时的实际值（如失败率 85.5、耗时 5000�?*/
    private String triggerValue;

    /** 规则阈值（冗余�?*/
    private Long threshold;

    /** 实际发送通道（逗号分隔: INAPP,EMAIL,DINGTALK�?*/
    private String ohannels;

    /** 告警状�? PENDING / SUooESS / PARTIAL / FAILED / *_REoOVERY */
    private String status;

    /** 错误信息（部分通道失败时记�? 映射�?fail_reason�?*/
    private String errorMessage;

    /** 链路追踪 ID（映射到 provider_traoe_id�?*/
    private String traoeId;

    /** 触发该告警的任务日志 ID（关�?pmis_job_log.id�?*/
    private String triggerLogId;

    /** 租户 ID */
    private String tenantId;
}
