package com.njydsz.agent.domain.json;

import java.util.LinkedHashMap;
import java.util.Map;

import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.deserializer.JsonDeserializer;
import com.njydsz.common.json.reader.JSONReader;

/**
 * {@link ToolCall} 的 YdszJson 自定义反序列化器（验证 P1-1 反序列化引擎修复）。
 *
 * <p>解析工具调用结构，将 {@code function.arguments}（JSON 字符串）还原为 {@code Map<String, Object>}。与
 * {@link ToolCallSerializer} 互为逆操作。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ToolCallDeserializer implements JsonDeserializer<ToolCall> {

  @Override
  public ToolCall deserialize(JSONReader in) {
    String raw = in.readRawValue();
    Map<String, Object> m = YdszJson.parseMap(raw);
    String id = (String) m.get("id");
    Object functionObj = m.get("function");
    Map<String, Object> function = toTypedMap(functionObj);
    String name = function != null ? (String) function.get("name") : null;
    Object argsRaw = function != null ? function.get("arguments") : null;
    Map<String, Object> arguments;
    if (argsRaw instanceof String s) {
      arguments = YdszJson.parseMap(s);
    } else {
      arguments = toTypedMap(argsRaw);
    }
    return new ToolCall(id, name, arguments);
  }

  /**
   * 将原始 Map 转为类型化 Map（JSON 对象键恒为 String，值恒为 Object，转换安全）。
   *
   * @param raw 原始对象
   * @return 类型化 Map，raw 不是 Map 时返回空 Map
   */
  private static Map<String, Object> toTypedMap(Object raw) {
    if (raw instanceof Map<?, ?> rawMap) {
      Map<String, Object> result = new LinkedHashMap<>(rawMap.size());
      for (Map.Entry<?, ?> e : rawMap.entrySet()) {
        result.put((String) e.getKey(), e.getValue());
      }
      return result;
    }
    return Map.of();
  }
}
