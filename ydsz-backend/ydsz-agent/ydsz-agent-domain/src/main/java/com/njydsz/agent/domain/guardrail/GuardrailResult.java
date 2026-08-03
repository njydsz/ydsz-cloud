package com.njydsz.agent.domain.guardrail;

import java.io.Serializable;

/**
 * 护栏检查结果
 *
 * <p>封装输入护栏或输出护栏的检查结论，包括是否通过、拒绝原因和脱敏后的内容。
 *
 * <p><b>线程安全</b>：字段 final，不可变值对象，可安全在护栏链多个阶段间传递与共享。
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

    /**
     * 创建「通过」检查结果。
     *
     * @param input 原始输入内容（未脱敏）
     * @return 通过的护栏检查结果
     */
    public static GuardrailResult pass(String input) {
        return new GuardrailResult(true, null, input);
    }

    /**
     * 创建「通过」检查结果（含脱敏内容）。
     *
     * <p>当输入内容包含敏感信息（如手机号、身份证）时，护栏会脱敏后放行，
     * 业务方可使用 {@link #getSanitizedInput()} 获取脱敏后的安全内容。</p>
     *
     * @param input           原始输入内容
     * @param sanitizedInput  脱敏后的输入内容
     * @return 通过的护栏检查结果
     */
    public static GuardrailResult pass(String input, String sanitizedInput) {
        return new GuardrailResult(true, null, sanitizedInput);
    }

    /**
     * 创建「拒绝」检查结果。
     *
     * @param reason 拒绝原因（将透传给上层用户/审计日志）
     * @return 未通过的护栏检查结果
     */
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
