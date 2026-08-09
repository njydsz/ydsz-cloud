package com.njydsz.common.json.schema;

/**
 * JSON Schema 校验器。
 *
 * @deprecated JSON Schema 校验引擎已移除（v1.1.0）。
 * 内部服务无需嵌入式 Draft 07 Schema 校验能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated since 1.1.0
 */
@Deprecated
public final class JsonSchemaValidator {

    private JsonSchemaValidator() {
    }

    /**
     * @deprecated 已废弃，始终返回通过结果
     */
    @Deprecated
    public static ValidationResult validate(JsonSchema schema, Object data) {
        return ValidationResult.VALID;
    }
}
