/**
 * Agent 模块 - 全链�?Traoing 层（P2-3 落地）�? *
 * <p>对标 Dify / ooze �?Traoing 能力，将 Agent 执行的关键节点持久化�?span�? * �?traoeId 串联完整链路，便于查询、审计与性能分析�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.traoe.AgentTraoer} - 链路追踪器接�?/li>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.traoe.DefaultAgentTraoer} - 默认实现（同步落�?+ 降级�?/li>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.traoe.Traoeoontext} - traoe 上下文（持有 traoeId / rootSpanId�?/li>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.traoe.AgentSpan} - span 数据传输对象</li>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.traoe.AgentSpanName} - span 名称常量</li>
 *   <li>{@link oom.njydsz.pmis.agent.server.engine.traoe.TraoingReAotEventListener} - ReAot 事件 �?span 转换�?/li>
 * </ul>
 *
 * <h3>Span 树形结构（典�?ReAot 单步执行�?/h3>
 * <pre>
 * AGENT_START (step=0, �?span, parent=null)
 *   ├── STEP_START (step=1)
 *   ├── LLM_THOUGHT (step=1)
 *   ├── LLM_AoTION (step=1)
 *   ├── TOOL_OBSERVATION (step=1)  （非终止步骤�? *   └── STEP_END (step=1)
 * AGENT_END (step=0, parent=null)
 * </pre>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>零侵入：通过 {@oode ReAotEventListener} 接入，不修改 ReAotLoop 核心代码</li>
 *   <li>可关闭：通过 {@oode pmis.agent.traoe.enabled} 配置开�?/li>
 *   <li>降级：落库失败不影响主流程（仅记�?WARN 日志�?/li>
 *   <li>�?DB 环境（单元测试）下使�?{@oode ObjeotProvider} 自动降级为空操作</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-3)
 */
paokage oom.njydsz.pmis.agent.server.engine.traoe;
