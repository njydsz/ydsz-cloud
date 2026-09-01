package com.njydsz.agent.domain.trigger;

/**
 * Agent 触发器类型枚举。
 *
 * <p>定义 Agent 可被外部事件触发执行的类型。
 * 借鉴 MateClaw 的 Triggers 系统（6 种模式：cron / webhook / channel_message /
 * agent_lifecycle / content_match / workflow_completion），
 * 并扩展了 Agent 特有的生命周期触发。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public enum TriggerType {

    /**
     * Cron 定时触发 — 按 Cron 表达式定期执行 Agent。
     */
    CRON("cron", "定时触发"),

    /**
     * Webhook 触发 — 接收外部 HTTP 回调触发 Agent。
     */
    WEBHOOK("webhook", "Webhook 触发"),

    /**
     * 渠道消息触发 — IM 渠道消息到达时触发。
     */
    CHANNEL_MESSAGE("channel_message", "渠道消息触发"),

    /**
     * Agent 生命周期触发 — 另一 Agent 完成/失败时触发。
     */
    AGENT_LIFECYCLE("agent_lifecycle", "Agent 生命周期触发"),

    /**
     * 内容匹配触发 — 消息内容匹配指定规则时触发。
     */
    CONTENT_MATCH("content_match", "内容匹配触发"),

    /**
     * 工作流完成触发 — 关联工作流执行完成后触发。
     */
    WORKFLOW_COMPLETION("workflow_completion", "工作流完成触发");

    private final String code;
    private final String desc;

    TriggerType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据 code 值查找对应的触发器类型。
     *
     * @param code 类型码字符串
     * @return 对应的枚举值，未找到返回 null
     */
    public static TriggerType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TriggerType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
