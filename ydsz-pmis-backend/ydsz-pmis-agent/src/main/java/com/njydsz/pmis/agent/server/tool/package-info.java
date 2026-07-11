/**
 * Agent 工具抽象层（P1-1 落地）
 *
 * <p>对标 Coze Plugin Store / Dify Tool Manager，为 ReAct 推理循环（P1-2）提供
 * 可被 LLM 调用的原子能力。
 *
 * <p>核心组件：
 * <ul>
 *   <li>{@link com.njydsz.pmis.agent.server.tool.AgentTool}       - 工具 SPI 接口</li>
 *   <li>{@link com.njydsz.pmis.agent.server.tool.ToolResult}      - 工具执行结果</li>
 *   <li>{@link com.njydsz.pmis.agent.server.tool.ToolRegistry}     - 工具注册中心（Spring 自动收集）</li>
 *   <li>{@code ProjectStatusTool}   - 查询项目指标（CPI/SPI/成本超支率）</li>
 *   <li>{@code RiskEventQueryTool}  - 查询项目风险事件列表</li>
 *   <li>{@code TimesheetStatTool}   - 查询工时异常统计</li>
 *   <li>{@code BpmnValidatorTool}   - 校验 BPMN XML 结构完整性</li>
 * </ul>
 *
 * <p>扩展方式：实现 {@link com.njydsz.pmis.agent.server.tool.AgentTool} + {@code @Component}
 * 即可被 {@link com.njydsz.pmis.agent.server.tool.ToolRegistry} 自动收集。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
package com.njydsz.pmis.agent.server.tool;
