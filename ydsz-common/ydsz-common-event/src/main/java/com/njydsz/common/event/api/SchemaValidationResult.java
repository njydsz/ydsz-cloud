package com.njydsz.common.event.api;

import java.util.Collections;
import java.util.List;

/**
 * Schema 校验结果
 *
 * <p>不可变对象，表示一次 JSON Schema 校验的结果。
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see JsonSchemaValidator
 */
public final class SchemaValidationResult {

    /** 校验通过的单例实例 */
    private static final SchemaValidationResult VALID = new SchemaValidationResult(true, Collections.emptyList());

    /** 是否校验通过 */
    private final boolean valid;

    /** 校验错误信息列表（valid=true 时为空列表） */
    private final List<String> errors;

    /**
     * 私有构造函数，使用工厂方法创建实例
     *
     * @param valid  是否校验通过
     * @param errors 错误信息列表
     */
    private SchemaValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors != null ? List.copyOf(errors) : Collections.emptyList();
    }

    /**
     * 返回校验通过的结果
     *
     * @return 通过的校验结果
     */
    public static SchemaValidationResult valid() {
        return VALID;
    }

    /**
     * 返回校验失败的结果
     *
     * @param errors 错误信息列表（不可为 null）     * @return 失败的校验结果
     */
    public static SchemaValidationResult invalid(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("errors must not be null or empty for invalid result");
        }
        return new SchemaValidationResult(false, errors);
    }

    /**
     * 是否校验通过
     *
     * @return true 表示通过
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * 获取校验错误信息列表
     *
     * @return 错误列表（不可修改，valid=true 时为空列表）
     */
    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return valid ? "SchemaValidationResult{valid=true}"
                : "SchemaValidationResult{valid=false, errors=" + errors + "}";
    }
}
