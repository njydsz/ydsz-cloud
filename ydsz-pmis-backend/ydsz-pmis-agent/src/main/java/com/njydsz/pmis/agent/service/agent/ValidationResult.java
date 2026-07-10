package com.njydsz.pmis.agent.service.agent;

import java.util.List;

/**
 * DAG 定义验证结果（P1-7 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0 (P1-7)
 */
public record ValidationResult(boolean valid, List<String> errors) {

    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return valid ? "VALID" : "INVALID: " + String.join("; ", errors);
    }
}
