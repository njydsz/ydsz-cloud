package com.njydsz.agent.domain.tool;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.agent.domain.model.ToolDefinition;

/**
 * 工具注册条目（内部数据结构）
 *
 * <p>将 {@link ToolDefinition}（元数据）与 {@link ToolExecutor}（执行器）绑定在一起。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ToolRegistration {

    /** 工具定义（元数据） */
    private final ToolDefinition definition;
    /** 工具执行器 */
    private final ToolExecutor executor;

    public ToolRegistration(ToolDefinition definition, ToolExecutor executor) {
        this.definition = definition;
        this.executor = executor;
    }

    public ToolDefinition getDefinition() { return definition; }
    public ToolExecutor getExecutor() { return executor; }

    /**
     * 获取工具名称（委托给工具定义）。
     *
     * @return 工具名称字符串
     */
    public String getName() {
        return definition.getName();
    }

    /**
     * 快速创建 Builder。
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ToolRegistration 构建器。
     *
     * <p>简化工具注册流程：名称 + 描述 + 入参声明 + 执行器，
     * 内部自动组装 {@link ToolDefinition}。受简化约定限制，
     * 入参类型固定为 string，复杂类型请直接构造 {@link ToolDefinition}。</p>
     */
    public static class Builder {
        private String name;
        private String description;
        private final Map<String, Object> parametersSchema = new HashMap<>();
        private ToolExecutor executor;

        /** 设置工具名，需在注册表内全局唯一；LLM 依据该名称发起 Function Calling，命名建议用小写下划线。 */
        public Builder name(String name) { this.name = name; return this; }
        /** 设置工具用途描述，该文本会随 tools 定义发给 LLM，直接影响模型的选择准确率，应写清适用场景与边界。 */
        public Builder description(String description) { this.description = description; return this; }

        /**
         * 追加一个工具入参声明。
         *
         * <p>同名参数后注册者覆盖先注册者。受 Builder 简化约定限制，
         * 参数类型固定生成为 {@code string}；若需要 object / number / array 等复杂类型，
         * 应绕过本 Builder 直接构造 {@link ToolDefinition}。
         *
         * @param paramName 参数名，作为 JSON Schema properties 的 key，不可为 {@code null}
         * @param paramDesc 参数语义描述，会随 schema 发给 LLM，描述越准确模型填参越可靠
         * @param required  是否为必填参数，仅作为提示信息写入 schema，不做本地强校验
         * @return 当前 Builder，便于链式调用
         */
        public Builder addParameter(String paramName, String paramDesc, boolean required) {
        Map<String, Object> param = new HashMap<>();
        // 当前 Builder 仅支持 string 类型参数（简化约定）；如需 object/number 等复杂类型应直接构造 ToolDefinition
        param.put("type", "string");
            param.put("description", paramDesc);
            param.put("required", required);
            parametersSchema.put(paramName, param);
            return this;
        }
        /** 绑定实际执行逻辑；执行器会被 Agent 在工具调用线程上直接调用，实现需自行保证线程安全与超时控制。 */
        public Builder executor(ToolExecutor executor) { this.executor = executor; return this; }

        /**
         * 组装并构建 ToolRegistration 实例。
         *
         * @return 绑定定义与执行器的注册条目
         */
        public ToolRegistration build() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            schema.put("properties", parametersSchema);
            ToolDefinition def = new ToolDefinition(name, description, schema);
            return new ToolRegistration(def, executor);
        }
    }
}
