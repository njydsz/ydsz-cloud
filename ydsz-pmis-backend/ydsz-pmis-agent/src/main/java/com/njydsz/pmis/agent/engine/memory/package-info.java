/**
 * 对话记忆与上下文窗口管理（P1-3 落地）
 *
 * <p>对标 LangChain ConversationBufferMemory / OpenAI Chat Completion 的多轮对话能力，
 * 为 ReAct 推理循环提供对话历史持久化、token 计数、上下文窗口截断能力。
 *
 * <p>核心组件：
 * <ul>
 *   <li>{@link com.njydsz.pmis.agent.engine.memory.ChatMessage}    - 单条对话消息</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.memory.ChatMemory}     - 对话记忆管理器（按 sessionId 隔离）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.memory.TokenCounter}   - Token 计数工具</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.memory.ContextWindow}  - 上下文窗口截断策略</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>
 * // 添加多轮对话
 * chatMemory.addMessage("session-001", ChatMessage.user("你好"));
 * chatMemory.addMessage("session-001", ChatMessage.assistant("你好，我是助手"));
 *
 * // 获取历史（已自动截断到窗口大小）
 * List&lt;ChatMessage&gt; history = chatMemory.getHistory("session-001");
 *
 * // 获取当前 token 总数
 * int tokens = chatMemory.getTokenCount("session-001");
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
package com.njydsz.pmis.agent.engine.memory;
