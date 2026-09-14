package com.njydsz.agent.domain.middleware;

import java.util.Map;

import com.njydsz.agent.domain.model.ChatResponse;

/**
 * 中间件链接口 — 串接多个 {@link AgentMiddleware} 并按优先级调度钩子执行。
 *
 * <p>中间件链在 Agent 执行器初始化时构建一次（不可变），每次 Agent 执行时复用于调度各钩子。
 * 优先级越低（数字小）的中间件越先执行前置钩子、越后执行后置钩子（类似 Servlet Filter 的洋葱模型）。
 *
 * <p><b>对标 AgentScope</b>：对应其 {@code MiddlewarePipeline}，按注册顺序依次调用钩子位置。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
public interface MiddlewareChain {

  /**
   * 执行 Agent 开始阶段所有中间件的 onAgentStart 钩子。
   *
   * @param context 中间件上下文
   */
  void executeAgentStart(MiddlewareContext context);

  /**
   * 执行系统 Prompt 构建阶段所有中间件的 onSystemPrompt 钩子。
   *
   * @param context 中间件上下文
   */
  void executeSystemPrompt(MiddlewareContext context);

  /**
   * 执行推理前阶段所有中间件的 onReasoning 钩子。
   *
   * @param context 中间件上下文
   */
  void executeReasoning(MiddlewareContext context);

  /**
   * 执行 LLM 调用阶段所有中间件的 onModelCall 钩子（洋葱模型，支持缓存/限流拦截）。
   *
   * @param context 中间件上下文
   * @param finalCall 最终 LLM 调用函数（最后一个中间件放行时触发）
   * @return LLM 响应（可能由某个中间件缓存提供，也可能来自实际调用）
   */
  ChatResponse executeModelCall(
      MiddlewareContext context, AgentMiddleware.ModelCallProceed finalCall);

  /**
   * 执行工具调用阶段所有中间件的 onActing 钩子（洋葱模型，支持审计/拦截/结果后处理）。
   *
   * @param context 中间件上下文（toolCalls 已就绪）
   * @param finalCall 最终工具执行函数（最后一个中间件放行时触发）
   * @return callId → 工具执行结果文本
   */
  Map<String, String> executeActing(
      MiddlewareContext context, AgentMiddleware.ActingProceed finalCall);

  /**
   * 执行工具观察阶段所有中间件的 onObservation 钩子。
   *
   * @param context 中间件上下文
   */
  void executeObservation(MiddlewareContext context);

  /**
   * 执行 Agent 结束阶段所有中间件的 onAgentEnd 钩子。
   *
   * @param context 中间件上下文
   */
  void executeAgentEnd(MiddlewareContext context);
}
