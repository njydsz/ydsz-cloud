package com.njydsz.pmis.agent.domain.tool;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.pmis.agent.domain.model.ToolDefinition;

/**
 * 工具注册条目（内部数据结构）
 *
 * <p>将 {@link ToolDefinition}（元数据）与 {@link ToolExecutor}（执行器）绑定在一起。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class ToolRegistration {

    private final ToolDefinition definition;
    private final ToolExecutor executor;

    public ToolRegistration(ToolDefinition definition, ToolExecutor executor) {
        this.definition = definition;
        this.executor = executor;
    }

    public ToolDefinition getDefinition() { return definition; }
    public ToolExecutor getExecutor() { return executor; }

    public String getName() {
        return definition.getName();
    }

    /**
     * 快速创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private final Map<String, Object> parametersSchema = new HashMap<>();
        private ToolExecutor executor;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder addParameter(String paramName, String paramDesc, boolean required) {
            Map<String, Object> param = new HashMap<>();
            param.put("type", "string");
            param.put("description", paramDesc);
            param.put("required", required);
            parametersSchema.put(paramName, param);
            return this;
        }
        public Builder executor(ToolExecutor executor) { this.executor = executor; return this; }

        public ToolRegistration build() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            schema.put("properties", parametersSchema);
            ToolDefinition def = new ToolDefinition(name, description, schema);
            return new ToolRegistration(def, executor);
        }
    }
}
