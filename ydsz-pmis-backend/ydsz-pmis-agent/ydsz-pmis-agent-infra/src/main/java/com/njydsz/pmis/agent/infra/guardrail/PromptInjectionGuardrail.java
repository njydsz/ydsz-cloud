package com.njydsz.pmis.agent.infra.guardrail;

import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.pmis.agent.domain.guardrail.GuardrailResult;
import com.njydsz.pmis.agent.domain.guardrail.InputGuardrail;

/**
 * Prompt 注入检测护栏
 *
 * <p>检测常见 Prompt 注入攻击模式：
 * <ul>
 *   <li>"ignore previous instructions"</li>
 *   <li>"system:" / "assistant:" 伪装</li>
 *   <li>角色覆盖指令</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public class PromptInjectionGuardrail implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    private static final Set<Pattern> INJECTION_PATTERNS = Set.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+(instructions|prompts)"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?prior"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+a"),
            Pattern.compile("(?i)forget\\s+(everything|all)"),
            Pattern.compile("(?i)\\[system\\]|\\[assistant\\]"),
            Pattern.compile("(?i)reveal\\s+(your|the)\\s+(system\\s+)?prompt")
    );

    @Override
    public GuardrailResult check(String input) {
        if (input == null || input.isBlank()) {
            return GuardrailResult.pass(input);
        }
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("[Guardrail] 检测到 Prompt 注入: pattern={}", pattern.pattern());
                return GuardrailResult.reject("检测到潜在的 Prompt 注入攻击");
            }
        }
        return GuardrailResult.pass(input);
    }

    @Override
    public String getName() {
        return "prompt-injection-detector";
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
