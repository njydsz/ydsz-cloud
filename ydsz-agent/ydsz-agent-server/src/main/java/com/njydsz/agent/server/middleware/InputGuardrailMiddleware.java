package com.njydsz.agent.server.middleware;

import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.agent.domain.guardrail.GuardrailResult;
import com.njydsz.agent.domain.guardrail.InputGuardrail;
import com.njydsz.agent.domain.middleware.AgentMiddleware;
import com.njydsz.agent.domain.middleware.MiddlewareContext;
import com.njydsz.agent.domain.middleware.MiddlewareException;
import com.njydsz.agent.server.metrics.AgentMetrics;

/**
 * 输入护栏中间件 — 将现有 {@link InputGuardrail} 链适配到中间件体系。
 *
 * <p>优先级 10（最先执行），在 onReasoning 钩子中对 LLM 请求中的用户消息执行安全检查。
 * 任一护栏拒绝时抛出 {@link MiddlewareException} 中断执行管线。
 *
 * <p><b>设计意图</b>：将安全校验从 {@code AbstractAgentExecutor.applyInputGuardrails} 下沉到中间件链，
 * 保持执行器的业务无关性，安全规则变更只需调整中间件配置。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Slf4j
@Component
@Order(10)
public class InputGuardrailMiddleware implements AgentMiddleware {

  /** 中间件优先级（最先执行） */
  private static final int PRIORITY = 10;

  private final List<InputGuardrail> inputGuardrails;
  private final AgentMetrics metrics;

  public InputGuardrailMiddleware(
      List<InputGuardrail> inputGuardrails, AgentMetrics metrics) {
    this.inputGuardrails = inputGuardrails != null
        ? inputGuardrails.stream()
            .sorted(Comparator.comparingInt(InputGuardrail::getPriority))
            .toList()
        : List.of();
    this.metrics = metrics;
  }

  @Override
  public void onReasoning(MiddlewareContext context) {
    String userInput = context.getExecutionRequest().getUserInput();
    if (userInput == null || userInput.isBlank()) {
      return;
    }
    String sanitized = userInput;
    for (InputGuardrail guard : inputGuardrails) {
      GuardrailResult result = guard.check(sanitized);
      if (result.isRejected()) {
        log.warn("[Guardrail] 输入护栏拒绝: guard={}, reason={}", guard.getName(), result.getReason());
        metrics.recordGuardrailRejection(guard.getName(), "input");
        throw new MiddlewareException(
            "输入内容未通过安全校验，请修改后重试",
            AgentExceptionCode.GUARDRAIL_REJECTED,
            "InputGuardrail rejected: " + guard.getName() + ", reason=" + result.getReason()
        );
      }
      if (result.getSanitizedInput() != null) {
        sanitized = result.getSanitizedInput();
      }
    }
    // 将脱敏后的内容反映到请求（如果发生了变化）
    if (!sanitized.equals(userInput)) {
      context.getExecutionRequest().setUserInput(sanitized);
    }
  }

  @Override
  public String getName() {
    return "guardrail-input";
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }
}
