package com.njydsz.pmis.message.enums;

/**
 * 消息发送通道枚举（8 通道统一抽象）。
 *
 * <p>对应 SQL {@code pmis_msg_log.channel} 与 {@code pmis_msg_template.channel} 的 CHECK 约束取值。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum MessageChannelEnum {

    /** 短信 */
    SMS,
    /** 邮件 */
    EMAIL,
    /** App 推送 */
    PUSH,
    /** 站内信 */
    INAPP,
    /** Webhook */
    WEBHOOK,
    /** 钉钉群机器人 */
    DINGTALK,
    /** 企业微信群机器人 */
    WECOM,
    /** 飞书群机器人 */
    FEISHU;

    /**
     * 安全解析通道字符串（大小写无关），非法时抛出 IllegalArgumentException。
     *
     * @param value 通道字符串
     * @return 通道枚举
     */
    public static MessageChannelEnum parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("消息通道不能为空");
        }
        try {
            return MessageChannelEnum.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的消息通道: " + value);
        }
    }
}
