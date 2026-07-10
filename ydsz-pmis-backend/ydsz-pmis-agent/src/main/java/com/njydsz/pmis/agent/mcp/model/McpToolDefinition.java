package com.njydsz.pmis.agent.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具定义（P3-3 落地）。
 *
 * <p>由服务端 tools/list 返回，描述一个可被调用的工具：
 * <ul>
 *   <li>name - 工具名称（唯一标识）</li>
 *   <li>description - 工具描述</li>
 *   <li>inputSchema - 输入参数 JSON Schema</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P3-3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolDefinition {

    /** 工具名称（唯一标识，用于 tools/call） */
    private String name;

    /** 工具描述（展示给 LLM） */
    private String description;

    /** 输入参数 JSON Schema */
    private JsonNode inputSchema;

    /**
     * 从 inputSchema 中提取参数名到类型的映射。
     *
     * <p>JSON Schema 的 properties 中每个参数的 type 字段映射为 Java 类型字符串。
     * 用于生成 {@link com.njydsz.pmis.agent.tool.AgentTool#parameterSchema()}。
     *
     * @return 参数名 → 类型名字符串（如 "string" / "number" / "boolean"）
     */
    public Map<String, Class<?>> extractParameterSchema() {
        if (inputSchema == null || !inputSchema.has("properties")) {
            return Map.of();
        }
        JsonNode props = inputSchema.get("properties");
        Map<String, Class<?>> schema = new LinkedHashMap<>();
        props.properties().forEach(entry -> {
            String paramName = entry.getKey();
            JsonNode paramDef = entry.getValue();
            String typeStr = paramDef.has("type") ? paramDef.get("type").asText() : "string";
            schema.put(paramName, mapJsonSchemaTypeToJava(typeStr));
        });
        return schema;
    }

    /**
     * 将 JSON Schema 类型字符串映射为 Java Class。
     *
     * @param jsonType JSON Schema 类型（string/number/integer/boolean/array/object）
     * @return Java Class
     */
    private static Class<?> mapJsonSchemaTypeToJava(String jsonType) {
        if (jsonType == null) return String.class;
        return switch (jsonType) {
            case "string" -> String.class;
            case "number" -> Double.class;
            case "integer" -> Integer.class;
            case "boolean" -> Boolean.class;
            case "array" -> List.class;
            case "object" -> Map.class;
            default -> String.class;
        };
    }
}
