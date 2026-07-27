package com.njydsz.agent.domain.guardrail;

import java.io.Serializable;

/**
 * 护栏检查结果
 *
 * <p>封装输入护栏或输出护栏的检查结论，包括是否通过、拒绝原因和脱敏后的内容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class GuardrailResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否通过检查 */
    private final boolean passed;
    /** 拒绝原因（未通过时有值） */
    private final String reason;
    /** 脱敏后的输入内容 */
    private final String sanitizedInput;

    public GuardrailResult(boolean passed, String reason, String sanitizedInput) {
        this.passed = passed;
        this.reason = reason;
        this.sanitizedInput = sanitizedInput;
    }

    public static GuardrailResult pass(String input) {
        return new GuardrailResult(true, null, input);
    }

    public static GuardrailResult pass(String input, String sanitizedInput) {
        return new GuardrailResult(true, null, sanitizedInput);
    }

    public static GuardrailResult reject(String reason) {
        return new GuardrailResult(false, reason, null);
    }

    public boolean isPassed() { return passed; }
    public String getReason() { return reason; }
    public String getSanitizedInput() { return sanitizedInput; }

    public boolean isRejected() {
        return !passed;
    }

    @Override
    public String toString() {
        return passed ? "GuardrailResult{PASSED}" : "GuardrailResult{REJECTED: " + reason + "}";
    }
}
