package com.njydsz.agent.server.chat;

import java.util.Comparator;
import java.util.List;

import com.njydsz.agent.server.metrics.AgentMetrics;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.agent.domain.guardrail.InputGuardrail;
import com.njydsz.agent.domain.guardrail.OutputGuardrail;
import com.njydsz.agent.server.metrics.AgentMetrics;

/**
 * Guardrail 统一服务
 *
 * <p>消除 ChatService、ReActAgentExecutor、SimpleAgentExecutor、RagAgentExecutor 中 重复的
 * applyInputGuardrails / applyOutputGuardrails 逻辑。
 *
 * <h3>执行流程</h3>
 *
 * <ol>
 *   <li>按优先级排序护栏链
 *   <li>依次执行每个护栏的 check 方法
 *   <li>若被拒绝，记录指标并返回 null（输入）或替换文本（输出）
 *   <li>若通过，使用护栏返回的脱敏内容继续下一个护栏
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class GuardrailService {

  /** 默认输出护栏拒绝文案 */
  private static final String DEFAULT_REJECTION_MESSAGE = "抱歉，我无法回答这个问题。";

  private final List<InputGuardrail> inputGuardrails;
  private final List<OutputGuardrail> outputGuardrails;
  private final AgentMetrics metrics;

  /** 输出护栏拒绝时的兜底文案（P2 修复：原文案硬编码，现支持配置化） */
  private final String rejectionMessage;

  public GuardrailService(
      List<InputGuardrail> inputGuardrails,
      List<OutputGuardrail> outputGuardrails,
      AgentMetrics metrics) {
    this(inputGuardrails, outputGuardrails, metrics, DEFAULT_REJECTION_MESSAGE);
  }

  /**
   * 构造护栏编排服务。
   *
   * @param inputGuardrails 输入护栏集合（按优先级排序）
   * @param outputGuardrails 输出护栏集合（按优先级排序）
   * @param metrics 指标组件
   * @param rejectionMessage 输出护栏拒绝文案（可通过配置 {@code ydsz.agent.guardrail.rejection-message} 覆盖）
   */
  public GuardrailService(
      List<InputGuardrail> inputGuardrails,
      List<OutputGuardrail> outputGuardrails,
      AgentMetrics metrics,
      String rejectionMessage) {
    this.inputGuardrails =
        inputGuardrails != null
            ? inputGuardrails.stream()
                .sorted(Comparator.comparingInt(InputGuardrail::getPriority))
                .toList()
            : List.of();
    this.outputGuardrails =
        outputGuardrails != null
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

  /**
   * 应用输入护栏
   *
   * @param input 原始输入
   * @return 脱敏后的输入；null 表示被拒绝
   */
  public String applyInputGuardrails(String input) {
    String sanitized = input;
    for (InputGuardrail guard : inputGuardrails) {
      GuardrailResult result = guard.check(sanitized);
      if (result.isRejected()) {
        log.warn("[Guardrail] 输入护栏拒绝: guard={}, reason={}", guard.getName(), result.getReason());
        metrics.recordGuardrailRejection(guard.getName(), "input");
        return null;
      }
      if (result.getSanitizedInput() != null) {
        sanitized = result.getSanitizedInput();
      }
    }
    return sanitized;
  }

  /**
   * 应用输出护栏
   *
   * @param output 原始输出
   * @return 脱敏后的输出
   */
  public String applyOutputGuardrails(String output) {
    String sanitized = output;
    for (OutputGuardrail guard : outputGuardrails) {
      GuardrailResult result = guard.check(sanitized);
      if (result.isRejected()) {
        log.warn("[Guardrail] 输出护栏拒绝: guard={}, reason={}", guard.getName(), result.getReason());
        metrics.recordGuardrailRejection(guard.getName(), "output");
        // P2 修复：拒绝文案可配置（原为硬编码固定文案）
        return rejectionMessage;
      }
      if (result.getSanitizedInput() != null) {
        sanitized = result.getSanitizedInput();
      }
    }
    return sanitized;
  }
}
