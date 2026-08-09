package com.njydsz.common.json.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON Schema 校验结果封装。
 *
 * <p>Immutable 对象，记录校验是否通过以及所有校验错误信息。
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see JsonSchemaValidator
 */
public final class ValidationResult {

    /** 校验通过的空结果单例 */
    public static final ValidationResult VALID = new ValidationResult(true, Collections.emptyList());

    private final boolean valid;
    private final List<String> errors;

    /**
     * 构造函数。
     *
     * @param valid  校验是否通过
     * @param errors 错误列表（不可为 null）
     */
    public ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
    }

    /**
     * 校验是否通过。
     *
     * @return true 表示校验通过，false 表示存在错误
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * 是否存在校验错误。
     *
     * @return true 表示存在至少一条错误
     */
    public boolean hasErrors() {
        return !valid || !errors.isEmpty();
    }

    /**
     * 获取错误列表（不可修改）。
     *
     * @return 错误消息列表
     */
    public List<String> getErrors() {
        return errors;
    }

    /**
     * 添加错误并返回新的 ValidationResult（原对象不变）。
     *
     * @param error 错误消息
     * @return 包含新错误的 ValidationResult
     */
    public ValidationResult addError(String error) {
        List<String> newErrors = new ArrayList<>(this.errors.size() + 1);
        newErrors.addAll(this.errors);
        newErrors.add(error);
        return new ValidationResult(false, newErrors);
    }

    /**
     * 合并另一个 ValidationResult（取逻辑与：两者都 valid 才 valid）。
     *
     * @param other 另一个校验结果
     * @return 合并后的结果
     */
    public ValidationResult merge(ValidationResult other) {
        if (other == null) {
            return this;
        }
        List<String> mergedErrors = new ArrayList<>(this.errors.size() + other.errors.size());
        mergedErrors.addAll(this.errors);
        mergedErrors.addAll(other.errors);
        return new ValidationResult(this.valid && other.valid && mergedErrors.isEmpty(), mergedErrors);
    }

    @Override
    public String toString() {
        if (!hasErrors()) {
            return "ValidationResult{valid=true}";
        }
        return "ValidationResult{valid=false, errors=" + errors + "}";
    }
}
