package com.njydsz.agent.domain.agent;

import java.util.function.Consumer;

import com.njydsz.agent.domain.model.ChatChunk;
import com.njydsz.agent.domain.model.ChatResponse;

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
 * @since 1.0.0
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
