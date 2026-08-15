package com.njydsz.common.event.api;

/**
 * 默认不执行任何校验的 JSON Schema 校验器
 *
 * <p>当使用方未引入具体的 JSON Schema 库实现时，使用此空实现作为 fallback。
 * {@link EventAutoConfiguration} 中 {@code @ConditionalOnMissingBean} 确保
 * 使用方的自定义实现优先。
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see JsonSchemaValidator
 */
public class NoopJsonSchemaValidator implements JsonSchemaValidator {

    /**
     * 永远返回校验通过（不做任何实际校验）
     *
     * @param eventType  事件类型
     * @param payload    事件 payload
     * @param schemaJson JSON Schema
     * @return 永远为 valid 的结果
     */
    @Override
    public SchemaValidationResult validate(String eventType, String payload, String schemaJson) {
        return SchemaValidationResult.valid();
    }
}
