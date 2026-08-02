package com.njydsz.common.json;

import com.njydsz.common.json.tree.*;
import com.njydsz.common.json.schema.JsonSchema;
import org.junit.jupiter.api.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 树模型 + JSON Schema + JSON Pointer 综合测试。
 */
@DisplayName("树模型/Schema/Pointer 综合测试")
class YdszJsonTreeSchemaPointerTest {

    // ==================== 树模型测试 ====================

    @Nested
    @DisplayName("JsonNode 树模型")
    class TreeModelTests {

        @Test
        @DisplayName("readTree 解析对象")
        void readTreeObject() {
            JsonNode root = YdszJson.readTree("{\"a\":1,\"b\":\"x\",\"c\":true}");

            assertTrue(root instanceof ObjectNode);
            assertEquals(1, root.get("a").asInt());
            assertEquals("x", root.get("b").asText());
            assertTrue(root.get("c").asBoolean());
        }

        @Test
        @DisplayName("readTree 解析数组")
        void readTreeArray() {
            JsonNode root = YdszJson.readTree("[1,2,3]");
            assertTrue(root instanceof ArrayNode);
            assertEquals(3, root.size());
            assertEquals(1, root.get(0).asInt());
        }

        @Test
        @DisplayName("readTree 解析嵌套结构")
        void readTreeNested() {
            JsonNode root = YdszJson.readTree("{\"user\":{\"id\":5,\"name\":\"z\"},\"tags\":[\"a\"]}");
            assertEquals(5, root.get("user").get("id").asInt());
            assertEquals("z", root.get("user").get("name").asText());
            assertEquals(1, root.get("tags").size());
        }

        @Test
        @DisplayName("valueToTree 序列化为 JsonNode")
        void valueToTree() {
            TestBean bean = new TestBean();
            bean.setId(42);
            bean.setName("tree");

            JsonNode node = YdszJson.valueToTree(bean);
            assertEquals(42, node.get("id").asInt());
            assertEquals("tree", node.get("name").asText());
        }

        @Test
        @DisplayName("valueToTree null → NullNode")
        void valueToTreeNull() {
            JsonNode node = YdszJson.valueToTree(null);
            assertTrue(node instanceof NullNode);
        }

        @Test
        @DisplayName("缺少的字段返回 MissingNode")
        void missingField() {
            JsonNode root = YdszJson.readTree("{\"a\":1}");
            JsonNode missing = root.get("nonexistent");
            assertTrue(missing instanceof MissingNode, "不存在字段应返回 MissingNode");
            assertTrue(missing.isMissingNode());
        }
    }

    // ==================== JSON Pointer 测试 ====================

    @Nested
    @DisplayName("JSON Pointer (RFC 6901)")
    class PointerTests {

        @Test
        @DisplayName("基础指针路径")
        void basicPointer() {
            String json = "{\"a\":{\"b\":{\"c\":42}}}";
            Object result = YdszJson.getByPointer(json, "/a/b/c");
            assertEquals(42, ((Number) result).intValue());
        }

        @Test
        @DisplayName("数组索引指针")
        void arrayIndexPointer() {
            String json = "{\"items\":[\"a\",\"b\",\"c\"]}";
            Object result = YdszJson.getByPointer(json, "/items/1");
            assertEquals("b", result);
        }
    }

    // ==================== JSON Schema 测试 ====================

    @Nested
    @DisplayName("JSON Schema 校验")
    class SchemaTests {

        @Test
        @DisplayName("类型校验通过")
        void typeValidationPass() {
            JsonSchema schema = JsonSchema.object()
                .addProperty("name", JsonSchema.string().required())
                .addProperty("age", JsonSchema.integer().minimum(0).maximum(150));

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("name", "Alice");
            data.put("age", 30);

            ValidationResult result = YdszJson.validate(data, schema);
            assertTrue(result.isValid(), "有效数据应通过校验: " + result.getErrors());
        }

        @Test
        @DisplayName("必填字段缺失")
        void requiredFieldMissing() {
            JsonSchema schema = JsonSchema.object()
                .addProperty("name", JsonSchema.string().required());

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("age", 30);

            ValidationResult result = YdszJson.validate(data, schema);
            assertFalse(result.isValid(), "缺少必填字段应校验失败");
            assertFalse(result.getErrors().isEmpty());
        }

        @Test
        @DisplayName("类型不匹配")
        void typeMismatch() {
            JsonSchema schema = JsonSchema.object()
                .addProperty("age", JsonSchema.integer());

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("age", "thirty");

            ValidationResult result = YdszJson.validate(data, schema);
            assertFalse(result.isValid(), "类型不匹配应校验失败");
        }

        @Test
        @DisplayName("字符串长度约束")
        void stringLengthConstraint() {
            JsonSchema schema = JsonSchema.string().minLength(3).maxLength(10);

            ValidationResult resultShort = YdszJson.validate("ab", schema);
            assertFalse(resultShort.isValid(), "短于最小长度应失败");

            ValidationResult resultOk = YdszJson.validate("hello", schema);
            assertTrue(resultOk.isValid(), "符合长度应通过");
        }

        @Test
        @DisplayName("数字范围约束")
        void numberRangeConstraint() {
            JsonSchema schema = JsonSchema.integer().minimum(0).maximum(100);

            assertFalse(YdszJson.validate(-1, schema).isValid());
            assertTrue(YdszJson.validate(50, schema).isValid());
            assertFalse(YdszJson.validate(101, schema).isValid());
        }

        @Test
        @DisplayName("枚举值校验")
        void enumValidation() {
            JsonSchema schema = JsonSchema.string()
                .enumValues("PENDING", "APPROVED", "REJECTED");

            assertTrue(YdszJson.validate("APPROVED", schema).isValid());
            assertFalse(YdszJson.validate("INVALID", schema).isValid());
        }

        @Test
        @DisplayName("正则表达式校验")
        void patternValidation() {
            JsonSchema schema = JsonSchema.string().pattern("^[a-z]+$");

            assertTrue(YdszJson.validate("hello", schema).isValid());
            assertFalse(YdszJson.validate("Hello123", schema).isValid());
        }

        @Test
        @DisplayName("数组项数约束")
        void arrayItemCountConstraint() {
            JsonSchema schema = JsonSchema.array()
                .minItems(1)
                .maxItems(3);

            java.util.List<String> tooFew = java.util.Collections.emptyList();
            assertFalse(YdszJson.validate(tooFew, schema).isValid());

            java.util.List<String> valid = java.util.Arrays.asList("a", "b");
            assertTrue(YdszJson.validate(valid, schema).isValid());
        }

        @Test
        @DisplayName("对象属性数约束")
        void objectPropertyCountConstraint() {
            JsonSchema schema = JsonSchema.object()
                .minProperties(1)
                .maxProperties(2);

            Map<String, Object> empty = new java.util.LinkedHashMap<>();
            assertFalse(YdszJson.validate(empty, schema).isValid());

            Map<String, Object> valid = new java.util.LinkedHashMap<>();
            valid.put("a", 1);
            assertTrue(YdszJson.validate(valid, schema).isValid());
        }

        @Test
        @DisplayName("allOf 组合校验")
        void allOfValidation() {
            JsonSchema schema = JsonSchema.object()
                .allOf(
                    JsonSchema.object().addProperty("name", JsonSchema.string()),
                    JsonSchema.object().addProperty("age", JsonSchema.integer())
                );

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("name", "Bob");
            data.put("age", 25);

            assertTrue(YdszJson.validate(data, schema).isValid());
        }
    }
}
