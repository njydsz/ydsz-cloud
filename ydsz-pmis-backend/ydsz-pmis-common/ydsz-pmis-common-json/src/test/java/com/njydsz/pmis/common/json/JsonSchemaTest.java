package com.njydsz.pmis.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.json.schema.JsonSchema;
import com.njydsz.pmis.common.json.schema.SchemaValidator;
import com.njydsz.pmis.common.json.schema.ValidationResult;

/**
 * JSON Schema 校验测试。
 *
 * @since 1.4.0
 */
class JsonSchemaTest {

    @Test
    void testStringSchemaValid() {
        JsonSchema schema = JsonSchema.string().minLength(1).maxLength(10);
        ValidationResult result = SchemaValidator.validate(schema, "hello");
        assertTrue(result.isValid());
    }

    @Test
    void testStringSchemaTooShort() {
        JsonSchema schema = JsonSchema.string().minLength(5);
        ValidationResult result = SchemaValidator.validate(schema, "hi");
        assertFalse(result.isValid());
    }

    @Test
    void testStringSchemaTooLong() {
        JsonSchema schema = JsonSchema.string().maxLength(3);
        ValidationResult result = SchemaValidator.validate(schema, "hello");
        assertFalse(result.isValid());
    }

    @Test
    void testIntegerSchemaValid() {
        JsonSchema schema = JsonSchema.integer().minimum(0).maximum(100);
        ValidationResult result = SchemaValidator.validate(schema, 50);
        assertTrue(result.isValid());
    }

    @Test
    void testIntegerSchemaBelowMinimum() {
        JsonSchema schema = JsonSchema.integer().minimum(10);
        ValidationResult result = SchemaValidator.validate(schema, 5);
        assertFalse(result.isValid());
    }

    @Test
    void testIntegerSchemaAboveMaximum() {
        JsonSchema schema = JsonSchema.integer().maximum(100);
        ValidationResult result = SchemaValidator.validate(schema, 150);
        assertFalse(result.isValid());
    }

    @Test
    void testNumberSchemaValid() {
        JsonSchema schema = JsonSchema.number();
        ValidationResult result = SchemaValidator.validate(schema, 3.14);
        assertTrue(result.isValid());
    }

    @Test
    void testBooleanSchemaValid() {
        JsonSchema schema = JsonSchema.booleanType();
        ValidationResult result = SchemaValidator.validate(schema, true);
        assertTrue(result.isValid());
    }

    @Test
    void testObjectSchemaWithRequiredProperty() {
        JsonSchema schema = JsonSchema.object()
                .addProperty("name", JsonSchema.string().required())
                .addRequired("name");

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        ValidationResult result = SchemaValidator.validate(schema, data);
        assertTrue(result.isValid());
    }

    @Test
    void testObjectSchemaMissingRequired() {
        JsonSchema schema = JsonSchema.object()
                .addProperty("name", JsonSchema.string().required())
                .addRequired("name");

        Map<String, Object> data = new HashMap<>();
        data.put("age", 30);
        ValidationResult result = SchemaValidator.validate(schema, data);
        assertFalse(result.isValid());
    }

    @Test
    void testArraySchemaValid() {
        JsonSchema schema = JsonSchema.array()
                .items(JsonSchema.integer())
                .minItems(1)
                .maxItems(5);

        ValidationResult result = SchemaValidator.validate(schema, List.of(1, 2, 3));
        assertTrue(result.isValid());
    }

    @Test
    void testArraySchemaTooFewItems() {
        JsonSchema schema = JsonSchema.array().minItems(3);
        ValidationResult result = SchemaValidator.validate(schema, List.of(1));
        assertFalse(result.isValid());
    }

    @Test
    void testNullSchemaValid() {
        JsonSchema schema = JsonSchema.nullType();
        ValidationResult result = SchemaValidator.validate(schema, null);
        assertTrue(result.isValid());
    }

    @Test
    void testRequiredNullValue() {
        JsonSchema schema = JsonSchema.string().required();
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
        JsonSchema schema = JsonSchema.string().enumValues("red", "green", "blue");
        assertTrue(SchemaValidator.validate(schema, "red").isValid());
        assertFalse(SchemaValidator.validate(schema, "yellow").isValid());
    }

    @Test
    void testViaJsonFacade() {
        JsonSchema schema = JsonSchema.object()
                .addProperty("name", JsonSchema.string())
                .addRequired("name");

        String json = "{\"name\":\"Alice\"}";
        ValidationResult result = Json.validate(json, schema);
        assertNotNull(result);
    }

    @Test
    void testPatternValidation() {
        JsonSchema schema = JsonSchema.string().pattern("^[A-Z][a-z]+$");
        assertTrue(SchemaValidator.validate(schema, "Alice").isValid());
        assertFalse(SchemaValidator.validate(schema, "alice").isValid());
    }

    @Test
    void testEnsureValid() {
        ValidationResult valid = new ValidationResult(true);
        assertDoesNotThrow(() -> Json.ensureValid(valid));

        ValidationResult invalid = new ValidationResult(false);
        invalid.addError("test");
        assertThrows(IllegalArgumentException.class, () -> Json.ensureValid(invalid));
    }
}
