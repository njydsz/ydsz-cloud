package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.tree.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YdszJson Tree Model 测试")
class YdszJsonTreeTest {

    // ==================== readTree ====================

    @Nested
    @DisplayName("readTree 测试")
    class ReadTreeTests {

        @Test
        @DisplayName("从 JSON 字符串读取 ObjectNode")
        void readTreeObject() {
            JsonNode node = YdszJson.readTree("{\"name\":\"John\",\"age\":30}");
            assertTrue(node.isObject());
            assertEquals("John", node.get("name").asText());
            assertEquals(30, node.get("age").asInt());
        }

        @Test
        @DisplayName("从 JSON 字符串读取 ArrayNode")
        void readTreeArray() {
            JsonNode node = YdszJson.readTree("[1,2,3]");
            assertTrue(node.isArray());
            assertEquals(3, node.size());
            assertEquals(1, node.get(0).asInt());
        }

        @Test
        @DisplayName("从 JSON 字符串读取嵌套对象")
        void readTreeNestedObject() {
            JsonNode node = YdszJson.readTree("{\"user\":{\"name\":\"John\"}}");
            assertTrue(node.isObject());
            JsonNode user = node.get("user");
            assertTrue(user.isObject());
            assertEquals("John", user.get("name").asText());
        }

        @Test
        @DisplayName("从 JSON 字符串读取布尔值数组")
        void readTreeBooleanArray() {
            JsonNode node = YdszJson.readTree("[true,false]");
            assertTrue(node.isArray());
            assertTrue(node.get(0).asBoolean());
            assertFalse(node.get(1).asBoolean());
        }

        @Test
        @DisplayName("从 JSON 字符串读取 null 值")
        void readTreeNull() {
            JsonNode node = YdszJson.readTree("{\"value\":null}");
            assertTrue(node.isObject());
            assertTrue(node.get("value").isNull());
        }
    }

    // ==================== valueToTree ====================

    @Nested
    @DisplayName("valueToTree 测试")
    class ValueToTreeTests {

        @Test
        @DisplayName("从 Map 转换为 JsonNode 树")
        void valueToTreeFromMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", "John");
            map.put("age", 30);
            JsonNode node = YdszJson.valueToTree(map);
            assertTrue(node.isObject());
            assertEquals("John", node.get("name").asText());
        }

