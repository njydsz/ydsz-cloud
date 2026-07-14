package com.njydsz.pmis.common.json.schema;

import java.util.*;

/**
 * 验证结果
 * 
 * <p>包含验证是否通过以及错误信息列表。</p>
 * 
 * @since 1.3.0
 */
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
        return errors.isEmpty();
    }
    
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{valid=true}";
        } else {
            return "ValidationResult{valid=false, errors=" + errors + "}";
        }
    }
}
