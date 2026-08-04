package com.remisoft.agent.server.chat;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.remisoft.agent.domain.guardrail.GuardrailResult;
import com.remisoft.agent.domain.guardrail.InputGuardrail;
import com.remisoft.agent.domain.guardrail.OutputGuardrail;
import com.remisoft.agent.server.metrics.AgentMetrics;

/**
 * Guardrail 统一服务
 *
 * <p>消除 ChatService、ReActAgentExecutor、SimpleAgentExecutor、RagAgentExecutor 中
 * 重复的 applyInputGuardrails / applyOutputGuardrails 逻辑。
 *
 * <h3>执行流程</h3>
 * <ol>
 *   <li>按优先级排序护栏链</li>
 *   <li>依次执行每个护栏的 check 方法</li>
 *   <li>若被拒绝，记录指标并返回 null（输入）或替换文本（输出）</li>
 *   <li>若通过，使用护栏返回的脱敏内容继续下一个护栏</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailService.class);

    private final List<InputGuardrail> inputGuardrails;
    private final List<OutputGuardrail> outputGuardrails;
    private final AgentMetrics metrics;

    public GuardrailService(List<InputGuardrail> inputGuardrails,
                            List<OutputGuardrail> outputGuardrails,
                            AgentMetrics metrics) {
        this.inputGuardrails = inputGuardrails != null
                ? inputGuardrails.stream().sorted(Comparator.comparingInt(InputGuardrail::getPriority)).toList()
                : List.of();
        this.outputGuardrails = outputGuardrails != null
                ? outputGuardrails.stream().sorted(Comparator.comparingInt(OutputGuardrail::getPriority)).toList()
                : List.of();
        this.metrics = metrics;
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
                return "抱歉，我无法回答这个问题。";
            }
            if (result.getSanitizedInput() != null) {
                sanitized = result.getSanitizedInput();
            }
        }
        return sanitized;
    }
}
