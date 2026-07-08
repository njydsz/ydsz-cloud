package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;

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
