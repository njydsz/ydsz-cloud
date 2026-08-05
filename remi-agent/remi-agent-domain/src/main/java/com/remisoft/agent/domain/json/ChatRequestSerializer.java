package com.remisoft.agent.domain.json;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.serializer.JsonSerializer;
import com.remisoft.common.json.writer.JSONWriter;
import com.remisoft.agent.domain.model.ChatRequest;

/**
 * {@link ChatRequest} 的 RemiJson 自定义序列化器（JsonModule SPI 落地 + OpenAI 请求体形状）。
 *
 * <p>产出 OpenAI Chat Completions 请求体（snake_case）：
 * {@code {"model":..,"temperature":..,"max_tokens":..,"top_p":..,"stream":..,"stop"?:[..],"messages":[..],"tools"?:[..],"tool_choice"?:..}}。
 * 嵌套 {@code messages} / {@code tools} 委托全局引擎，分别命中 {@link ChatMessageSerializer} /
 * {@link ToolDefinitionSerializer}。
 *
 * <p><b>注意：</b>{@code stream_options} 由 {@code OpenAiCompatibleClient#buildRequestBody} 按调用态
 * 动态附加（ChatRequest 不持有 stream 标志），故本序列化器不输出该字段。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class ChatRequestSerializer implements JsonSerializer<ChatRequest> {

    @Override
    public void serialize(ChatRequest req, JSONWriter out) {
        if (req == null) {
            out.write("null");
            return;
        }
        out.write("{\"model\":");
        out.writeString(req.getModel());
        out.write(",\"temperature\":");
        out.writeDouble(req.getTemperature());
        out.write(",\"max_tokens\":");
        out.writeInt(req.getMaxTokens());
        out.write(",\"top_p\":");
        out.writeDouble(req.getTopP());
        out.write(",\"stream\":");
        out.write(req.isStream() ? "true" : "false");
        if (!req.getStop().isEmpty()) {
            out.write(",\"stop\":");
            out.write(RemiJson.toJson(req.getStop()));
        }
        out.write(",\"messages\":");
        out.write(RemiJson.toJson(req.getMessages()));
        if (!req.getTools().isEmpty()) {
            out.write(",\"tools\":");
            out.write(RemiJson.toJson(req.getTools()));
        }
        if (req.getToolChoice() != null) {
            out.write(",\"tool_choice\":");
            out.writeString(req.getToolChoice());
        }
        out.write("}");
    }
}
