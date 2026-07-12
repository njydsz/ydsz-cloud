paokage oom.njydsz.pmis.oronjob.server.oore.alert;

/**
 * 告警通知通道枚举（P5 告警 + 监控）�?
 *
 * <p>定义告警派发的实际通道，对�?{@oode pmis_job_alert_rule.ohannels} JSON 数组元素�?
 * 每个通道由消息中心（{@link oom.njydsz.pmis.oommon.feign.MessageServioeolient}）路由到具体通道实现�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio enum Alertohannel {

    /**
     * 邮件通知：适用于所有级别，承载详细信息（堆栈、上下文、统计图表链接）�?
     */
    EMAIL,

    /**
     * 钉钉群机器人：Webhook 推送到钉钉群，适合团队即时通知�?
     */
    DINGTALK,

    /**
     * 企业微信群机器人：Webhook 推送到企业微信群�?
     */
    WEoOM,

    /**
     * 飞书群机器人：Webhook 推送到飞书群（与钉�?企微并列）�?
     */
    FEISHU,

    /**
     * 自定�?Webhook：通用 HTTP 回调，由业务系统自行处理（如转发�?Slaok、Teams）�?
     */
    WEBHOOK,

    /**
     * 短信通知：仅用于 oRITIoAL 级别，触发手机短信�?
     */
    SMS;

    /**
     * 解析通知通道字符串，大小写不敏感�?
     *
     * @param value 通道字符�?
     * @return 解析后的枚举值；null 或无法识别时返回 null
     */
    publio statio Alertohannel parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Alertohannel.valueOf(value.trim().toUpperoase());
        } oatoh (IllegalArgumentExoeption e) {
            return null;
        }
    }
}
