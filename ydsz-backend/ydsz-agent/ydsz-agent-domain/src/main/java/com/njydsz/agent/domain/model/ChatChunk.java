package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * LLM 流式响应片段（对标 OpenAI SSE chunk）
 *
 * <p>流式输出时每个 SSE 事件对应一个 ChatChunk，包含增量内容或工具调用增量。
 *
 * <p><b>线程安全</b>：全字段 final 且集合不可变，不可变值对象，可安全在流式回调与业务线程间共享。
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

    /**
     * 创建携带增量内容的流式片段。
     *
     * @param id    chunk 唯一标识（SSE 事件 id）
     * @param model 模型名称
     * @param delta 增量文本内容
     * @return 内容型流式片段
     */
    public static ChatChunk content(String id, String model, String delta) {
        return new ChatChunk(id, model, delta, null, null, null);
    }

    /**
     * 创建标识流结束的终止片段。
     *
     * @param id            chunk 唯一标识
     * @param model         模型名称
     * @param finishReason  结束原因（如 stop / length / tool_calls）
     * @param usage         本次请求累计 Token 用量
     * @return 终止型流式片段
     */
    public static ChatChunk finish(String id, String model, String finishReason, TokenUsage usage) {
        return new ChatChunk(id, model, null, null, finishReason, usage);
    }

    public String getId() { return id; }
    public String getModel() { return model; }
    public String getDeltaContent() { return deltaContent; }
    public List<ToolCall> getDeltaToolCalls() { return deltaToolCalls; }
    public String getFinishReason() { return finishReason; }
    public TokenUsage getUsage() { return usage; }

    /**
     * 判断流式响应是否已结束。
     *
     * <p>以 {@code finishReason} 是否非空判定流结束，与 OpenAI SSE 约定一致；
     * {@code null} 表示后续仍有 chunk 到达。</p>
     *
     * @return {@code true} 表示流已结束（收到终止片段）
     */
    public boolean isFinished() {
        // 以 finishReason 是否非空判定流结束，与 OpenAI SSE 约定一致；null 表示仍有后续 chunk
        return finishReason != null;
    }

    /**
     * 判断该片段是否携带增量文本内容。
     *
     * @return {@code true} 表示 {@code deltaContent} 非空
     */
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
