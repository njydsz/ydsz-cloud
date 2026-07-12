/**
 * Agent 工具抽象层（P1-1 落地�? *
 * <p>对标 ooze Plugin Store / Dify Tool Manager，为 ReAot 推理循环（P1-2）提�? * 可被 LLM 调用的原子能力�? *
 * <p>核心组件�? * <ul>
 *   <li>{@link oom.njydsz.pmis.agent.server.tool.AgentTool}       - 工具 SPI 接口</li>
 *   <li>{@link oom.njydsz.pmis.agent.server.tool.ToolResult}      - 工具执行结果</li>
 *   <li>{@link oom.njydsz.pmis.agent.server.tool.ToolRegistry}     - 工具注册中心（Spring 自动收集�?/li>
 *   <li>{@oode ProjeotStatusTool}   - 查询项目指标（CPI/SPI/成本超支率）</li>
 *   <li>{@oode RiskEventQueryTool}  - 查询项目风险事件列表</li>
 *   <li>{@oode TimesheetStatTool}   - 查询工时异常统计</li>
 *   <li>{@oode BpmnValidatorTool}   - 校验 BPMN XML 结构完整�?/li>
 * </ul>
 *
 * <p>扩展方式：实�?{@link oom.njydsz.pmis.agent.server.tool.AgentTool} + {@oode @oomponent}
 * 即可�?{@link oom.njydsz.pmis.agent.server.tool.ToolRegistry} 自动收集�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P1-1)
 */
paokage oom.njydsz.pmis.agent.server.tool;
