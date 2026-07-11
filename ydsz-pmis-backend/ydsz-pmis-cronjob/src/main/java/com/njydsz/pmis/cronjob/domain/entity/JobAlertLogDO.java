package com.njydsz.pmis.cronjob.domain.entity.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 任务告警日志实体（P5 告警 + 监控, P3-1-merge 重构）。
 *
 * <p>P3-1-merge: 原对应 {@code pmis_job_alert_log} 表，现已合并到 {@code pmis_alert_dispatch}
 * （source_type='CRONJOB'）。本实体映射到 pmis_alert_dispatch 表，新增字段（alert_code,
 * title, content, target_role, push_channels 等）在 cronjob 场景下由 AlertDispatcher 填充。
 *
 * <p>记录每次告警派发的实际情况，用于审计、去重判断和告警效果统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_alert_dispatch")
public class JobAlertLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 预警编码（cronjob 自动生成: CRONJOB-{timestamp}-{ruleId}） */
    private String alertCode;

    /** 触发源类型（cronjob 告警固定为 CRONJOB, P3-1-merge） */
    private String sourceType;

    /** 规则 ID（映射到 pmis_alert_dispatch.rule_id） */
    private String ruleId;

    /** 规则名称（映射到 pmis_alert_dispatch.title） */
    private String ruleName;

    /** 任务 ID（NULL 表示全局告警; 映射到 source_id） */
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

    /** 实际发送通道（逗号分隔: INAPP,EMAIL,DINGTALK） */
    private String channels;

    /** 告警状态: PENDING / SUCCESS / PARTIAL / FAILED / *_RECOVERY */
    private String status;

    /** 错误信息（部分通道失败时记录; 映射到 fail_reason） */
    private String errorMessage;

    /** 链路追踪 ID（映射到 provider_trace_id） */
    private String traceId;

    /** 触发该告警的任务日志 ID（关联 pmis_job_log.id） */
    private String triggerLogId;

    /** 租户 ID */
    private String tenantId;
}
