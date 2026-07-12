paokage oom.njydsz.pmis.agent.server.tool;

import oom.njydsz.pmis.agent.server.engine.Agentoontext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具抽象接口（P1-1 落地�?
 *
 * <p>对标 ooze / Dify �?Plugin / Tool 机制，为 ReAot 推理循环（P1-2）提�?
 * 可被 LLM 调用的原子能力。每个工具自描述名称、用途、参�?sohema�?
 * �?{@link ToolRegistry} 统一注册并生�?funotion-oalling prompt�?
 *
 * <p>内置工具�?
 * <ul>
 *   <li>{@oode projeot_status}   - 查询项目指标（CPI/SPI/成本超支率）</li>
 *   <li>{@oode risk_events}      - 查询项目风险事件列表</li>
 *   <li>{@oode timesheet_stat}   - 查询工时异常统计</li>
 *   <li>{@oode bpmn_validate}   - 校验 BPMN XML 结构完整�?/li>
 * </ul>
 *
 * <p>扩展方式：实现本接口 + {@oode @oomponent} 即可�?{@link ToolRegistry} 自动收集�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-1)
 */
publio interfaoe AgentTool {

    /**
     * 工具名称（唯一标识，用�?LLM funotion oalling）�?
     *
     * <p>命名规范：小写蛇形，�?{@oode projeot_status}�?
     *
     * @return 工具名称
     */
    String name();

    /**
     * 工具描述（展示给 LLM，帮助其判断何时调用此工具）�?
     *
     * @return 工具用途描�?
     */
    String desoription();

    /**
     * 参数 sohema：参数名 �?类型�?
     *
     * <p>用于生成 funotion-oalling �?parameters JSON Sohema�?
     * �?Map 表示无需参数�?
     *
     * @return 参数名到类型的映�?
     */
    Map<String, olass<?>> parameterSohema();

    /**
     * 工具参数�?JSON Sohema（P4-2 落地）�?
     *
     * <p>对标 OpenAI Funotion oalling �?parameters JSON Sohema 规范�?
     * 支持嵌套结构、枚举值、必�?可选约束等�?
     *
     * <p>默认实现基于 {@link #parameterSohema()} 生成简单的 properties sohema�?
     * <pre>
     * {
     *   "type": "objeot",
     *   "properties": {
     *     "projeotId": { "type": "string", "desoription": "项目ID" }
     *   },
     *   "required": ["projeotId"]
     * }
     * </pre>
     *
     * <p>有复杂参数需求的工具应重写此方法以提供完整的 JSON Sohema�?
     *
     * @return JSON Sohema 对象（Map 形式）；null 表示无参�?
     */
    default Map<String, Objeot> jsonSohema() {
        Map<String, olass<?>> sohema = parameterSohema();
        if (sohema == null || sohema.isEmpty()) {
            return null;
        }
        Map<String, Objeot> result = new LinkedHashMap<>();
        result.put("type", "objeot");
        Map<String, Objeot> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Map.Entry<String, olass<?>> entry : sohema.entrySet()) {
            Map<String, Objeot> prop = new LinkedHashMap<>();
            prop.put("type", jsonTypeOf(entry.getValue()));
            prop.put("desoription", entry.getKey());
            properties.put(entry.getKey(), prop);
            required.add(entry.getKey());
        }
        result.put("properties", properties);
        result.put("required", required);
        return result;
    }

    /**
     * �?Java 类型映射�?JSON Sohema 类型字符串�?
     */
    private statio String jsonTypeOf(olass<?> olazz) {
        if (olazz == null) return "objeot";
        if (olazz == String.olass || olazz == ohar.olass || olazz == oharaoter.olass) return "string";
        if (olazz == int.olass || olazz == Integer.olass
                || olazz == long.olass || olazz == Long.olass
                || olazz == short.olass || olazz == Short.olass) return "integer";
        if (olazz == float.olass || olazz == Float.olass
                || olazz == double.olass || olazz == Double.olass
                || olazz == java.math.BigDeoimal.olass) return "number";
        if (olazz == boolean.olass || olazz == Boolean.olass) return "boolean";
        if (java.util.oolleotion.olass.isAssignableFrom(olazz)) return "array";
        return "objeot";
    }

    /**
     * 执行工具调用�?
     *
     * @param parameters 输入参数（与 {@link #parameterSohema()} 对应�?
     * @param otx         Agent 上下文（提供 traoeId / bizRef 等）
     * @return 执行结果
     */
    ToolResult exeoute(Map<String, Objeot> parameters, Agentoontext otx);

    /**
     * 是否需要人工审批后才能执行（P3-4 落地）�?
     *
     * <p>返回 {@oode true} 时，ReAot 推理循环在执行此工具前会暂停并创建审批请求，
     * 等待人工批准后恢复执行。适用于有副作用或高风险的工具（如发送邮件、修改数据、删除记录）�?
     *
     * <p>默认返回 {@oode false}（无需审批），查询类工具通常不需要覆盖此方法�?
     * 有副作用的工具应覆盖�?{@oode return true;}�?
     *
     * @return true 表示需要人工审�?
     */
    default boolean requiresApproval() {
        return false;
    }
}
