package com.njydsz.pmis.cronjob.entity.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务告警日志实体（pmis_job_alert_log 表，P5 告警 + 监控）。
 *
 * <p>记录每次告警派发的实际情况，用于审计、去重判断和告警效果统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job_alert_log")
public class JobAlertLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 规则 ID */
    private String ruleId;

    /** 规则名称（冗余，避免连表） */
    private String ruleName;

    /** 任务 ID（NULL 表示全局告警） */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95 */
    private String alertType;

    /** 告警级别: INFO / WARN / ERROR / CRITICAL */
    private String alertLevel;

    /** 触发时的实际值（如失败率 85.5、耗时 5000） */
    private String triggerValue;

    /** 规则阈值（冗余） */
    private Long threshold;

    /** 实际发送通道（JSON 数组） */
    private String channels;

    /** 告警状态: PENDING / SUCCESS / PARTIAL / FAILED */
    private String status;

    /** 错误信息（部分通道失败时记录） */
    private String errorMessage;

    /** 链路追踪 ID */
    private String traceId;

    /** 触发该告警的任务日志 ID（关联 pmis_job_log.id） */
    private String triggerLogId;

    /** 租户 ID */
    private String tenantId;
}
