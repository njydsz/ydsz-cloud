package com.njydsz.agent.infra.guardrail;

import java.util.Set;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.guardrail.GuardrailResult;
import com.njydsz.agent.domain.guardrail.InputGuardrail;

/**
 * Prompt 注入检测护栏
 *
 * <p>检测常见 Prompt 注入攻击模式：
 *
 * <ul>
 *   <li>"ignore previous instructions"
 *   <li>"system:" / "assistant:" 伪装
 *   <li>角色覆盖指令
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class PromptInjectionGuardrail implements InputGuardrail {

  /** Prompt 注入检测模式集合 */
  private static final Set<Pattern> INJECTION_PATTERNS =
      Set.of(
          // English patterns
          Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+(instructions|prompts)"),
          Pattern.compile("(?i)disregard\\s+(all\\s+)?prior"),
          Pattern.compile("(?i)you\\s+are\\s+now\\s+a"),
          Pattern.compile("(?i)forget\\s+(everything|all)"),
          Pattern.compile("(?i)\\[system\\]|\\[assistant\\]"),
          Pattern.compile("(?i)reveal\\s+(your|the)\\s+(system\\s+)?prompt"),
          // Chinese patterns
          Pattern.compile("忽略.*?(之前|上面|前面|先前).*?(指令|提示|指示)"),
          Pattern.compile("忘记.*?(之前|上面|前面|先前).*?(指令|提示)"),
          Pattern.compile("(你现在是|从现在起你是|你的新角色)"),
          Pattern.compile("(忽略|无视).*?(所有|全部)?(规则|限制|约束)"),
          Pattern.compile("(显示|展示|输出).*(系统提示词|系统指令|prompt)"),
          Pattern.compile("(不要遵守|不要执行).*(指令|规则|指示)"),
          Pattern.compile("\\[系统\\]|\\[助手\\]|\\[用户\\]"),
          Pattern.compile("(假装|扮演).*?(管理员|开发者|root|超级用户)"),
          Pattern.compile("(切换到|进入).*(调试|开发|管理员).*?模式"));

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
