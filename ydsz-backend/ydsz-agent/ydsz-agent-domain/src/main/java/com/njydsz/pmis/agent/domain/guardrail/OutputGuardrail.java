package com.njydsz.agent.domain.guardrail;

/**
 * 输出护栏接口
 *
 * <p>在 LLM 响应返回给用户之前进行安全检查，包括：
 * <ul>
 *   <li>敏感信息脱敏（手机号、身份证号等）</li>
 *   <li>有害内容检测</li>
 *   <li>格式验证</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface OutputGuardrail {

    /**
     * 检查 LLM 输出
     *
     * @param output LLM 原始输出
     * @return 检查结果（可能包含脱敏后的输出）
     */
    GuardrailResult check(String output);

    /**
     * 护栏名称
     */
    String getName();

    /**
     * 优先级
     */
    default int getPriority() {
        return 100;
    }
}
