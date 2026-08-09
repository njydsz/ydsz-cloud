package com.njydsz.common.json.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.njydsz.common.json.annotation.Experimental;

/**
 * JSON Schema 校验结果。
 *
 * <p>封装 {@link JsonSchemaValidator#validate} 的校验结果，提供
 * {@link #isValid()} / {@link #hasErrors()} / {@link #getErrors()} 三个查询方法。
 *
 * <p><b>不可变语义：</b>通过 {@link #addError} 和 {@link #merge} 在内部累积错误，
 * 外部读取的 {@link #getErrors()} 返回不可修改的快照。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonSchemaValidator JSON Schema 校验器
 * @see JsonSchema JSON Schema 定义
 */
@Experimental
public final class ValidationResult {

    private final boolean valid;
    private final List<String> errors;

    public ValidationResult(boolean valid) {
        this(valid, new ArrayList<>());
    }

    public ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = new ArrayList<>(errors);
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    public void merge(ValidationResult other) {
        if (other != null && other.hasErrors()) {
            this.errors.addAll(other.errors);
        }
    }

    public boolean isValid() {
        return valid && errors.isEmpty();
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        if (isValid()) {
            return "ValidationResult{valid=true}";
        }
        return "ValidationResult{valid=false, errors=" + errors + "}";
    }
}
