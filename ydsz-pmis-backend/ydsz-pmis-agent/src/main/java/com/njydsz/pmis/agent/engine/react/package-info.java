/**
 * ReAct 推理循环模块（P1-2 落地）
 *
 * <p>对标 LangGraph / Coze / Dify 的 ReAct 推理引擎，实现 Thought → Action → Observation
 * 循环，让 LLM 能够主动调用工具获取外部信息，再基于观察结果给出最终答案。
 *
 * <p>核心组件：
 * <ul>
 *   <li>{@link com.njydsz.pmis.agent.engine.react.ReActLoop}     - ReAct 循环核心</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.react.ReActDecision} - LLM 单步决策（Thought + Action）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.react.ReActStep}     - 单步执行记录（含 Observation）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.react.ReActResult}   - 循环执行结果</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 * ReActResult result = reactLoop.run(systemPrompt, userPrompt, ctx, 5);
 * if (result.isSuccess()) {
 *     String finalAnswer = result.getFinalAnswer();
 *     // 转换为 AgentResult
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-2)
 */
package com.njydsz.pmis.agent.engine.react;
