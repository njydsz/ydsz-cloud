package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 流式响应片段（对标 OpenAI SSE chunk）
 *
 * <p>流式输出时每个 SSE 事件对应一个 ChatChunk，包含增量内容或工具调用增量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ChatChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String model;
    private final String deltaContent;
    private final List<ToolCall> deltaToolCalls;
    private final String finishReason;
    private final TokenUsage usage;

    public ChatChunk(String id, String model, String deltaContent,
                     List<ToolCall> deltaToolCalls, String finishReason, TokenUsage usage) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.model = model;
        this.deltaContent = deltaContent;
        this.deltaToolCalls = deltaToolCalls != null ? List.copyOf(deltaToolCalls) : List.of();
        this.finishReason = finishReason;
        this.usage = usage;
    }

    public static ChatChunk content(String id, String model, String delta) {
        return new ChatChunk(id, model, delta, null, null, null);
    }

    public static ChatChunk finish(String id, String model, String finishReason, TokenUsage usage) {
        return new ChatChunk(id, model, null, null, finishReason, usage);
    }

    public String getId() { return id; }
    public String getModel() { return model; }
    public String getDeltaContent() { return deltaContent; }
    public List<ToolCall> getDeltaToolCalls() { return deltaToolCalls; }
    public String getFinishReason() { return finishReason; }
    public TokenUsage getUsage() { return usage; }

    public boolean isFinished() {
        // 以 finishReason 是否非空判定流结束，与 OpenAI SSE 约定一致；null 表示仍有后续 chunk
        return finishReason != null;
    }

    public boolean hasContent() {
        return deltaContent != null && !deltaContent.isEmpty();
    }

    @Override
    public String toString() {
        return "ChatChunk{delta='" +
                (deltaContent != null && deltaContent.length() > 50 ? deltaContent.substring(0, 50) + "..." : deltaContent) +
                "', finished=" + isFinished() + "}";
    }
}
