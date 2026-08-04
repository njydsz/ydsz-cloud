package com.remisoft.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 聊天补全响应
 *
 * <p>对标 OpenAI Chat Completions API 响应体，包含助手回复消息和 Token 用量。
 *
 * <p><b>线程安全</b>：全字段 final 且集合不可变，不可变值对象，可安全跨线程共享。
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class ChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 响应 ID */
    private final String id;
    /** 实际使用的模型名称 */
    private final String model;
    /** 助手回复消息 */
    private final ChatMessage message;
    /** Token 用量统计 */
    private final TokenUsage usage;
    /** 结束原因（stop/length/tool_calls/content_filter） */
    private final String finishReason;
    /** 工具调用列表 */
    private final List<ToolCall> toolCalls;

    public ChatResponse(String id, String model, ChatMessage message, TokenUsage usage,
                        String finishReason, List<ToolCall> toolCalls) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.model = model;
        this.message = message;
        this.usage = usage != null ? usage : TokenUsage.zero();
        this.finishReason = finishReason;
        this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
    }

    public String getId() { return id; }
    public String getModel() { return model; }
    public ChatMessage getMessage() { return message; }
    public TokenUsage getUsage() { return usage; }
    public String getFinishReason() { return finishReason; }
    public List<ToolCall> getToolCalls() { return toolCalls; }

    /**
     * 判断响应是否携带工具调用。
     *
     * @return {@code true} 表示助手回复中包含至少一个工具调用
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * 获取助手回复的文本内容。
     *
     * <p>当响应为异常兜底或纯工具调用场景时，{@code message} 可能为
     * {@code null}，此时返回 {@code null} 而非抛出 NPE，调用方须判空。</p>
     *
     * @return 回复文本内容；消息缺失时返回 {@code null}
     */
    public String getContent() {
        // 消息可能为 null（异常兜底场景），返回 null 而非抛 NPE，调用方须判空
        return message != null ? message.getContent() : null;
    }

    @Override
    public String toString() {
        return "ChatResponse{id='" + id + "', model='" + model + "', finishReason='" + finishReason + "'}";
    }
}
