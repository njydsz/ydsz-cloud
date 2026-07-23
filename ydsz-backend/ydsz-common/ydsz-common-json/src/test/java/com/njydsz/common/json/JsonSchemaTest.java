package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.schema.YdszJsonSchema;
import com.njydsz.common.json.schema.SchemaValidator;
import com.njydsz.common.json.schema.ValidationResult;

/**
 * JSON Schema 校验测试。
 *
 * @since 1.4.0
 */
class JsonSchemaTest {

    @Test
    void testStringSchemaValid() {
        YdszJsonSchema schema = YdszJsonSchema.string().minLength(1).maxLength(10);
        ValidationResult result = SchemaValidator.validate(schema, "hello");
        assertTrue(result.isValid());
    }

    @Test
    void testStringSchemaTooShort() {
        YdszJsonSchema schema = YdszJsonSchema.string().minLength(5);
        ValidationResult result = SchemaValidator.validate(schema, "hi");
        assertFalse(result.isValid());
    }

    @Test
    void testStringSchemaTooLong() {
        YdszJsonSchema schema = YdszJsonSchema.string().maxLength(3);
        ValidationResult result = SchemaValidator.validate(schema, "hello");
        assertFalse(result.isValid());
    }

    @Test
    void testIntegerSchemaValid() {
        YdszJsonSchema schema = YdszJsonSchema.integer().minimum(0).maximum(100);
        ValidationResult result = SchemaValidator.validate(schema, 50);
        assertTrue(result.isValid());
    }

    @Test
    void testIntegerSchemaBelowMinimum() {
        YdszJsonSchema schema = YdszJsonSchema.integer().minimum(10);
        ValidationResult result = SchemaValidator.validate(schema, 5);
        assertFalse(result.isValid());
    }

    @Test
    void testIntegerSchemaAboveMaximum() {
        YdszJsonSchema schema = YdszJsonSchema.integer().maximum(100);
        ValidationResult result = SchemaValidator.validate(schema, 150);
        assertFalse(result.isValid());
    }

    @Test
    void testNumberSchemaValid() {
        YdszJsonSchema schema = YdszJsonSchema.number();
        ValidationResult result = SchemaValidator.validate(schema, 3.14);
        assertTrue(result.isValid());
    }

    @Test
    void testBooleanSchemaValid() {
        YdszJsonSchema schema = YdszJsonSchema.booleanType();
        ValidationResult result = SchemaValidator.validate(schema, true);
        assertTrue(result.isValid());
    }

    @Test
    void testObjectSchemaWithRequiredProperty() {
        YdszJsonSchema schema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string().required())
                .addRequired("name");

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        ValidationResult result = SchemaValidator.validate(schema, data);
        assertTrue(result.isValid());
    }

    @Test
    void testObjectSchemaMissingRequired() {
        YdszJsonSchema schema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string().required())
                .addRequired("name");

        Map<String, Object> data = new HashMap<>();
        data.put("age", 30);
        ValidationResult result = SchemaValidator.validate(schema, data);
        assertFalse(result.isValid());
    }

    @Test
    void testArraySchemaValid() {
        YdszJsonSchema schema = YdszJsonSchema.array()
                .items(YdszJsonSchema.integer())
                .minItems(1)
                .maxItems(5);

        ValidationResult result = SchemaValidator.validate(schema, List.of(1, 2, 3));
        assertTrue(result.isValid());
    }

    @Test
    void testArraySchemaTooFewItems() {
        YdszJsonSchema schema = YdszJsonSchema.array().minItems(3);
        ValidationResult result = SchemaValidator.validate(schema, List.of(1));
        assertFalse(result.isValid());
    }

    @Test
    void testNullSchemaValid() {
        YdszJsonSchema schema = YdszJsonSchema.nullType();
        ValidationResult result = SchemaValidator.validate(schema, null);
        assertTrue(result.isValid());
    }

    @Test
    void testRequiredNullValue() {
        YdszJsonSchema schema = YdszJsonSchema.string().required();
        ValidationResult result = SchemaValidator.validate(schema, null);
        assertFalse(result.isValid());
    }

    @Test
    void testNullSchemaIsNullValid() {
        ValidationResult result = new ValidationResult(true);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidationResultAddError() {
        ValidationResult result = new ValidationResult(true);
        result.addError("test error");
        assertFalse(result.isValid());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().contains("test error"));
    }

    @Test
    void testValidationResultMerge() {
        ValidationResult result1 = new ValidationResult(true);
        ValidationResult result2 = new ValidationResult(false);
        result2.addError("merged error");

        result1.merge(result2);
        assertFalse(result1.isValid());
        assertTrue(result1.getErrors().contains("merged error"));
    }

    @Test
    void testEnumValues() {
        YdszJsonSchema schema = YdszJsonSchema.string().enumValues("red", "green", "blue");
        assertTrue(SchemaValidator.validate(schema, "red").isValid());
        assertFalse(SchemaValidator.validate(schema, "yellow").isValid());
    }

    @Test
    void testViaJsonFacade() {
        YdszJsonSchema schema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string())
                .addRequired("name");

        String json = "{\"name\":\"Alice\"}";
        ValidationResult result = YdszJson.validate(json, schema);
        assertNotNull(result);
    }

    @Test
    void testPatternValidation() {
        YdszJsonSchema schema = YdszJsonSchema.string().pattern("^[A-Z][a-z]+$");
        assertTrue(SchemaValidator.validate(schema, "Alice").isValid());
        assertFalse(SchemaValidator.validate(schema, "alice").isValid());
    }

    @Test
    void testEnsureValid() {
        ValidationResult valid = new ValidationResult(true);
        assertDoesNotThrow(() -> YdszJson.ensureValid(valid));

        ValidationResult invalid = new ValidationResult(false);
        invalid.addError("test");
        assertThrows(IllegalArgumentException.class, () -> YdszJson.ensureValid(invalid));
    }
}
