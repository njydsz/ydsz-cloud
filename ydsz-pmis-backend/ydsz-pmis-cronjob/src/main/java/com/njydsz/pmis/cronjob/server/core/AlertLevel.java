package com.njydsz.pmis.cronjob.server.core.alert;

/**
 * 告警级别枚举（P5 告警 + 监控）。
 *
 * <p>定义告警严重程度，对应 {@code pmis_job_alert_rule.alert_level} 字段。
 * 级别越高，通知通道越激进（如 CRITICAL 触发电话/短信，INFO 仅邮件）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum AlertLevel {

    /**
     * 提示级别：仅邮件通知，用于低影响事件（如任务首次慢执行）。
     */
    INFO,

    /**
     * 警告级别：邮件 + IM 通知，用于需要关注但不紧急的事件（默认级别）。
     */
    WARN,

    /**
     * 错误级别：邮件 + IM + 短信，用于影响业务但可恢复的事件（如任务连续失败）。
     */
    ERROR,

    /**
     * 严重级别：全部通道（含电话），用于影响核心业务的紧急事件（如 Leader 失联、关键任务长时间不可用）。
     */
    CRITICAL;

    /**
     * 解析告警级别字符串，大小写不敏感。
     *
     * @param value 告警级别字符串
     * @return 解析后的枚举值；null 或无法识别时返回 {@link #WARN}
     */
    public static AlertLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return WARN;
        }
        try {
            return AlertLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return WARN;
        }
    }
}
