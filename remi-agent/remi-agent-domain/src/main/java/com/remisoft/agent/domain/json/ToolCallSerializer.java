package com.remisoft.agent.domain.json;

import com.remisoft.common.json.YdszJson;
import com.remisoft.common.json.serializer.JsonSerializer;
import com.remisoft.common.json.writer.JSONWriter;
import com.remisoft.agent.domain.model.ToolCall;

/**
 * {@link ToolCall} 的 YdszJson 自定义序列化器（JsonModule SPI 落地 + OpenAI tool_calls 形状）。
 *
 * <p>产出 OpenAI 工具调用结构：{@code {"id":..,"type":"function","function":{"name":..,"arguments":<json-string>}}}。
 * 注意 {@code arguments} 按 OpenAI 契约序列化为「JSON 字符串」（而非嵌套对象）。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class ToolCallSerializer implements JsonSerializer<ToolCall> {

    @Override
    public void serialize(ToolCall tc, JSONWriter out) {
        if (tc == null) {
            out.write("null");
            return;
        }
        out.write("{\"id\":");
        out.writeString(tc.getId());
        out.write(",\"type\":\"function\"");
        out.write(",\"function\":{\"name\":");
        out.writeString(tc.getName());
        out.write(",\"arguments\":");
        // 按 OpenAI 契约：arguments 为 JSON 字符串
        out.writeString(tc.getArguments() != null ? YdszJson.toJson(tc.getArguments()) : "{}");
        out.write("}}");
    }
}
