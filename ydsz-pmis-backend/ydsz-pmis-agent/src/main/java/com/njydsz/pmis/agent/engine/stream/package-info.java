/**
 * Agent 流式输出模块（P2-1 落地）
 *
 * <p>对标 Coze / Dify 的 Chat Stream，让前端能够实时展示 ReAct 推理循环的全过程：
 * 「思考中 → 调用工具 → 观察 → 最终回答」。
 *
 * <p>核心组件：
 * <ul>
 *   <li>{@link com.njydsz.pmis.agent.engine.stream.StreamEvent}          - 流式事件 DTO（type + payload）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.stream.ReActEventListener}   - ReAct 事件监听器接口</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.stream.NoOpReActEventListener} - 空实现（默认参数）</li>
 *   <li>{@link com.njydsz.pmis.agent.engine.stream.SseEventListener}    - 把事件推送到 Spring MVC SseEmitter</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   // 1. Controller 端创建 SseEmitter
 *   SseEmitter emitter = new SseEmitter(60_000L);
 *   SseEventListener listener = new SseEventListener(emitter);
 *
 *   // 2. 异步线程执行 ReAct 循环
 *   reactLoop.runStream(systemPrompt, userPrompt, ctx, 5, listener);
 *
 *   // 3. 返回 emitter 给客户端
 *   return emitter;
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-1)
 */
package com.njydsz.pmis.agent.engine.stream;
