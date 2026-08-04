package com.njydsz.common.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.BooleanNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.MissingNode;
import com.njydsz.common.json.tree.NullNode;
import com.njydsz.common.json.tree.NumberNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.tree.TextNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 树模型（tree 包）单元测试（P0）。
 *
 * <p>覆盖 {@link ObjectNode}/{@link ArrayNode}/{@link TextNode}/{@link NumberNode}/
 * {@link BooleanNode}/{@link NullNode}/{@link MissingNode}/{@link JsonNode} 的核心 API。</p>
 */
class TreeNodeTest {

    @BeforeEach
    void setUp() {
        SerializationProvider.clearThreadLocals();
    }

    @AfterEach
    void tearDown() {
        SerializationProvider.clearThreadLocals();
    }

    // ==================== ObjectNode ====================

    @Test
    void objectNodePutAndGetBasicTypes() {
        ObjectNode node = new ObjectNode();
        node.put("name", "Alice")
            .put("age", 30)
            .put("score", 95.5)
            .put("active", true)
            .put("id", 100L);

        assertEquals("Alice", node.getString("name"));
        assertEquals(30, node.getIntValue("age"));
        assertEquals(95.5, node.getDoubleValue("score"), 1e-9);
        assertTrue(node.getBooleanValue("active"));
        assertEquals(100L, node.getLongValue("id"));
        assertEquals(5, node.size());
    }

    @Test
    void objectNodePutNullStringConvertsToNullNode() {
        ObjectNode node = new ObjectNode();
        node.put("name", (String) null);
        assertTrue(node.get("name").isNull());
    }

    @Test
    void objectNodeHasAndRemove() {
        ObjectNode node = new ObjectNode();
        node.put("a", 1);
        node.put("b", 2);

        assertTrue(node.has("a"));
        assertTrue(node.has("b"));
        assertFalse(node.has("c"));

        JsonNode removed = node.remove("a");
        assertNotNull(removed);
        assertFalse(node.has("a"));
        assertEquals(1, node.size());
    }

    @Test
    void objectNodeFieldNames() {
        ObjectNode node = new ObjectNode();
        node.put("x", 1).put("y", 2).put("z", 3);
        Iterator<String> names = node.fieldNames();
        assertEquals("x", names.next());
        assertEquals("y", names.next());
        assertEquals("z", names.next());
        assertFalse(names.hasNext());
    }

    @Test
    void objectNodeGetReturnsMissingForAbsentField() {
        ObjectNode node = new ObjectNode();
        JsonNode absent = node.get("nonexistent");
        assertTrue(absent.isMissing());
        assertTrue(absent instanceof MissingNode);
    }

    @Test
    void objectNodeNestedObjectAndArray() {
        ObjectNode root = new ObjectNode();
        ObjectNode sub = new ObjectNode();
        sub.put("inner", 42);
        ArrayNode arr = new ArrayNode();
        arr.add(1).add(2).add(3);

        root.put("sub", sub);
        root.put("arr", arr);

        ObjectNode retrieved = root.getJSONObject("sub");
        assertNotNull(retrieved);
        assertEquals(42, retrieved.getIntValue("inner"));

        ArrayNode retrievedArr = root.getJSONArray("arr");
        assertNotNull(retrievedArr);
        assertEquals(3, retrievedArr.size());
        assertEquals(2, retrievedArr.getIntValue(1));
    }

    @Test
    void objectNodePathTraversal() {
        ObjectNode root = new ObjectNode();
        ObjectNode user = new ObjectNode();
        user.put("name", "Bob");
        root.put("user", user);

        JsonNode nameNode = root.path("user/name");
        assertFalse(nameNode.isMissing());
        assertEquals("Bob", nameNode.asText());

        JsonNode missing = root.path("user/nonexistent");
        assertTrue(missing.isMissing());
    }

    @Test
    void objectNodeTypeChecks() {
        ObjectNode node = new ObjectNode();
        assertTrue(node.isObject());
        assertFalse(node.isArray());
        assertFalse(node.isTextual());
    }

    @Test
    void objectNodeGetIntegerReturnsNullForAbsent() {
        ObjectNode node = new ObjectNode();
        assertNull(node.getInteger("absent"));
        assertNull(node.getLong("absent"));
        assertNull(node.getDouble("absent"));
        assertNull(node.getBoolean("absent"));
    }

    @Test
    void objectNodeGetWithDefaults() {
        ObjectNode node = new ObjectNode();
        assertEquals("default", node.getStringOrDefault("absent", "default"));
        assertEquals(Integer.valueOf(42), node.getIntegerOrDefault("absent", 42));
        assertEquals(Long.valueOf(99L), node.getLongOrDefault("absent", 99L));
        assertEquals(Boolean.TRUE, node.getBooleanOrDefault("absent", true));
    }

