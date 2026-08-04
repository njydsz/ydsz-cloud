package com.remisoft.agent.domain.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * 工具调用描述（对标 OpenAI tool_calls）
 *
 * <p>表示 LLM 决定调用某个工具时产生的调用请求，包含工具名称和参数。
 *
 * <p><b>线程安全</b>：字段 final 且参数 Map 经不可变封装，不可变值对象，可安全跨线程传递。
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class ToolCall implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工具调用 ID */
    private final String id;
    /** 工具名称 */
    private final String name;
    /** 工具调用参数（key=参数名, value=参数值） */
    private final Map<String, Object> arguments;

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.name = Objects.requireNonNull(name, "name 不能为 null");
        this.arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Object> getArguments() { return arguments; }

    @Override
    public String toString() {
        return "ToolCall{name='" + name + "', args=" + arguments + "}";
    }
}
