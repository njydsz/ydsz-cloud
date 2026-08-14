package com.njydsz.common.json.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON Schema 校验结果。
 *
 * <p>不可变对象，通过 {@link #isSuccess()} 快速判断校验是否通过，
 * 通过 {@link #getErrors()} 获取错误详情列表。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * ValidationResult result = JsonSchemaValidator.validate(data, schema);
 * if (!result.isSuccess()) {
 *     log.error("JSON Schema 校验失败：{}", result.getErrors());
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.2.1
 */
public final class ValidationResult {

    /** 校验通过单例结果 */
    private static final ValidationResult SUCCESS = new ValidationResult(true, Collections.emptyList());

    private final boolean success;
    private final List<String> errors;

    private ValidationResult(boolean success, List<String> errors) {
        this.success = success;
        this.errors = errors;
    }

    /**
     * 返回校验通过结果。
     *
     * @return 通过的校验结果（无错误）
     */
    public static ValidationResult success() {
        return SUCCESS;
    }

    /**
     * 返回校验失败结果。
     *
     * @param errors 错误列表，不可为 null
     * @return 失败的校验结果
     */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, new ArrayList<>(errors));
    }

    /**
     * 判断校验是否通过。
     *
     * @return true 表示校验通过，false 表示存在错误
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 获取错误详情列表。
     *
     * @return 错误消息列表（不可修改），永不为 null
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    @Override
    public String toString() {
        if (success) {
            return "ValidationResult{success=true}";
        }
        return "ValidationResult{success=false, errors=" + errors + "}";
    }
}
