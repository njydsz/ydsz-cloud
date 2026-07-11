package com.njydsz.pmis.agent.server.tool;

import com.njydsz.pmis.agent.server.engine.AgentContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具抽象接口（P1-1 落地）
 *
 * <p>对标 Coze / Dify 的 Plugin / Tool 机制，为 ReAct 推理循环（P1-2）提供
 * 可被 LLM 调用的原子能力。每个工具自描述名称、用途、参数 schema，
 * 由 {@link ToolRegistry} 统一注册并生成 function-calling prompt。
 *
 * <p>内置工具：
 * <ul>
 *   <li>{@code project_status}   - 查询项目指标（CPI/SPI/成本超支率）</li>
 *   <li>{@code risk_events}      - 查询项目风险事件列表</li>
 *   <li>{@code timesheet_stat}   - 查询工时异常统计</li>
 *   <li>{@code bpmn_validate}   - 校验 BPMN XML 结构完整性</li>
 * </ul>
 *
 * <p>扩展方式：实现本接口 + {@code @Component} 即可被 {@link ToolRegistry} 自动收集。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
public interface AgentTool {

    /**
     * 工具名称（唯一标识，用于 LLM function calling）。
     *
     * <p>命名规范：小写蛇形，如 {@code project_status}。
     *
     * @return 工具名称
     */
    String name();

    /**
     * 工具描述（展示给 LLM，帮助其判断何时调用此工具）。
     *
     * @return 工具用途描述
     */
    String description();

    /**
     * 参数 schema：参数名 → 类型。
     *
     * <p>用于生成 function-calling 的 parameters JSON Schema。
     * 空 Map 表示无需参数。
     *
     * @return 参数名到类型的映射
     */
    Map<String, Class<?>> parameterSchema();

    /**
     * 工具参数的 JSON Schema（P4-2 落地）。
     *
     * <p>对标 OpenAI Function Calling 的 parameters JSON Schema 规范，
     * 支持嵌套结构、枚举值、必填/可选约束等。
     *
     * <p>默认实现基于 {@link #parameterSchema()} 生成简单的 properties schema：
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "projectId": { "type": "string", "description": "项目ID" }
     *   },
     *   "required": ["projectId"]
     * }
     * </pre>
     *
     * <p>有复杂参数需求的工具应重写此方法以提供完整的 JSON Schema。
     *
     * @return JSON Schema 对象（Map 形式）；null 表示无参数
     */
    default Map<String, Object> jsonSchema() {
        Map<String, Class<?>> schema = parameterSchema();
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Map.Entry<String, Class<?>> entry : schema.entrySet()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", jsonTypeOf(entry.getValue()));
            prop.put("description", entry.getKey());
            properties.put(entry.getKey(), prop);
            required.add(entry.getKey());
        }
        result.put("properties", properties);
        result.put("required", required);
        return result;
    }

    /**
     * 将 Java 类型映射为 JSON Schema 类型字符串。
     */
    private static String jsonTypeOf(Class<?> clazz) {
        if (clazz == null) return "object";
        if (clazz == String.class || clazz == char.class || clazz == Character.class) return "string";
        if (clazz == int.class || clazz == Integer.class
                || clazz == long.class || clazz == Long.class
                || clazz == short.class || clazz == Short.class) return "integer";
        if (clazz == float.class || clazz == Float.class
                || clazz == double.class || clazz == Double.class
                || clazz == java.math.BigDecimal.class) return "number";
        if (clazz == boolean.class || clazz == Boolean.class) return "boolean";
        if (java.util.Collection.class.isAssignableFrom(clazz)) return "array";
        return "object";
    }

    /**
     * 执行工具调用。
     *
     * @param parameters 输入参数（与 {@link #parameterSchema()} 对应）
     * @param ctx         Agent 上下文（提供 traceId / bizRef 等）
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> parameters, AgentContext ctx);

    /**
     * 是否需要人工审批后才能执行（P3-4 落地）。
     *
     * <p>返回 {@code true} 时，ReAct 推理循环在执行此工具前会暂停并创建审批请求，
     * 等待人工批准后恢复执行。适用于有副作用或高风险的工具（如发送邮件、修改数据、删除记录）。
     *
     * <p>默认返回 {@code false}（无需审批），查询类工具通常不需要覆盖此方法。
     * 有副作用的工具应覆盖为 {@code return true;}。
     *
     * @return true 表示需要人工审批
     */
    default boolean requiresApproval() {
        return false;
    }
}
