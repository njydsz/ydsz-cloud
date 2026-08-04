package com.remisoft.common.json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.remisoft.common.json.schema.JsonSchema;
import com.remisoft.common.json.schema.JsonSchemaValidator;
import com.remisoft.common.json.schema.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON Schema 验证器单元测试（P0）。
 *
 * <p>覆盖 {@link JsonSchema}/{@link JsonSchemaValidator}/{@link ValidationResult}
 * 的核心校验路径（类型/必填/枚举/字符串约束/数字约束/数组约束/对象约束）。</p>
 */
class JsonSchemaTest {

    private Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void validateStringType() {
        JsonSchema schema = JsonSchema.string();
        assertTrue(JsonSchemaValidator.validate(schema, "hello").isValid());
        assertFalse(JsonSchemaValidator.validate(schema, 42).isValid());
    }

    @Test
    void validateIntegerType() {
        JsonSchema schema = JsonSchema.integer();
        assertTrue(JsonSchemaValidator.validate(schema, 42).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "hello").isValid());
    }

    @Test
    void validateNumberType() {
        JsonSchema schema = JsonSchema.number();
        assertTrue(JsonSchemaValidator.validate(schema, 3.14).isValid());
        assertTrue(JsonSchemaValidator.validate(schema, 42).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "hello").isValid());
    }

    @Test
    void validateBooleanType() {
        JsonSchema schema = JsonSchema.booleanType();
        assertTrue(JsonSchemaValidator.validate(schema, true).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "true").isValid());
    }

    @Test
    void validateArrayType() {
        JsonSchema schema = JsonSchema.array();
        assertTrue(JsonSchemaValidator.validate(schema, List.of(1, 2, 3)).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "not array").isValid());
    }

    @Test
    void validateObjectType() {
        JsonSchema schema = JsonSchema.object();
        assertTrue(JsonSchemaValidator.validate(schema, map("a", 1)).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "not object").isValid());
    }

    @Test
    void validateRequiredFields() {
        JsonSchema schema = JsonSchema.object()
            .addProperty("name", JsonSchema.string())
            .addProperty("age", JsonSchema.integer())
            .addRequired("name")
            .addRequired("age");

        ValidationResult valid = JsonSchemaValidator.validate(schema, map("name", "Alice", "age", 30));
        assertTrue(valid.isValid());

        ValidationResult missing = JsonSchemaValidator.validate(schema, map("name", "Alice"));
        assertFalse(missing.isValid());
        assertFalse(missing.getErrors().isEmpty());
    }

    @Test
    void validateStringMinMaxLength() {
        JsonSchema schema = JsonSchema.string().minLength(2).maxLength(5);

        assertTrue(JsonSchemaValidator.validate(schema, "abc").isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "a").isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "abcdef").isValid());
    }

    @Test
    void validateStringPattern() {
        JsonSchema schema = JsonSchema.string().pattern("^[a-z]+$");

        assertTrue(JsonSchemaValidator.validate(schema, "abc").isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "ABC").isValid());
    }

    @Test
    void validateNumberMinMax() {
        JsonSchema schema = JsonSchema.integer().minimum(0).maximum(100);

        assertTrue(JsonSchemaValidator.validate(schema, 50).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, -1).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, 101).isValid());
    }

    @Test
    void validateNumberExclusiveMinMax() {
        JsonSchema schema = JsonSchema.number()
            .exclusiveMinimum(0.0)
            .exclusiveMaximum(1.0);

        assertTrue(JsonSchemaValidator.validate(schema, 0.5).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, 0).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, 1).isValid());
    }

    @Test
    void validateEnumValues() {
        JsonSchema schema = JsonSchema.string()
            .enumValues("RED", "GREEN", "BLUE");

        assertTrue(JsonSchemaValidator.validate(schema, "RED").isValid());
        assertFalse(JsonSchemaValidator.validate(schema, "YELLOW").isValid());
    }

    @Test
    void validateArrayItems() {
        JsonSchema schema = JsonSchema.array()
            .items(JsonSchema.integer())
            .minItems(1)
            .maxItems(3);

        assertTrue(JsonSchemaValidator.validate(schema, List.of(1, 2, 3)).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, List.of()).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, List.of(1, 2, 3, 4)).isValid());
        assertFalse(JsonSchemaValidator.validate(schema, List.of("a", "b")).isValid());
    }

    @Test
    void validateObjectProperties() {
        // 注意：必填字段必须通过 addRequired 注册到外层 schema 的 requiredProperties 列表，
        // 子 schema 上的 .required() 仅在值为 null 时触发，无法检测字段缺失。
        JsonSchema schema = JsonSchema.object()
            .addProperty("name", JsonSchema.string())
            .addProperty("age", JsonSchema.integer().minimum(0))
            .addRequired("name");

        ValidationResult ok = JsonSchemaValidator.validate(schema,
            map("name", "Alice", "age", 30));
        assertTrue(ok.isValid());

        ValidationResult badAge = JsonSchemaValidator.validate(schema,
            map("name", "Alice", "age", -1));
        assertFalse(badAge.isValid());

        ValidationResult missingName = JsonSchemaValidator.validate(schema,
            map("age", 30));
        assertFalse(missingName.isValid());
    }

    @Test
    void validateNullSchemaPasses() {
        ValidationResult result = JsonSchemaValidator.validate(null, "anything");
        assertTrue(result.isValid());
    }

    @Test
    void validationResultCollectsErrors() {
        JsonSchema schema = JsonSchema.object()
            .addProperty("a", JsonSchema.integer().minimum(0))
            .addProperty("b", JsonSchema.string().minLength(1));

        ValidationResult result = JsonSchemaValidator.validate(schema,
            map("a", -1, "b", ""));

        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() >= 2);
    }

    @Test
    void schemaDescription() {
        JsonSchema schema = JsonSchema.string().description("User name");
        assertEquals("User name", schema.getDescription());
    }

    @Test
    void schemaDefaultValue() {
        JsonSchema schema = JsonSchema.string().defaultValue("anonymous");
        assertEquals("anonymous", schema.getDefaultValue());
    }
}
