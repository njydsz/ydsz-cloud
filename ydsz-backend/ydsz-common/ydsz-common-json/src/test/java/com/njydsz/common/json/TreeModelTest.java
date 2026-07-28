package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.BooleanNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.MissingNode;
import com.njydsz.common.json.tree.NullNode;
import com.njydsz.common.json.tree.NumberNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.tree.TextNode;

import java.util.HashMap;
import java.util.Map;
/**
 * 树模型 (JsonNode / ObjectNode / ArrayNode) 测试。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
class TreeModelTest {

    @Test
    void testReadTree() {
        JsonNode tree = YdszJson.readTree("{\"name\":\"Alice\",\"age\":30}");
        assertNotNull(tree);
        assertTrue(tree.isObject());
    }

    @Test
    void testObjectNodeGetString() {
        ObjectNode node = new ObjectNode();
        node.put("name", "Alice");
        JsonNode nameNode = node.get("name");
        assertTrue(nameNode.isTextual());
        assertEquals("Alice", nameNode.asText());
    }

    @Test
    void testObjectNodeGetInt() {
        ObjectNode node = new ObjectNode();
        node.put("age", 30);
        JsonNode ageNode = node.get("age");
        assertTrue(ageNode.isNumber());
        assertEquals(30, ageNode.asInt());
    }

    @Test
    void testObjectNodeGetBoolean() {
        ObjectNode node = new ObjectNode();
        node.put("active", true);
        JsonNode boolNode = node.get("active");
        assertTrue(boolNode.isBoolean());
        assertTrue(boolNode.asBoolean());
    }

    @Test
    void testObjectNodeMissingField() {
        ObjectNode node = new ObjectNode();
        node.put("name", "Alice");
        JsonNode missing = node.get("nonexistent");
        assertTrue(missing instanceof MissingNode);
    }

    @Test
    void testObjectNodeRemove() {
        ObjectNode node = new ObjectNode();
        node.put("a", 1);
        node.put("b", 2);
        JsonNode removed = node.remove("a");
        assertNotNull(removed);
        assertTrue(node.get("a") instanceof MissingNode);
    }

    @Test
    void testArrayNodeAddAndGet() {
        ArrayNode array = new ArrayNode();
        array.add(new TextNode("first"));
        array.add(new NumberNode(42));
        assertEquals(2, array.size());
        assertTrue(array.get(0).isTextual());
        assertEquals("first", array.get(0).asText());
        assertTrue(array.get(1).isNumber());
        assertEquals(42, array.get(1).asInt());
    }

    @Test
    void testNullNode() {
        NullNode nullNode = NullNode.getInstance();
        assertTrue(nullNode.isNull());
        assertEquals("null", nullNode.toString());
    }

    @Test
    void testMissingNode() {
        MissingNode missing = MissingNode.getInstance();
        assertFalse(missing.isObject());
        assertFalse(missing.isArray());
        assertFalse(missing.isTextual());
    }

    @Test
    void testBooleanNode() {
        BooleanNode trueNode = BooleanNode.of(true);
        BooleanNode falseNode = BooleanNode.of(false);
        assertTrue(trueNode.asBoolean());
        assertFalse(falseNode.asBoolean());
        assertTrue(trueNode.isBoolean());
    }

    @Test
    void testTextNode() {
        TextNode text = new TextNode("hello");
        assertTrue(text.isTextual());
        assertEquals("hello", text.asText());
    }

    @Test
    void testNumberNode() {
        NumberNode intNode = new NumberNode(42);
        assertTrue(intNode.isNumber());
        assertEquals(42, intNode.asInt());

        NumberNode longNode = new NumberNode(9999999999L);
        assertEquals(9999999999L, longNode.asLong());

        NumberNode doubleNode = new NumberNode(3.14);
        assertEquals(3.14, doubleNode.asDouble(), 0.001);
    }

    @Test
    void testValueToTree() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Bob");
        data.put("age", 25);
        JsonNode tree = YdszJson.valueToTree(data);
        assertNotNull(tree);
        assertTrue(tree.isObject());
        assertEquals("Bob", tree.get("name").asText());
        assertEquals(25, tree.get("age").asInt());
    }

    @Test
    void testObjectNodeSize() {
        ObjectNode node = new ObjectNode();
        node.put("a", 1);
        node.put("b", 2);
        node.put("c", 3);
        assertEquals(3, node.size());
    }

    @Test
    void testArrayNodeSize() {
        ArrayNode array = new ArrayNode();
        array.add(new NumberNode(1));
        array.add(new NumberNode(2));
        assertEquals(2, array.size());
    }
}
