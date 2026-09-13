package com.njydsz.agent.server.middleware;

import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.agent.domain.guardrail.GuardrailResult;
import com.njydsz.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.middleware.MiddlewareException;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.agent.server.metrics.AgentMetrics;

/**
 * 输出护栏中间件 — 将现有 {@link OutputGuardrail} 链适配到中间件体系。
 *
 * <p>优先级 100（最后执行），在 onAgentEnd 钩子中对 LLM 最终输出执行安全审查和脱敏。
 * 护栏拒绝时替换为预设兜底文案而非中断执行。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@Component
@Order(100)
public class OutputGuardrailMiddleware implements AgentMiddleware {

  /** 中间件优先级（最后执行） */
  private static final int PRIORITY = 100;

  /** 默认输出护栏拒绝文案 */
  private static final String DEFAULT_REJECTION_MESSAGE = "抱歉，我无法回答这个问题。";

  private final List<OutputGuardrail> outputGuardrails;
  private final AgentMetrics metrics;
  private final String rejectionMessage;

  public OutputGuardrailMiddleware(
      List<OutputGuardrail> outputGuardrails, AgentMetrics metrics) {
    this(outputGuardrails, metrics, DEFAULT_REJECTION_MESSAGE);
  }

  public OutputGuardrailMiddleware(
      List<OutputGuardrail> outputGuardrails,
      AgentMetrics metrics,
      String rejectionMessage) {
    this.outputGuardrails = outputGuardrails != null
        ? outputGuardrails.stream()
            .sorted(Comparator.comparingInt(OutputGuardrail::getPriority))
            .toList()
        : List.of();
    this.metrics = metrics;
    this.rejectionMessage =
        rejectionMessage != null && !rejectionMessage.isBlank()
            ? rejectionMessage
            : DEFAULT_REJECTION_MESSAGE;
  }

  @Override
  public void onAgentEnd(MiddlewareContext context) {
    ChatResponse response = context.getLlmResponse();
    if (response == null || response.getContent() == null) {
      return;
    }
    String content = response.getContent();
    String sanitized = content;
    for (OutputGuardrail guard : outputGuardrails) {
      GuardrailResult result = guard.check(sanitized);
      if (result.isRejected()) {
        log.warn("[Guardrail] 输出护栏拒绝: guard={}, reason={}", guard.getName(), result.getReason());
        metrics.recordGuardrailRejection(guard.getName(), "output");
        // 输出护栏拒绝时替换为兜底文案（不中断执行）
        context.setLlmResponse(buildRejectionResponse(response));
        return;
      }
      if (result.getSanitizedInput() != null) {
        sanitized = result.getSanitizedInput();
      }
    }
    // 如果发生了脱敏，更新响应内容
    if (!sanitized.equals(content)) {
      context.setLlmResponse(response.withContent(sanitized));
    }
  }

  /**
   * 构造护栏拒绝响应。
   *
   * @param original 原始响应
   * @return 替换了内容的响应对象
   */
  private ChatResponse buildRejectionResponse(ChatResponse original) {
    return original.withContent(rejectionMessage);
  }

  @Override
  public String getName() {
    return "guardrail-output";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
