package com.njydsz.agent.domain.channel;

/**
 * 渠道类型枚举。
 *
 * <p>定义 Agent 支持的消息渠道类型。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
public enum ChannelType {

    /** Web 页面渠道 */
    WEB("web", "Web 页面"),

    /** API 接口渠道 */
    API("api", "API 接口"),

    /** Webhook 渠道 */
    WEBHOOK("webhook", "Webhook"),

    /** 消息队列渠道 */
    MESSAGE_QUEUE("mq", "消息队列"),

    /** 定时任务渠道 */
    SCHEDULED("scheduled", "定时任务");

    private final String code;
    private final String description;

    ChannelType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码查找枚举。
     *
     * @param code 代码
     * @return 对应枚举，未找到返回 null
     */
    public static ChannelType fromCode(String code) {
        for (ChannelType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
