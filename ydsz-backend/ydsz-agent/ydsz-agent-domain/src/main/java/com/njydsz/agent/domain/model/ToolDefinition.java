package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * 工具定义（对标 OpenAI tools schema 中的 function definition）
 *
 * <p>描述一个可供 LLM 调用的工具，包含名称、描述和 JSON Schema 参数定义。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ToolDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工具名称 */
    private final String name;
    /** 工具描述（告诉 LLM 该工具的用途） */
    private final String description;
    /** 参数 JSON Schema 定义 */
    private final Map<String, Object> parametersSchema;

    public ToolDefinition(String name, String description, Map<String, Object> parametersSchema) {
        this.name = Objects.requireNonNull(name, "name 不能为 null");
        this.description = description;
        this.parametersSchema = parametersSchema != null ? Map.copyOf(parametersSchema) : Map.of();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Map<String, Object> getParametersSchema() { return parametersSchema; }

    @Override
    public String toString() {
        return "ToolDefinition{name='" + name + "', desc='" + description + "'}";
    }
}
