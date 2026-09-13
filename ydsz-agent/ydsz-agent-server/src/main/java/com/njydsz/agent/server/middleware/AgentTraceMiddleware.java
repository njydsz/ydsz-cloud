package com.njydsz.agent.server.middleware;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.trace.TraceRecorder;

/**
 * 链路追踪中间件 — 自动记录 Agent 执行全链路的 traceId 和各步骤耗时。
 *
 * <p>优先级 20（紧接输入护栏之后），在每个钩子中记录对应步骤到 TraceRecorder：
 * <ul>
 *   <li>onAgentStart — 启动链路追踪</li>
 *   <li>onModelCall — 记录 LLM 调用成功/失败步骤</li>
 *   <li>onObservation — 记录工具执行步骤</li>
 *   <li>onAgentEnd — 结束链路追踪</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@Component
@Order(20)
public class AgentTraceMiddleware implements AgentMiddleware {

  /** 中间件优先级 */
  private static final int PRIORITY = 20;

  private final TraceRecorder traceRecorder;

  public AgentTraceMiddleware(TraceRecorder traceRecorder) {
    this.traceRecorder = traceRecorder;
  }

  @Override
  public void onAgentStart(MiddlewareContext context) {
    String traceId = traceRecorder.startTrace(context.getConversationId(), "AGENT");
    context.setTraceId(traceId);
    log.debug("[Trace] 启动链路追踪: traceId={}, conversationId={}",
        traceId, context.getConversationId());
  }

  @Override
  public void onModelCall(MiddlewareContext context, ModelCallProceed proceed) {
    long startTime = System.currentTimeMillis();
    ChatResponse response = proceed.execute();
    long duration = System.currentTimeMillis() - startTime;
    context.setLlmResponse(response);

    String traceId = context.getTraceId();
    if (traceId != null && response != null) {
      BigDecimal cost = response.getCostEstimate() != null
          ? response.getCostEstimate().getTotalCost() : BigDecimal.ZERO;
      traceRecorder.recordStep(
          traceId,
          "LLM_CALL",
          "LLM 推理调用",
          context.getLlmRequest(),
          response,
          duration,
          cost);
    }
  }

  @Override
  public void onObservation(MiddlewareContext context) {
    String traceId = context.getTraceId();
    if (traceId != null && context.getToolCall() != null) {
      traceRecorder.recordStep(
          traceId,
          "TOOL_CALL",
          "工具执行: " + context.getToolCall().getName(),
          context.getToolCall().getArguments(),
          context.getToolResult(),
          0L);
    }
  }

  @Override
  public void onAgentEnd(MiddlewareContext context) {
    String traceId = context.getTraceId();
    if (traceId == null) {
      return;
    }
    String status = context.getError() != null ? "FAILED" : "SUCCESS";
    if (context.getError() != null) {
      traceRecorder.recordStep(
          traceId,
          "AGENT_ERROR",
          "Agent 执行异常",
          null,
          context.getError().getMessage(),
          0L);
    }
    traceRecorder.endTrace(traceId, status);
    log.debug("[Trace] 结束链路追踪: traceId={}, status={}", traceId, status);
  }

  @Override
  public String getName() {
    return "trace";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