        @Test
        @DisplayName("从 List 转换为 JsonNode 树")
        void valueToTreeFromList() {
            List<String> list = Arrays.asList("a", "b", "c");
            JsonNode node = YdszJson.valueToTree(list);
            assertTrue(node.isArray());
            assertEquals(3, node.size());
        }
    }

    // ==================== ObjectNode 操作 ====================

    @Nested
    @DisplayName("ObjectNode 操作测试")
    class ObjectNodeTests {

        @Test
        @DisplayName("put 字符串")
        void putString() {
            ObjectNode node = new ObjectNode();
            node.put("name", "John");
            assertTrue(node.has("name"));
            assertEquals("John", node.get("name").asText());
        }

        @Test
        @DisplayName("put 整数")
        void putInt() {
            ObjectNode node = new ObjectNode();
            node.put("age", 30);
            assertEquals(30, node.get("age").asInt());
        }

        @Test
        @DisplayName("put 长整数")
        void putLong() {
            ObjectNode node = new ObjectNode();
            node.put("id", 9999999999L);
            assertEquals(9999999999L, node.get("id").asLong());
        }

        @Test
        @DisplayName("put 双精度浮点数")
        void putDouble() {
            ObjectNode node = new ObjectNode();
            node.put("score", 95.5);
            assertEquals(95.5, node.get("score").asDouble(), 0.001);
        }

        @Test
        @DisplayName("put 布尔值")
        void putBoolean() {
            ObjectNode node = new ObjectNode();
            node.put("active", true);
            assertTrue(node.get("active").asBoolean());
            node.put("deleted", false);
            assertFalse(node.get("deleted").asBoolean());
        }

        @Test
        @DisplayName("put null 值")
        void putNull() {
            ObjectNode node = new ObjectNode();
            node.put("value", (String) null);
            assertTrue(node.get("value").isNull());
        }

        @Test
        @DisplayName("put JsonNode")
        void putJsonNode() {
            ObjectNode node = new ObjectNode();
            ArrayNode arr = new ArrayNode();
            arr.add(1);
            node.put("items", arr);
            assertTrue(node.get("items").isArray());
            assertEquals(1, node.get("items").size());
        }

        @Test
        @DisplayName("remove 字段")
        void removeField() {
            ObjectNode node = new ObjectNode();
            node.put("name", "John");
            node.put("age", 30);
            node.remove("age");
            assertFalse(node.has("age"));
            assertEquals(1, node.size());
        }

        @Test
        @DisplayName("fieldNames 迭代")
        void fieldNamesIteration() {
            ObjectNode node = new ObjectNode();
            node.put("a", 1);
            node.put("b", 2);
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            assertEquals(2, names.size());
            assertTrue(names.contains("a"));
            assertTrue(names.contains("b"));
        }

        @Test
        @DisplayName("size 返回字段数")
        void sizeReturnsFieldCount() {
            ObjectNode node = new ObjectNode();
            assertEquals(0, node.size());
            node.put("a", 1);
            assertEquals(1, node.size());
            node.put("b", 2);
            assertEquals(2, node.size());
        }

        @Test
        @DisplayName("toString 输出 JSON")
        void toStringOutputsJson() {
            ObjectNode node = new ObjectNode();
            node.put("name", "John");
            String json = node.toString();
            assertTrue(json.startsWith("{"));
            assertTrue(json.endsWith("}"));
            assertTrue(json.contains("name"));
        }

        @Test
        @DisplayName("get 不存在的字段返回 MissingNode")
        void getNonExistingReturnsMissing() {
            ObjectNode node = new ObjectNode();
            JsonNode result = node.get("nonexistent");
            assertTrue(result.isMissing());
        }
    }

    // ==================== ArrayNode 操作 ====================

    @Nested
    @DisplayName("ArrayNode 操作测试")
    class ArrayNodeTests {

        @Test
        @DisplayName("add 字符串")
        void addString() {
            ArrayNode node = new ArrayNode();
            node.add("hello");
            assertEquals(1, node.size());
            assertEquals("hello", node.get(0).asText());
        }

        @Test
        @DisplayName("add 整数")
        void addInt() {
            ArrayNode node = new ArrayNode();
            node.add(42);
            assertEquals(42, node.get(0).asInt());
        }

        @Test
        @DisplayName("add 双精度浮点数")
        void addDouble() {
            ArrayNode node = new ArrayNode();
            node.add(3.14);
            assertEquals(3.14, node.get(0).asDouble(), 0.001);
        }

        @Test
        @DisplayName("add 布尔值")
        void addBoolean() {
            ArrayNode node = new ArrayNode();
            node.add(true);
            assertTrue(node.get(0).asBoolean());
        }

        @Test
        @DisplayName("add JsonNode")
        void addJsonNode() {
            ArrayNode node = new ArrayNode();
            ObjectNode obj = new ObjectNode();
            obj.put("key", "value");
            node.add(obj);
            assertTrue(node.get(0).isObject());
        }

        @Test
        @DisplayName("set 替换元素")
        void setElement() {
            ArrayNode node = new ArrayNode();
            node.add(1);
            node.add(2);
            node.set(0, new TextNode("replaced"));
            assertEquals("replaced", node.get(0).asText());
        }

        @Test
        @DisplayName("remove 移除元素")
        void removeElement() {
            ArrayNode node = new ArrayNode();
            node.add(1);
            node.add(2);
            node.add(3);
            node.remove(1);
            assertEquals(2, node.size());
            assertEquals(1, node.get(0).asInt());
            assertEquals(3, node.get(1).asInt());
        }

        @Test
        @DisplayName("elements 迭代")
        void elementsIteration() {
            ArrayNode node = new ArrayNode();
            node.add(1);
            node.add(2);
            List<JsonNode> items = new ArrayList<>();
            node.elements().forEachRemaining(items::add);
            assertEquals(2, items.size());
        }

        @Test
        @DisplayName("越界索引返回 MissingNode")
        void outOfBoundsReturnsMissing() {
            ArrayNode node = new ArrayNode();
            node.add(1);
            JsonNode missing = node.get(5);
            assertTrue(missing.isMissing());
        }

        @Test
        @DisplayName("has 检查索引是否存在")
        void hasIndexCheck() {
            ArrayNode node = new ArrayNode();
            node.add(1);
            assertTrue(node.has(0));
            assertFalse(node.has(1));
        }
    }

    // ==================== 节点类型检查 ====================

    @Nested
    @DisplayName("节点类型检查测试")
    class NodeTypeTests {

        @Test
        @DisplayName("TextNode 类型检查")
        void textNodeType() {
            TextNode node = new TextNode("hello");
            assertTrue(node.isTextual());
            assertFalse(node.isNumber());
            assertFalse(node.isBoolean());
            assertFalse(node.isNull());
            assertFalse(node.isObject());
            assertFalse(node.isArray());
        }

        @Test
        @DisplayName("NumberNode 类型检查")
        void numberNodeType() {
            NumberNode node = new NumberNode(42);
            assertTrue(node.isNumber());
            assertFalse(node.isTextual());
        }

        @Test
        @DisplayName("BooleanNode 类型检查")
        void booleanNodeType() {
            BooleanNode trueNode = BooleanNode.of(true);
            assertTrue(trueNode.isBoolean());
            assertTrue(trueNode.asBoolean());

            BooleanNode falseNode = BooleanNode.of(false);
            assertTrue(falseNode.isBoolean());
            assertFalse(falseNode.asBoolean());
        }

        @Test
        @DisplayName("NullNode 类型检查")
        void nullNodeType() {
            NullNode node = NullNode.getInstance();
            assertTrue(node.isNull());
            assertFalse(node.isTextual());
        }

        @Test
        @DisplayName("ObjectNode 类型检查")
        void objectNodeType() {
            ObjectNode node = new ObjectNode();
            assertTrue(node.isObject());
            assertFalse(node.isArray());
        }

        @Test
        @DisplayName("ArrayNode 类型检查")
        void arrayNodeType() {
            ArrayNode node = new ArrayNode();
            assertTrue(node.isArray());
            assertFalse(node.isObject());
        }
    }

    // ==================== path 导航 ====================

    @Nested
    @DisplayName("path 导航测试")
    class PathNavigationTests {

        @Test
        @DisplayName("path 导航到嵌套字段")
        void pathNavigationNested() {
            ObjectNode root = new ObjectNode();
            ObjectNode child = new ObjectNode();
            child.put("name", "John");
            root.put("user", child);

            JsonNode result = root.path("user/name");
            assertEquals("John", result.asText());
        }

        @Test
        @DisplayName("path 不存在的字段返回 MissingNode")
        void pathNonExistingReturnsMissing() {
            ObjectNode root = new ObjectNode();
            root.put("name", "John");
            JsonNode result = root.path("nonexistent");
            assertTrue(result.isMissing());
        }

        @Test
        @DisplayName("path 导航到数组元素")
        void pathNavigationArrayElement() {
            ObjectNode root = new ObjectNode();
            ArrayNode arr = new ArrayNode();
            arr.add(10);
            arr.add(20);
            root.put("items", arr);

            JsonNode result = root.path("items/0");
            assertEquals(10, result.asInt());
        }
    }

    // ==================== MissingNode ====================

    @Nested
    @DisplayName("MissingNode 测试")
    class MissingNodeTests {

        @Test
        @DisplayName("MissingNode 单例")
        void missingNodeSingleton() {
            MissingNode m1 = MissingNode.getInstance();
            MissingNode m2 = MissingNode.getInstance();
            assertSame(m1, m2);
        }

        @Test
        @DisplayName("MissingNode isMissing 返回 true")
        void missingNodeIsMissing() {
            assertTrue(MissingNode.getInstance().isMissing());
        }

        @Test
        @DisplayName("MissingNode asText 返回空字符串")
        void missingNodeAsText() {
            assertEquals("", MissingNode.getInstance().asText());
        }

        @Test
        @DisplayName("MissingNode asValue 返回 null")
        void missingNodeAsValue() {
            assertNull(MissingNode.getInstance().asValue());
        }
    }

    // ==================== asValue 转换 ====================

    @Nested
    @DisplayName("asValue 转换测试")
    class AsValueTests {

        @Test
        @DisplayName("TextNode asValue 返回字符串")
        void textNodeAsValue() {
            assertEquals("hello", new TextNode("hello").asValue());
        }

        @Test
        @DisplayName("NumberNode asValue 返回数值")
        void numberNodeAsValue() {
            assertEquals(42, new NumberNode(42).asValue());
        }

        @Test
        @DisplayName("BooleanNode asValue 返回布尔值")
        void booleanNodeAsValue() {
            assertEquals(true, BooleanNode.of(true).asValue());
        }

        @Test
        @DisplayName("NullNode asValue 返回 null")
        void nullNodeAsValue() {
            assertNull(NullNode.getInstance().asValue());
        }

        @Test
        @DisplayName("ObjectNode asValue 返回 Map")
        void objectNodeAsValue() {
            ObjectNode node = new ObjectNode();
            node.put("key", "value");
            Object value = node.asValue();
            assertInstanceOf(Map.class, value);
        }

        @Test
        @DisplayName("ArrayNode asValue 返回 List")
        void arrayNodeAsValue() {
            ArrayNode node = new ArrayNode();
            node.add(1);
            Object value = node.asValue();
            assertInstanceOf(List.class, value);
        }
    }
}
