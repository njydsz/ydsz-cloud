package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.schema.YdszJsonSchema;
import com.njydsz.pmis.common.json.schema.SchemaValidator;
import com.njydsz.pmis.common.json.schema.ValidationResult;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YdszJson Schema 验证测试")
class YdszJsonSchemaTest {

    // ==================== String Schema ====================

    @Nested
    @DisplayName("String Schema 测试")
    class StringSchemaTests {

        @Test
        @DisplayName("字符串 minLength 验证 - 通过")
        void stringMinLengthValid() {
            YdszJsonSchema schema = YdszJsonSchema.string().minLength(3);
            ValidationResult result = SchemaValidator.validate(schema, "hello");
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("字符串 minLength 验证 - 失败")
        void stringMinLengthInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.string().minLength(5);
            ValidationResult result = SchemaValidator.validate(schema, "hi");
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("字符串 maxLength 验证 - 通过")
        void stringMaxLengthValid() {
            YdszJsonSchema schema = YdszJsonSchema.string().maxLength(10);
            ValidationResult result = SchemaValidator.validate(schema, "hello");
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("字符串 maxLength 验证 - 失败")
        void stringMaxLengthInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.string().maxLength(3);
            ValidationResult result = SchemaValidator.validate(schema, "hello");
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("字符串 pattern 验证 - 通过")
        void stringPatternValid() {
            YdszJsonSchema schema = YdszJsonSchema.string().pattern("^[a-z]+$");
            ValidationResult result = SchemaValidator.validate(schema, "hello");
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("字符串 pattern 验证 - 失败")
        void stringPatternInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.string().pattern("^[a-z]+$");
            ValidationResult result = SchemaValidator.validate(schema, "Hello123");
            assertFalse(result.isValid());
        }
    }

    // ==================== Number Schema ====================

    @Nested
    @DisplayName("Number Schema 测试")
    class NumberSchemaTests {

        @Test
        @DisplayName("数字 minimum 验证 - 通过")
        void numberMinimumValid() {
            YdszJsonSchema schema = YdszJsonSchema.number().minimum(0);
            ValidationResult result = SchemaValidator.validate(schema, 5);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("数字 minimum 验证 - 失败")
        void numberMinimumInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.number().minimum(10);
            ValidationResult result = SchemaValidator.validate(schema, 5);
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("数字 maximum 验证 - 通过")
        void numberMaximumValid() {
            YdszJsonSchema schema = YdszJsonSchema.number().maximum(100);
            ValidationResult result = SchemaValidator.validate(schema, 50);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("数字 maximum 验证 - 失败")
        void numberMaximumInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.number().maximum(10);
            ValidationResult result = SchemaValidator.validate(schema, 50);
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("数字 exclusiveMinimum 验证")
        void numberExclusiveMinimum() {
            YdszJsonSchema schema = YdszJsonSchema.number().exclusiveMinimum(10);
            assertFalse(SchemaValidator.validate(schema, 10).isValid());
            assertTrue(SchemaValidator.validate(schema, 11).isValid());
        }

        @Test
        @DisplayName("数字 exclusiveMaximum 验证")
        void numberExclusiveMaximum() {
            YdszJsonSchema schema = YdszJsonSchema.number().exclusiveMaximum(10);
            assertFalse(SchemaValidator.validate(schema, 10).isValid());
            assertTrue(SchemaValidator.validate(schema, 9).isValid());
        }
    }

    // ==================== Integer Schema ====================

    @Nested
    @DisplayName("Integer Schema 测试")
    class IntegerSchemaTests {

        @Test
        @DisplayName("整数类型验证 - 通过")
        void integerTypeValid() {
            YdszJsonSchema schema = YdszJsonSchema.integer();
            ValidationResult result = SchemaValidator.validate(schema, 42);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("整数类型验证 - 失败（浮点数）")
        void integerTypeInvalidFloat() {
            YdszJsonSchema schema = YdszJsonSchema.integer();
            ValidationResult result = SchemaValidator.validate(schema, 3.14);
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("Long 类型通过 integer 验证")
        void longTypePassesIntegerValidation() {
            YdszJsonSchema schema = YdszJsonSchema.integer();
            ValidationResult result = SchemaValidator.validate(schema, 42L);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("整数 minimum/maximum 验证")
        void integerRange() {
            YdszJsonSchema schema = YdszJsonSchema.integer().minimum(0).maximum(100);
            assertTrue(SchemaValidator.validate(schema, 50).isValid());
            assertFalse(SchemaValidator.validate(schema, -1).isValid());
            assertFalse(SchemaValidator.validate(schema, 101).isValid());
        }
    }

    // ==================== Boolean Schema ====================

    @Nested
    @DisplayName("Boolean Schema 测试")
    class BooleanSchemaTests {

        @Test
        @DisplayName("布尔类型验证 - true")
        void booleanTypeTrue() {
            YdszJsonSchema schema = YdszJsonSchema.booleanType();
            assertTrue(SchemaValidator.validate(schema, true).isValid());
        }

        @Test
        @DisplayName("布尔类型验证 - false")
        void booleanTypeFalse() {
            YdszJsonSchema schema = YdszJsonSchema.booleanType();
            assertTrue(SchemaValidator.validate(schema, false).isValid());
        }

        @Test
        @DisplayName("布尔类型验证 - 失败（字符串）")
        void booleanTypeInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.booleanType();
            assertFalse(SchemaValidator.validate(schema, "true").isValid());
        }
    }

    // ==================== Array Schema ====================

    @Nested
    @DisplayName("Array Schema 测试")
    class ArraySchemaTests {

        @Test
        @DisplayName("数组 minItems 验证 - 通过")
        void arrayMinItemsValid() {
            YdszJsonSchema schema = YdszJsonSchema.array().minItems(1);
            List<Integer> data = Arrays.asList(1, 2, 3);
            assertTrue(SchemaValidator.validate(schema, data).isValid());
        }

        @Test
        @DisplayName("数组 minItems 验证 - 失败")
        void arrayMinItemsInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.array().minItems(5);
            List<Integer> data = Arrays.asList(1, 2);
            assertFalse(SchemaValidator.validate(schema, data).isValid());
        }

        @Test
        @DisplayName("数组 maxItems 验证 - 通过")
        void arrayMaxItemsValid() {
            YdszJsonSchema schema = YdszJsonSchema.array().maxItems(5);
            List<Integer> data = Arrays.asList(1, 2, 3);
            assertTrue(SchemaValidator.validate(schema, data).isValid());
        }

        @Test
        @DisplayName("数组 maxItems 验证 - 失败")
        void arrayMaxItemsInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.array().maxItems(2);
            List<Integer> data = Arrays.asList(1, 2, 3);
            assertFalse(SchemaValidator.validate(schema, data).isValid());
        }

        @Test
        @DisplayName("数组 items 类型验证 - 通过")
        void arrayItemsTypeValid() {
            YdszJsonSchema schema = YdszJsonSchema.array().items(YdszJsonSchema.integer());
            List<Integer> data = Arrays.asList(1, 2, 3);
            assertTrue(SchemaValidator.validate(schema, data).isValid());
        }

        @Test
        @DisplayName("数组 items 类型验证 - 失败")
        void arrayItemsTypeInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.array().items(YdszJsonSchema.integer());
            List<Object> data = Arrays.asList(1, "two", 3);
            assertFalse(SchemaValidator.validate(schema, data).isValid());
        }
    }

    // ==================== Object Schema ====================

    @Nested
    @DisplayName("Object Schema 测试")
    class ObjectSchemaTests {

        @Test
        @DisplayName("对象 required 属性验证 - 通过")
        void objectRequiredValid() {
            YdszJsonSchema schema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string())
                .addRequired("name");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", "John");
            assertTrue(SchemaValidator.validate(schema, data).isValid());
        }

        @Test
        @DisplayName("对象 required 属性验证 - 失败")
        void objectRequiredInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string())
                .addRequired("name");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("age", 30);
            assertFalse(SchemaValidator.validate(schema, data).isValid());
        }

