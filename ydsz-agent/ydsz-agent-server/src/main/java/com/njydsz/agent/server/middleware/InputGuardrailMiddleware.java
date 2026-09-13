package com.njydsz.agent.server.middleware;

import java.util.ArrayList;
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
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.MessageRole;
import com.njydsz.agent.server.metrics.AgentMetrics;

/**
 * 输入护栏中间件 — 将现有 {@link InputGuardrail} 链适配到中间件体系。
 *
 * <p>优先级 10（最先执行），在 onReasoning 钩子中对 LLM 请求中的最新用户消息执行安全检查。
 * 任一护栏拒绝时抛出 {@link MiddlewareException} 中断执行管线。
 *
 * <p><b>设计意图</b>：将安全校验从 {@code AbstractAgentExecutor.applyInputGuardrails} 下沉到中间件链，
 * 保持执行器的业务无关性，安全规则变更只需调整中间件配置。
 *
 * <p>脱敏后的内容通过更新 {@link MiddlewareContext#setLlmRequest(ChatRequest)} 写回，
 * 确保后续中间件和最终 LLM 调用使用脱敏后的消息。
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
    ChatRequest llmRequest = context.getLlmRequest();
    if (llmRequest == null) {
      return;
    }
    List<ChatMessage> messages = llmRequest.getMessages();
    // 找到最新的用户消息进行护栏检查
    int lastUserIdx = -1;
    for (int i = messages.size() - 1; i >= 0; i--) {
      if (messages.get(i).getRole() == MessageRole.USER) {
        lastUserIdx = i;
        break;
      }
    }
    if (lastUserIdx < 0) {
      return;
    }
    String userInput = messages.get(lastUserIdx).getContent();
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
    // 如果内容发生脱敏变更，重建消息列表
    if (!sanitized.equals(userInput)) {
      List<ChatMessage> newMessages = new ArrayList<>(messages);
      ChatMessage original = messages.get(lastUserIdx);
      newMessages.set(lastUserIdx, original.appendContent("/* sanitized */"));
      // 使用更精确的替换：直接替换内容（因为 appendContent 是追加，这里需要完整替换）
      newMessages.set(lastUserIdx, new ChatMessage(
          original.getId(),
          original.getRole(),
          sanitized,
          original.getConversationId(),
          original.getCreatedAt(),
          new ArrayList<>(original.getToolCalls()),
          original.getToolCallId(),
          original.getTokenUsage()));
      context.setLlmRequest(llmRequest.withMessages(newMessages));
      log.debug("[Guardrail] 输入内容已脱敏替换");
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
