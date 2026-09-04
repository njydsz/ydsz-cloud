package com.njydsz.agent.domain.json;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * {@link ToolDefinition} 的 YdszJson 自定义序列化器（JsonModule SPI 落地）。
 *
 * <p>产出 tools 结构： {@code
 * {"type":"function","function":{"name":..,"description":..,"parameters":<json-schema>}}}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class ToolDefinitionSerializer implements JsonSerializer<ToolDefinition> {

  @Override
  public void serialize(ToolDefinition tool, JSONWriter out) {
    if (tool == null) {
      out.write("null");
      return;
    }
    out.write("{\"type\":\"function\",\"function\":{\"name\":");
    out.writeString(tool.getName());
    out.write(",\"description\":");
    out.writeString(tool.getDescription() != null ? tool.getDescription() : "");
    out.write(",\"parameters\":");
    out.write(
        tool.getParametersSchema() != null ? YdszJson.toJson(tool.getParametersSchema()) : "{}");
    out.write("}}");
  }
}
