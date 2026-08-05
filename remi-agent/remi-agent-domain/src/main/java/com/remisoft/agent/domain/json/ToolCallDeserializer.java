package com.remisoft.agent.domain.json;

import java.util.Map;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.deserializer.JsonDeserializer;
import com.remisoft.common.json.reader.JSONReader;
import com.remisoft.agent.domain.model.ToolCall;

/**
 * {@link ToolCall} 的 RemiJson 自定义反序列化器（验证 P1-1 反序列化引擎修复）。
 *
 * <p>解析 OpenAI 工具调用结构，将 {@code function.arguments}（JSON 字符串）还原为
 * {@code Map<String, Object>}。与 {@link ToolCallSerializer} 互为逆操作。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class ToolCallDeserializer implements JsonDeserializer<ToolCall> {

    @Override
    @SuppressWarnings("unchecked")
    public ToolCall deserialize(JSONReader in) {
        String raw = in.readRawValue();
        Map<String, Object> m = RemiJson.fromJson(raw, Map.class);
        String id = (String) m.get("id");
        Map<String, Object> function = m.get("function") instanceof Map
                ? (Map<String, Object>) m.get("function") : null;
        String name = function != null ? (String) function.get("name") : null;
        Object argsRaw = function != null ? function.get("arguments") : null;
        Map<String, Object> arguments;
        if (argsRaw instanceof String) {
            arguments = RemiJson.fromJson((String) argsRaw, Map.class);
        } else if (argsRaw instanceof Map) {
            arguments = (Map<String, Object>) argsRaw;
        } else {
            arguments = Map.of();
        }
        return new ToolCall(id, name, arguments);
    }
}
