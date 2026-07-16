package com.njydsz.agent.domain.model;

/**
 * 消息角色枚举（对标 OpenAI Chat Completions message.role）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum MessageRole {

    /** 系统指令（设定 Agent 人设/行为约束） */
    SYSTEM("system"),

    /** 用户消息 */
    USER("user"),

    /** 助手回复 */
    ASSISTANT("assistant"),

    /** 工具调用结果 */
    TOOL("tool");

    private final String apiValue;

    MessageRole(String apiValue) {
        this.apiValue = apiValue;
    }

    public String getApiValue() {
        return apiValue;
    }

    public static MessageRole fromApiValue(String value) {
        for (MessageRole role : values()) {
            if (role.apiValue.equalsIgnoreCase(value)) {
                return role;
            }
        }
        return USER;
    }
}
