package com.njydsz.agent.server.middleware;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.server.metrics.AgentMetrics;

/**
 * 指标采集中间件 — 自动记录 LLM 调用耗时和 Token 消耗到 Prometheus。
 *
 * <p>优先级 30（护栏和追踪之后），在 onModelCall 中包装调用并记录指标，
 * 在 onAgentEnd 中记录 Token 用量（如未在 onModelCall 中记录）。
 *
 * <p>与 {@link AgentTraceMiddleware} 互补：TraceMiddleware 记录详细步骤日志，
 * MetricsMiddleware 专注聚合指标（适合 Grafana 看板消费）。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@Component
@Order(30)
public class AgentMetricsMiddleware implements AgentMiddleware {

  /** 中间件优先级 */
  private static final int PRIORITY = 30;

  private final AgentMetrics metrics;
  private final String defaultModel;

  public AgentMetricsMiddleware(AgentMetrics metrics, String defaultModel) {
    this.metrics = metrics;
    this.defaultModel = defaultModel;
  }

  @Override
  public void onModelCall(MiddlewareContext context, ModelCallProceed proceed) {
    long startTime = System.currentTimeMillis();
    ChatResponse response = null;
    Throwable error = null;
    try {
      response = proceed.execute();
      context.setLlmResponse(response);
      return;
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      String provider = "default";
      String model = defaultModel;
      if (context.getLlmRequest() != null && context.getLlmRequest().getModel() != null) {
        model = context.getLlmRequest().getModel();
      }
      metrics.recordLlmCall(provider, model, duration, response, error);
    }
  }

  @Override
  public String getName() {
    return "metrics";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
