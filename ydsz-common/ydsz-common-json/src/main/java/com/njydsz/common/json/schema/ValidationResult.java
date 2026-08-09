package com.njydsz.common.json.schema;

import java.util.*;
import com.njydsz.common.json.annotation.Experimental;


/**
 * 验证结果
 *
 * <p>包含验证是否通过以及错误信息列表。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Experimental("RFC extension - non-core JSON serialization capability, may be extracted to separate module")
public final class ValidationResult {

    /** 验证是否通过 */
    private final boolean valid;

    /** 错误信息列表 */
    private final List<String> errors;

    /**
     * 构造函数
     */
    public ValidationResult(boolean valid) {
        this.valid = valid;
        this.errors = new ArrayList<>();
    }

    /**
     * 构造函数
     */
    public ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    /**
     * 添加错误信息
     */
    public void addError(String error) {
        this.errors.add(error);
    }

    /**
     * 合并其他验证结果
     */
    public void merge(ValidationResult other) {
        if (other != null && !other.isValid()) {
            this.errors.addAll(other.getErrors());
        }
    }

    // Getters

    /**
     * 验证是否通过（动态判断）
     */
    public boolean isValid() {
        return valid && errors.isEmpty();
    }

    /**
     * 获取不可修改的错误信息列表。
     *
     * @return 错误信息列表；无错误时返回空列表而非 {@code null}。
     *         返回列表为 {@link Collections#unmodifiableList} 包装，不可修改
     */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * 判断是否存在错误信息。
     *
     * @return {@code true} 表示至少存在一条错误信息，验证未通过
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public String toString() {
        boolean isV = isValid();
        if (isV) {
            return "ValidationResult{valid=true}";
        } else {
            return "ValidationResult{valid=false, errors=" + errors + "}";
        }
    }
}