    @Test
    void objectNodePutBigDecimalAndBigInteger() {
        ObjectNode node = new ObjectNode();
        node.put("bd", new BigDecimal("3.14159"));
        node.put("bi", new BigInteger("12345678901234567890"));

        assertEquals(new BigDecimal("3.14159"), node.getBigDecimal("bd"));
        assertEquals(new BigInteger("12345678901234567890"), node.getBigInteger("bi"));
    }

    @Test
    void objectNodeToStringProducesValidJson() {
        ObjectNode node = new ObjectNode();
        node.put("name", "test").put("value", 123);
        String json = node.toString();
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"test\""));
        assertTrue(json.contains("\"value\""));
        assertTrue(json.contains("123"));
    }

    @Test
    void objectNodeParseFromJson() {
        ObjectNode node = YdszJson.parseObject("{\"name\":\"Alice\",\"age\":30}");
        assertNotNull(node);
        assertEquals("Alice", node.getString("name"));
        assertEquals(30, node.getIntValue("age"));
    }

    // ==================== ArrayNode ====================

    @Test
    void arrayNodeAddAndGetBasicTypes() {
        ArrayNode arr = new ArrayNode();
        arr.add("hello").add(42).add(3.14).add(true).add(100L);

        assertEquals("hello", arr.getString(0));
        assertEquals(42, arr.getIntValue(1));
        assertEquals(3.14, arr.getDoubleValue(2), 1e-9);
        assertTrue(arr.getBooleanValue(3));
        assertEquals(100L, arr.getLongValue(4));
        assertEquals(5, arr.size());
    }

    @Test
    void arrayNodeAddNullConvertsToNullNode() {
        ArrayNode arr = new ArrayNode();
        arr.add((String) null);
        assertTrue(arr.get(0).isNull());
    }

    @Test
    void arrayNodeRemove() {
        ArrayNode arr = new ArrayNode();
        arr.add(1).add(2).add(3);
        JsonNode removed = arr.remove(1);
        assertNotNull(removed);
        assertEquals(2, arr.size());
        assertEquals(1, arr.getIntValue(0));
        assertEquals(3, arr.getIntValue(1));
    }

    @Test
    void arrayNodeGetReturnsMissingForOutOfBounds() {
        ArrayNode arr = new ArrayNode();
        arr.add(1);
        JsonNode oob = arr.get(10);
        assertTrue(oob.isMissing());
    }

    @Test
    void arrayNodeElements() {
        ArrayNode arr = new ArrayNode();
        arr.add(1).add(2).add(3);
        Iterator<JsonNode> it = arr.elements();
        assertEquals(1, it.next().asInt());
        assertEquals(2, it.next().asInt());
        assertEquals(3, it.next().asInt());
        assertFalse(it.hasNext());
    }

    @Test
    void arrayNodeTypeChecks() {
        ArrayNode arr = new ArrayNode();
        assertTrue(arr.isArray());
        assertFalse(arr.isObject());
        assertFalse(arr.isTextual());
    }

    @Test
    void arrayNodeNestedObjectAndArray() {
        ArrayNode arr = new ArrayNode();
        ObjectNode obj = new ObjectNode();
        obj.put("key", "value");
        ArrayNode inner = new ArrayNode();
        inner.add(1).add(2);

        arr.add(obj);
        arr.add(inner);

        ObjectNode retrieved = arr.getJSONObject(0);
        assertNotNull(retrieved);
        assertEquals("value", retrieved.getString("key"));

        ArrayNode retrievedArr = arr.getJSONArray(1);
        assertNotNull(retrievedArr);
        assertEquals(2, retrievedArr.size());
    }

    @Test
    void arrayNodeParseFromJson() {
        ArrayNode arr = YdszJson.parseArrayNode("[1,2,3]");
        assertNotNull(arr);
        assertEquals(3, arr.size());
        assertEquals(1, arr.getIntValue(0));
        assertEquals(3, arr.getIntValue(2));
    }

    @Test
    void arrayNodeToStringProducesValidJson() {
        ArrayNode arr = new ArrayNode();
        arr.add("a").add(1).add(true);
        String json = arr.toString();
        assertTrue(json.startsWith("["));
        assertTrue(json.endsWith("]"));
        assertTrue(json.contains("\"a\""));
        assertTrue(json.contains("1"));
        assertTrue(json.contains("true"));
    }

    // ==================== TextNode ====================

    @Test
    void textNodeBasicOperations() {
        TextNode node = new TextNode("hello");
        assertTrue(node.isTextual());
        assertEquals("hello", node.asText());
        assertEquals("hello", node.asText("default"));
        assertFalse(node.isNumber());
        assertFalse(node.isObject());
    }

    @Test
    void textNodeEmptyString() {
        TextNode node = new TextNode("");
        assertEquals("", node.asText());
        assertTrue(node.isTextual());
    }

    // ==================== NumberNode ====================

    @Test
    void numberNodeIntOperations() {
        NumberNode node = new NumberNode(42);
        assertTrue(node.isNumber());
        assertEquals(42, node.asInt());
        assertEquals(42L, node.asLong());
        assertEquals(42.0, node.asDouble(), 1e-9);
    }

    @Test
    void numberNodeLongOperations() {
        NumberNode node = new NumberNode(9876543210L);
        assertEquals(9876543210L, node.asLong());
        assertEquals(9876543210.0, node.asDouble(), 1e-9);
    }

    @Test
    void numberNodeDoubleOperations() {
        NumberNode node = new NumberNode(3.14);
        assertEquals(3.14, node.asDouble(), 1e-9);
    }

    @Test
    void numberNodeWithDefaultValue() {
        NumberNode node = new NumberNode(42);
        assertEquals(42, node.asInt(99));
        assertEquals(42L, node.asLong(99L));
        assertEquals(42.0, node.asDouble(99.0), 1e-9);
    }

    // ==================== BooleanNode ====================

    @Test
    void booleanNodeOperations() {
        BooleanNode trueNode = BooleanNode.of(true);
        BooleanNode falseNode = BooleanNode.of(false);

        assertTrue(trueNode.isBoolean());
        assertTrue(trueNode.asBoolean());
        assertFalse(falseNode.asBoolean());

        assertTrue(trueNode.asBoolean(false));
        assertFalse(falseNode.asBoolean(true));
    }

    // ==================== NullNode / MissingNode ====================

    @Test
    void nullNodeOperations() {
        NullNode node = NullNode.getInstance();
        assertTrue(node.isNull());
        assertFalse(node.isObject());
        assertFalse(node.isTextual());
        assertNull(node.asValue());
    }

    @Test
    void missingNodeOperations() {
        MissingNode node = MissingNode.getInstance();
        assertTrue(node.isMissing());
        assertFalse(node.isNull());
        assertFalse(node.isObject());
        assertEquals(0, node.size());
        assertFalse(node.has("anything"));
    }

    @Test
    void nullNodeSingleton() {
        assertSame(NullNode.getInstance(), NullNode.getInstance());
    }

    @Test
    void missingNodeSingleton() {
        assertSame(MissingNode.getInstance(), MissingNode.getInstance());
    }

    // ==================== JsonNode asMap/asList ====================

    @Test
    void objectNodeAsMap() {
        ObjectNode node = new ObjectNode();
        node.put("a", 1).put("b", 2);
        Map<String, JsonNode> map = node.asMap();
        assertEquals(2, map.size());
        assertEquals(1, map.get("a").asInt());
        assertEquals(2, map.get("b").asInt());
    }

    @Test
    void arrayNodeAsList() {
        ArrayNode arr = new ArrayNode();
        arr.add(1).add(2).add(3);
        assertEquals(3, arr.asList().size());
    }

    // ==================== Equals / HashCode ====================

    @Test
    void objectNodeEqualsAndHashCode() {
        ObjectNode n1 = new ObjectNode();
        n1.put("a", 1);
        ObjectNode n2 = new ObjectNode();
        n2.put("a", 1);
        ObjectNode n3 = new ObjectNode();
        n3.put("a", 2);

        assertEquals(n1, n2);
        assertEquals(n1.hashCode(), n2.hashCode());
        assertFalse(n1.equals(n3));
    }

    @Test
    void arrayNodeEqualsAndHashCode() {
        ArrayNode a1 = new ArrayNode();
        a1.add(1).add(2);
        ArrayNode a2 = new ArrayNode();
        a2.add(1).add(2);
        ArrayNode a3 = new ArrayNode();
        a3.add(1).add(3);

        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertFalse(a1.equals(a3));
    }

    @Test
    void textNodeEqualsAndHashCode() {
        TextNode t1 = new TextNode("abc");
        TextNode t2 = new TextNode("abc");
        TextNode t3 = new TextNode("xyz");

        assertEquals(t1, t2);
        assertEquals(t1.hashCode(), t2.hashCode());
        assertFalse(t1.equals(t3));
    }
}
