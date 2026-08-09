package com.njydsz.common.json.schema;

import java.util.Collections;
import java.util.List;

/**
 * JSON Schema 校验结果。
 *
 * @deprecated JSON Schema 校验引擎已移除（v1.1.0）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated since 1.1.0
 */
@Deprecated
public final class ValidationResult {

    /** 始终通过的校验结果（占位） */
    public static final ValidationResult VALID = new ValidationResult(true, Collections.emptyList());

    private final boolean valid;
    private final List<String> errors;

    private ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = errors;
    }

    /** @deprecated 已废弃，始终返回 true */
    @Deprecated
    public boolean isValid() {
        return valid;
    }

    /** @deprecated 已废弃，始终返回空列表 */
    @Deprecated
    public List<String> getErrors() {
        return errors;
    }
}
