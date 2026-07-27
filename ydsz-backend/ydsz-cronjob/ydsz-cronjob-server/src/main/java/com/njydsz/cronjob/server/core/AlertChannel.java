package com.njydsz.cronjob.server.core.alert;

/**
 * 告警通知通道枚举（P5 告警 + 监控）。
 *
 * <p>定义告警派发的实际通道，对应 {@code ydsz_job_alert_rule.channels} JSON 数组元素。
 * 每个通道由消息中心（{@link com.njydsz.common.feign.NotificationClient}）路由到具体通道实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum AlertChannel {

    /**
     * 邮件通知：适用于所有级别，承载详细信息（堆栈、上下文、统计图表链接）。
     */
    EMAIL,

    /**
     * 钉钉群机器人：Webhook 推送到钉钉群，适合团队即时通知。
     */
    DINGTALK,

    /**
     * 企业微信群机器人：Webhook 推送到企业微信群。
     */
    WECOM,

    /**
     * 飞书群机器人：Webhook 推送到飞书群（与钉钉/企微并列）。
     */
    FEISHU,

    /**
     * 自定义 Webhook：通用 HTTP 回调，由业务系统自行处理（如转发到 Slack、Teams）。
     */
    WEBHOOK,

    /**
     * 短信通知：仅用于 CRITICAL 级别，触发手机短信。
     */
    SMS;

    /**
     * 解析通知通道字符串，大小写不敏感。
     *
     * @param value 通道字符串
     * @return 解析后的枚举值；null 或无法识别时返回 null
     */
    public static AlertChannel parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AlertChannel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
