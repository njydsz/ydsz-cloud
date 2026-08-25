package com.njydsz.agent.domain.json;

import java.util.Map;

import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.deserializer.JsonDeserializer;
import com.njydsz.common.json.reader.JSONReader;

/**
 * {@link ToolCall} 的 YdszJson 自定义反序列化器（验证 P1-1 反序列化引擎修复）。
 *
 * <p>解析 OpenAI 工具调用结构，将 {@code function.arguments}（JSON 字符串）还原为 {@code Map<String, Object>}。与
 * {@link ToolCallSerializer} 互为逆操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ToolCallDeserializer implements JsonDeserializer<ToolCall> {

  @Override
  public ToolCall deserialize(JSONReader in) {
    String raw = in.readRawValue();
    Map<String, Object> m = YdszJson.fromJsonToMap(raw, String.class, Object.class);
    String id = (String) m.get("id");
    Object functionObj = m.get("function");
    Map<String, Object> function = functionObj instanceof Map ? Map.class.cast(functionObj) : null;
    String name = function != null ? (String) function.get("name") : null;
    Object argsRaw = function != null ? function.get("arguments") : null;
    Map<String, Object> arguments;
    if (argsRaw instanceof String) {
      arguments = YdszJson.fromJson((String) argsRaw, Map.class);
    } else if (argsRaw instanceof Map) {
      Map<String, Object> castArgs = Map.class.cast(argsRaw);
      arguments = castArgs;
    } else {
      arguments = Map.of();
    }
    return new ToolCall(id, name, arguments);
  }
}
