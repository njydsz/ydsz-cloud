package com.njydsz.pmis.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 聊天补全响应
 *
 * <p>对标 OpenAI Chat Completions API 响应体，包含助手回复消息和 Token 用量。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class ChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String model;
    private final ChatMessage message;
    private final TokenUsage usage;
    private final String finishReason;
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

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public String getContent() {
        return message != null ? message.getContent() : null;
    }

    @Override
    public String toString() {
        return "ChatResponse{id='" + id + "', model='" + model + "', finishReason='" + finishReason + "'}";
    }
}
