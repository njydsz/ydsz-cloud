package com.njydsz.pmis.agent.domain.guardrail;

import java.io.Serializable;

/**
 * 护栏检查结果
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class GuardrailResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean passed;
    private final String reason;
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
