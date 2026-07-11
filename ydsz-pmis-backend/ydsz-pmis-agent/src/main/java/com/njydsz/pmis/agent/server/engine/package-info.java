/**
 * Agent 模块 - 全链路 Tracing 层（P2-3 落地）。
 *
 * <p>对标 Dify / Coze 的 Tracing 能力，将 Agent 执行的关键节点持久化为 span，
 * 按 traceId 串联完整链路，便于查询、审计与性能分析。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.agent.server.engine.trace.AgentTracer} - 链路追踪器接口</li>
 *   <li>{@link com.njydsz.pmis.agent.server.engine.trace.DefaultAgentTracer} - 默认实现（同步落库 + 降级）</li>
 *   <li>{@link com.njydsz.pmis.agent.server.engine.trace.TraceContext} - trace 上下文（持有 traceId / rootSpanId）</li>
 *   <li>{@link com.njydsz.pmis.agent.server.engine.trace.AgentSpan} - span 数据传输对象</li>
 *   <li>{@link com.njydsz.pmis.agent.server.engine.trace.AgentSpanName} - span 名称常量</li>
 *   <li>{@link com.njydsz.pmis.agent.server.engine.trace.TracingReActEventListener} - ReAct 事件 → span 转换器</li>
 * </ul>
 *
 * <h3>Span 树形结构（典型 ReAct 单步执行）</h3>
 * <pre>
 * AGENT_START (step=0, 根 span, parent=null)
 *   ├── STEP_START (step=1)
 *   ├── LLM_THOUGHT (step=1)
 *   ├── LLM_ACTION (step=1)
 *   ├── TOOL_OBSERVATION (step=1)  （非终止步骤）
 *   └── STEP_END (step=1)
 * AGENT_END (step=0, parent=null)
 * </pre>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>零侵入：通过 {@code ReActEventListener} 接入，不修改 ReActLoop 核心代码</li>
 *   <li>可关闭：通过 {@code pmis.agent.trace.enabled} 配置开关</li>
 *   <li>降级：落库失败不影响主流程（仅记录 WARN 日志）</li>
 *   <li>无 DB 环境（单元测试）下使用 {@code ObjectProvider} 自动降级为空操作</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-3)
 */
package com.njydsz.pmis.agent.server.engine.trace;
