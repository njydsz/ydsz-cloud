package com.remisoft.agent.domain.json;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.serializer.JsonSerializer;
import com.remisoft.common.json.writer.JSONWriter;
import com.remisoft.agent.domain.model.ToolDefinition;

/**
 * {@link ToolDefinition} 的 RemiJson 自定义序列化器（JsonModule SPI 落地 + OpenAI tools 形状）。
 *
 * <p>产出 OpenAI tools 结构：
 * {@code {"type":"function","function":{"name":..,"description":..,"parameters":<json-schema>}}}。
 * {@code parameters} 字段名（单数）直接对应 OpenAI 契约，而非 Java 字段名 {@code parametersSchema}。</p>
 *
 * @author remi-team
 * @since 1.0.0
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
        out.write(tool.getParametersSchema() != null ? RemiJson.toJson(tool.getParametersSchema()) : "{}");
        out.write("}}");
    }
}
