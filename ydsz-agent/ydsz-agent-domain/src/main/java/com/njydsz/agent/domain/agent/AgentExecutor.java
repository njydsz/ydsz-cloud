package com.njydsz.agent.domain.agent;

import java.util.function.Consumer;

import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.SseEvent;

/**
 * Agent 执行器接口
 *
 * <p>定义 Agent 的核心执行能力。不同实现支持不同 Agent 模式：
 *
 * <ul>
 *   <li>{@code SimpleAgentExecutor} — 单轮 LLM 调用（对话模式）
 *   <li>{@code ReActAgentExecutor} — ReAct 模式（Thought→Action→Observation 循环，可选 RAG 增强）
 *   <li>{@code PlanExecuteAgentExecutor} — Plan-and-Execute 模式（工作流）
 * </ul>
 *
 * <p>P2-1 重构：RouterAgentExecutor 已删除，意图路由不再作为独立执行器存在。 多节点编排统一由 {@code DagOrchestrationExecutor}（Node + Edge + State 图引擎）承担。
 *
 * <p><b>线程安全</b>：执行器通常被多个请求并发调用，实现必须是无状态的（依赖通过参数传入）， 不得在实例字段中保存请求级状态，否则会引发并发错乱。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface AgentExecutor {

  /**
   * 同步执行 Agent
   *
   * @param request 执行请求
   * @return 执行结果
   */
  ChatResponse execute(AgentExecutionRequest request);

  /**
   * 流式执行 Agent
   *
   * @param request 执行请求
   * @param chunkConsumer 流式片段消费者
   */
  void executeStream(AgentExecutionRequest request, Consumer<ChatChunk> chunkConsumer);

  /**
   * 流式执行 Agent（带进度回调）。
   *
   * <p>默认实现忽略进度回调，仅委托 {@link #executeStream(AgentExecutionRequest, Consumer)}。
   * 支持细粒度进度的执行器（如 {@code DagOrchestrationExecutor}）应重写本方法，
   * 在节点生命周期事件发生时回调 {@code progressConsumer}。
   *
   * @param request 执行请求
   * @param chunkConsumer 流式片段消费者
   * @param progressConsumer DAG 节点进度事件消费者（可为 null）
   */
  default void executeStream(
      AgentExecutionRequest request,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<DagProgressEvent> progressConsumer) {
    executeStream(request, chunkConsumer);
  }

  /**
   * 流式执行 Agent（带进度回调 + 类型化事件回调）。
   *
   * <p>在流式片段之外额外推送类型化事件（{@link SseEvent}）：工具调用开始/结束、
   * 思考链、引用来源、人工审批请求等。默认实现忽略事件回调，仅委托三参重载；
   * 支持事件化输出的执行器（如 {@code ReActAgentExecutor}）应重写本方法。
   *
   * <p><b>设计说明</b>：中间件与执行器通过本回调把结构化事件推给前端，
   * 与纯文本片段（{@code chunkConsumer}）分离，便于前端按事件类型分区渲染；
   * 事件可携带来源标识（{@link SseEvent#getSource()}）以区分多 Agent 协作下的归属。
   *
   * @param request 执行请求
   * @param chunkConsumer 流式片段消费者
   * @param progressConsumer DAG 节点进度事件消费者（可为 null）
   * @param eventConsumer 类型化事件消费者（可为 null）
   */
  default void executeStream(
      AgentExecutionRequest request,
      Consumer<ChatChunk> chunkConsumer,
      Consumer<DagProgressEvent> progressConsumer,
      Consumer<SseEvent> eventConsumer) {
    executeStream(request, chunkConsumer, progressConsumer);
  }

  /**
   * Agent 类型标识
   *
   * @return 类型标识（如 "simple"、"react"、"plan_execute"）
   */
  String getType();

  /**
   * 是否支持指定 Agent 类型
   *
   * @param type Agent 类型
   * @return true=支持
   */
  boolean supports(String type);
}
