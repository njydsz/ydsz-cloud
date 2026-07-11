package com.njydsz.pmis.agent.server.engine.memory;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单条对话消息（P1-3 落地）
 *
 * <p>对标 LangChain ChatMessage / OpenAI Chat Completion Message，
 * 用于在 {@link ChatMemory} 中存储多轮对话历史。
 *
 * <p>角色定义：
 * <ul>
 *   <li>{@link Role#SYSTEM}    - 系统提示词（角色设定）</li>
 *   <li>{@link Role#USER}      - 用户输入</li>
 *   <li>{@link Role#ASSISTANT} - LLM 回复</li>
 *   <li>{@link Role#TOOL}     - 工具执行结果（Observation）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
@Data
public class ChatMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息角色 */
    private Role role;

    /** 消息内容 */
    private String content;

    /** Token 估算数（由 {@link TokenCounter} 计算后填充） */
    private int tokenCount;

    /** 时间戳（毫秒） */
    private long timestamp;

    public ChatMessage() {
    }

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    /** 构造系统消息 */
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    /** 构造用户消息 */
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    /** 构造助手消息 */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    /** 构造工具消息 */
    public static ChatMessage tool(String content) {
        return new ChatMessage(Role.TOOL, content);
    }

    /**
     * 消息角色枚举。
     *
     * <p>对齐 OpenAI Chat Completion 协议的 role 字段。
     */
    public enum Role {
        /** 系统提示词 */
        SYSTEM,
        /** 用户输入 */
        USER,
        /** LLM 回复 */
        ASSISTANT,
        /** 工具执行结果 */
        TOOL
    }
}
