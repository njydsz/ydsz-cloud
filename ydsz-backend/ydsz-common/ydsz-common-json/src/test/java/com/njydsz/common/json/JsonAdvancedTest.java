package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.njydsz.common.json.object.YdszJsonArray;
import com.njydsz.common.json.object.YdszJsonObject;

import org.junit.jupiter.api.Test;

/**
 * 高级功能测试：Optional / UUID / YdszJsonObject / YdszJsonArray / isValid / fromJson / streaming。
 *
 * @since 1.0.0
 */
class JsonAdvancedTest {

    // ==================== Optional 支持 ====================

    @Test
    void testOptionalPresent() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", Optional.of("Alice"));
        String json = YdszJson.toJson(data);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"Alice\""));
    }

    @Test
    void testOptionalEmpty() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", Optional.empty());
        String json = YdszJson.toJson(data);
        assertNotNull(json);
        assertTrue(json.contains("\"name\":null"));
    }

    @Test
    void testOptionalNestedInList() {
        Map<String, Object> data = new HashMap<>();
        data.put("items", List.of(Optional.of("a"), Optional.empty(), Optional.of("c")));
        String json = YdszJson.toJson(data);
        assertNotNull(json);
        assertTrue(json.contains("\"a\""));
        assertTrue(json.contains("null"));
        assertTrue(json.contains("\"c\""));
    }

    // ==================== UUID 支持 ====================

    @Test
    void testUuidSerialization() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String json = YdszJson.toJson(uuid);
        assertNotNull(json);
        assertTrue(json.contains("550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void testUuidInMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", UUID.fromString("12345678-1234-1234-1234-123456789012"));
        String json = YdszJson.toJson(data);
        assertNotNull(json);
        assertTrue(json.contains("12345678-1234-1234-1234-123456789012"));
    }

    // ==================== isValid ====================

    @Test
    void testIsValidTrue() {
        assertTrue(YdszJson.isValid("{\"a\":1}"));
        assertTrue(YdszJson.isValid("[1,2,3]"));
        assertTrue(YdszJson.isValid("\"hello\""));
        assertTrue(YdszJson.isValid("42"));
        assertTrue(YdszJson.isValid("true"));
        assertTrue(YdszJson.isValid("null"));
    }

    @Test
    void testIsValidFalse() {
        assertFalse(YdszJson.isValid(null));
        assertFalse(YdszJson.isValid(""));
        assertFalse(YdszJson.isValid("   "));
        assertFalse(YdszJson.isValid("{invalid}"));
        assertFalse(YdszJson.isValid("{\"a\":}"));
    }

    // ==================== fromJson 别名 ====================

    @Test
    void testFromJsonAlias() {
        User user = YdszJson.fromJson("{\"name\":\"Alice\",\"age\":30}", User.class);
        assertNotNull(user);
        assertEquals("Alice", user.name);
        assertEquals(30, user.age);
    }

    // ==================== 容错解析 ====================

    @Test
    void testToObjectWithDefaultValue() {
        User defaultUser = new User();
        defaultUser.name = "default";
        User user = YdszJson.toObject("{invalid}", User.class, defaultUser);
        assertEquals("default", user.name);
    }

    @Test
    void testToObjectWithDefaultValueOnNull() {
        User defaultUser = new User();
        defaultUser.name = "default";
        User user = YdszJson.toObject(null, User.class, defaultUser);
        assertEquals("default", user.name);
    }

    // ==================== YdszJsonObject ====================

    @Test
    void testJsonObjectCreation() {
        YdszJsonObject obj = YdszJson.object();
        assertNotNull(obj);
        obj.put("name", "Alice");
        obj.put("age", 30);
        assertEquals("Alice", obj.getString("name"));
        assertEquals(30, obj.getInteger("age"));
    }

    @Test
    void testJsonObjectFromJson() {
        YdszJsonObject obj = YdszJson.parseObjectToJsonObject("{\"name\":\"Bob\",\"age\":25}");
        assertNotNull(obj);
        assertEquals("Bob", obj.getString("name"));
    }

    @Test
    void testJsonObjectFluentChain() {
        YdszJsonObject obj = YdszJson.object()
                .put("a", 1)
                .put("b", "hello")
                .put("c", true);
        assertEquals(1, obj.get("a"));
        assertEquals("hello", obj.get("b"));
        assertEquals(true, obj.get("c"));
    }

    // ==================== YdszJsonArray ====================

    @Test
    void testJsonArrayCreation() {
        YdszJsonArray arr = YdszJson.array();
        assertNotNull(arr);
        arr.add("first");
        arr.add(42);
        arr.add(true);
        assertEquals(3, arr.size());
        assertEquals("first", arr.get(0));
    }

    @Test
    void testJsonArrayFromJson() {
        YdszJsonArray arr = YdszJson.parseArrayToJsonArray("[1,2,3]");
        assertNotNull(arr);
        assertEquals(3, arr.size());
    }

    // ==================== 流式 API ====================

    @Test
    void testStreamingOutput() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        YdszJson.toJson(Map.of("key", "value"), out);
        String json = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertNotNull(json);
        assertTrue(json.contains("\"key\":\"value\""));
    }

    @Test
    void testStreamingInput() throws Exception {
        byte[] bytes = "{\"name\":\"Alice\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes);
        User user = YdszJson.toObject(in, User.class);
        assertNotNull(user);
        assertEquals("Alice", user.name);
    }

    @Test
    void testFromJsonBytes() {
        byte[] bytes = "{\"name\":\"Bob\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        User user = YdszJson.fromJsonBytes(bytes, User.class);
        assertNotNull(user);
        assertEquals("Bob", user.name);
    }

    // ==================== fromJsonToMap ====================

    @Test
    void testFromJsonToMap() {
        Map<String, String> result = YdszJson.fromJsonToMap(
                "{\"a\":\"1\",\"b\":\"2\"}", String.class, String.class);
        assertNotNull(result);
        assertEquals("1", result.get("a"));
        assertEquals("2", result.get("b"));
    }

    // ==================== parseArray with type ====================

    @Test
    void testParseArrayWithType() {
        List<Integer> result = YdszJson.parseArray("[1,2,3]", Integer.class);
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(1, result.get(0));
        assertEquals(3, result.get(2));
    }

    // ==================== createGenerator ====================

    @Test
    void testCreateGenerator() throws Exception {
        java.io.StringWriter sw = new java.io.StringWriter();
        try (var gen = YdszJson.createGenerator(sw)) {
            gen.writeStartObject();
            gen.writeName("key");
            gen.writeString("value");
            gen.writeEndObject();
        }
        assertEquals("{\"key\":\"value\"}", sw.toString());
    }

    // --- Test helper ---
    public static class User {
        public String name;
        public int age;
    }
}
