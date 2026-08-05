package com.remisoft.agent.domain.json;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.serializer.JsonSerializer;
import com.remisoft.common.json.writer.JSONWriter;
import com.remisoft.agent.domain.model.ChatMessage;
import com.remisoft.agent.domain.model.ToolCall;

/**
 * {@link ChatMessage} 的 RemiJson 自定义序列化器（JsonModule SPI 落地 + OpenAI message 形状）。
 *
 * <p>产出 OpenAI message 结构：{@code {"role":<apiValue>,"content":..,"tool_calls":[...]?,"tool_call_id":..?}}。
 * 仅输出 LLM API 需要的字段（role / content / tool_calls / tool_call_id），
 * 不包含 {@code createdAt} / {@code conversationId} / {@code tokenUsage} 等内部字段。
 * 嵌套 {@link ToolCall} 通过 {@link RemiJson#toJson(Object)} 委托给 {@link ToolCallSerializer}，
 * 走全局引擎路径后自动命中已注册的自定义序列化器。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class ChatMessageSerializer implements JsonSerializer<ChatMessage> {

    @Override
    public void serialize(ChatMessage msg, JSONWriter out) {
        if (msg == null) {
            out.write("null");
            return;
        }
        out.write("{\"role\":");
        out.writeString(msg.getRole().getApiValue());
        out.write(",\"content\":");
        out.writeString(msg.getContent() != null ? msg.getContent() : "");
        if (msg.hasToolCalls()) {
            out.write(",\"tool_calls\":[");
            boolean first = true;
            for (ToolCall tc : msg.getToolCalls()) {
                if (!first) {
                    out.write(",");
                }
                first = false;
                // 委托全局引擎 -> 命中 ToolCallSerializer
                out.write(RemiJson.toJson(tc));
            }
            out.write("]");
        }
        if (msg.getToolCallId() != null) {
            out.write(",\"tool_call_id\":");
            out.writeString(msg.getToolCallId());
        }
        out.write("}");
    }
}
