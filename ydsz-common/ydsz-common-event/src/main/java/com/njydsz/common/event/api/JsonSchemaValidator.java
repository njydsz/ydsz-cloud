package com.njydsz.common.event.api;

import java.util.List;

/**
 * JSON Schema 校验器 SPI 接口
 *
 * <p>提供领域事件 payload 的 JSON Schema 校验能力。业务方可实现此接口并注册到
 * {@link JsonSchemaRegistry}，在 Outbox 写入时自动进行 schema 校验。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>本接口<b>不绑定</b>具体 JSON Schema 库（如 networknt、everit），
 *       由使用方自行选择实现</li>
 *   <li>校验失败时返回 {@link SchemaValidationResult}，不强制抛异常</li>
 *   <li>实现类应该是线程安全的（无状态或不可变状态）</li>
 * </ul>
 *
 * <p><b>使用示例（基于 networknt/json-schema-validator）：</b>
 * <pre>{@code
 * &#64;Component
 * public class NetworkntSchemaValidator implements JsonSchemaValidator {
 *     private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
 *
 *     &#64;Override
 *     public SchemaValidationResult validate(String eventType, String payload, String schemaJson) {
 *         try {
 *             JsonSchema schema = factory.getSchema(schemaJson);
 *             Set<ValidationMessage> errors = schema.validate(payload, InputFormat.JSON);
 *             if (errors.isEmpty()) {
 *                 return SchemaValidationResult.valid();
 *             }
 *             return SchemaValidationResult.invalid(
 *                 errors.stream().map(ValidationMessage::getMessage).toList()
 *             );
 *         } catch (Exception e) {
 *             return SchemaValidationResult.invalid(List.of("Schema parse error: " + e.getMessage()));
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see JsonSchemaRegistry
 * @see SchemaValidationResult
 */
public interface JsonSchemaValidator {

    /**
     * 校验事件 payload 是否符合指定的 JSON Schema
     *
     * @param eventType  事件类型（如 ORDER_CREATED）
     * @param payload    事件 payload JSON 字符串
     * @param schemaJson JSON Schema 字符串
     * @return 校验结果（never null）
     */
    SchemaValidationResult validate(String eventType, String payload, String schemaJson);
}