        @Test
        @DisplayName("对象属性类型验证")
        void objectPropertyTypeValidation() {
            YdszJsonSchema schema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string())
                .addProperty("age", YdszJsonSchema.integer());

            Map<String, Object> validData = new LinkedHashMap<>();
            validData.put("name", "John");
            validData.put("age", 30);
            assertTrue(SchemaValidator.validate(schema, validData).isValid());

            Map<String, Object> invalidData = new LinkedHashMap<>();
            invalidData.put("name", 123);
            invalidData.put("age", 30);
            assertFalse(SchemaValidator.validate(schema, invalidData).isValid());
        }
    }

    // ==================== Enum Schema ====================

    @Nested
    @DisplayName("Enum Schema 测试")
    class EnumSchemaTests {

        @Test
        @DisplayName("枚举验证 - 通过")
        void enumValid() {
            YdszJsonSchema schema = YdszJsonSchema.string().enumValues("red", "green", "blue");
            assertTrue(SchemaValidator.validate(schema, "red").isValid());
            assertTrue(SchemaValidator.validate(schema, "green").isValid());
        }

        @Test
        @DisplayName("枚举验证 - 失败")
        void enumInvalid() {
            YdszJsonSchema schema = YdszJsonSchema.string().enumValues("red", "green", "blue");
            assertFalse(SchemaValidator.validate(schema, "yellow").isValid());
        }
    }

    // ==================== 嵌套 Schema ====================

    @Nested
    @DisplayName("嵌套 Schema 测试")
    class NestedSchemaTests {

        @Test
        @DisplayName("嵌套对象 Schema 验证")
        void nestedObjectSchema() {
            YdszJsonSchema addressSchema = YdszJsonSchema.object()
                .addProperty("city", YdszJsonSchema.string())
                .addProperty("street", YdszJsonSchema.string())
                .addRequired("city");

            YdszJsonSchema userSchema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string())
                .addProperty("address", addressSchema)
                .addRequired("name");

            Map<String, Object> address = new LinkedHashMap<>();
            address.put("city", "Beijing");
            address.put("street", "Chaoyang");

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("name", "John");
            user.put("address", address);

            assertTrue(SchemaValidator.validate(userSchema, user).isValid());
        }

        @Test
        @DisplayName("嵌套对象 Schema 验证 - 缺少必需字段")
        void nestedObjectSchemaMissingRequired() {
            YdszJsonSchema addressSchema = YdszJsonSchema.object()
                .addProperty("city", YdszJsonSchema.string())
                .addRequired("city");

            YdszJsonSchema userSchema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string())
                .addProperty("address", addressSchema)
                .addRequired("name");

            Map<String, Object> address = new LinkedHashMap<>();

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("name", "John");
            user.put("address", address);

            assertFalse(SchemaValidator.validate(userSchema, user).isValid());
        }
    }

    // ==================== ensureValid ====================

    @Nested
    @DisplayName("ensureValid 测试")
    class EnsureValidTests {

        @Test
        @DisplayName("ensureValid 验证通过时不抛异常")
        void ensureValidPass() {
            YdszJsonSchema schema = YdszJsonSchema.string();
            ValidationResult result = SchemaValidator.validate(schema, "hello");
            assertDoesNotThrow(() -> YdszJson.ensureValid(result));
        }

        @Test
        @DisplayName("ensureValid 验证失败时抛出 IllegalArgumentException")
        void ensureValidFail() {
            YdszJsonSchema schema = YdszJsonSchema.string().minLength(10);
            ValidationResult result = SchemaValidator.validate(schema, "hi");
            assertThrows(IllegalArgumentException.class, () -> YdszJson.ensureValid(result));
        }
    }

    // ==================== null 值处理 ====================

    @Nested
    @DisplayName("null 值处理")
    class NullValueTests {

        @Test
        @DisplayName("null 值与 required schema 验证失败")
        void nullWithRequiredSchema() {
            YdszJsonSchema schema = YdszJsonSchema.string().required();
            ValidationResult result = SchemaValidator.validate(schema, null);
            assertFalse(result.isValid());
        }

        @Test
        @DisplayName("null 值与非 required schema 验证通过")
        void nullWithNonRequiredSchema() {
            YdszJsonSchema schema = YdszJsonSchema.string();
            ValidationResult result = SchemaValidator.validate(schema, null);
            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("null schema 验证始终通过")
        void nullSchemaAlwaysValid() {
            ValidationResult result = SchemaValidator.validate(null, "anything");
            assertTrue(result.isValid());
        }
    }

    // ==================== JSON 字符串验证 ====================

    @Nested
    @DisplayName("JSON 字符串验证")
    class JsonStringValidationTests {

        @Test
        @DisplayName("从 JSON 字符串验证")
        void validateFromJsonString() {
            YdszJsonSchema schema = YdszJsonSchema.object()
                .addProperty("name", YdszJsonSchema.string())
                .addRequired("name");

            String json = "{\"name\":\"John\"}";
            ValidationResult result = YdszJson.validate(json, schema);
            assertTrue(result.isValid());
        }
    }

    // ==================== ValidationResult ====================

    @Nested
    @DisplayName("ValidationResult 测试")
    class ValidationResultTests {

        @Test
        @DisplayName("ValidationResult 错误信息收集")
        void validationErrors() {
            ValidationResult result = new ValidationResult(true);
            result.addError("error1");
            result.addError("error2");
            assertFalse(result.isValid());
            assertEquals(2, result.getErrors().size());
            assertTrue(result.hasErrors());
        }

        @Test
        @DisplayName("ValidationResult merge 合并")
        void validationResultMerge() {
            ValidationResult r1 = new ValidationResult(true);
            r1.addError("error1");
            ValidationResult r2 = new ValidationResult(true);
            r2.addError("error2");

            r1.merge(r2);
            assertEquals(2, r1.getErrors().size());
        }

        @Test
        @DisplayName("ValidationResult 无错误时 isValid 为 true")
        void validationResultNoErrors() {
            ValidationResult result = new ValidationResult(true);
            assertTrue(result.isValid());
            assertFalse(result.hasErrors());
            assertEquals(0, result.getErrors().size());
        }
    }
}
