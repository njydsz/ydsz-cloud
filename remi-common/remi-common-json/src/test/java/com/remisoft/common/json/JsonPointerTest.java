package com.remisoft.common.json;

import com.remisoft.common.json.exception.JsonException;
import com.remisoft.common.json.pointer.JsonPointer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON Pointer（RFC 6901）单元测试（P1）。
 */
class JsonPointerTest {

    @Test
    void emptyPointerReturnsWholeDocument() {
        JsonPointer p = new JsonPointer("");
        Object result = p.evaluate("{\"a\":1}");
        // 空 Pointer 返回原始 JSON 字符串（RFC 6901 规范）
        assertTrue(result instanceof String);
    }

    @Test
    void rootFieldAccess() {
        JsonPointer p = new JsonPointer("/a");
        Object result = p.evaluate("{\"a\":42,\"b\":\"hello\"}");
        assertEquals(42, ((Number) result).intValue());
    }

    @Test
    void nestedFieldAccess() {
        JsonPointer p = new JsonPointer("/user/name");
        Object result = p.evaluate("{\"user\":{\"name\":\"Alice\",\"age\":30}}");
        assertEquals("Alice", result);
    }

    @Test
    void arrayIndexAccess() {
        JsonPointer p = new JsonPointer("/items/1");
        Object result = p.evaluate("{\"items\":[10,20,30]}");
        assertEquals(20, ((Number) result).intValue());
    }

    @Test
    void arrayRootAccess() {
        JsonPointer p = new JsonPointer("/0");
        Object result = p.evaluate("[\"a\",\"b\",\"c\"]");
        assertEquals("a", result);
    }

    @Test
    void deeplyNestedArrayAndObject() {
        JsonPointer p = new JsonPointer("/data/1/key");
        Object result = p.evaluate("{\"data\":[{\"key\":\"x\"},{\"key\":\"y\"}]}");
        assertEquals("y", result);
    }

    @Test
    void escapedSlashInFieldName() {
        // ~1 → /
        JsonPointer p = new JsonPointer("/a~1b");
        Object result = p.evaluate("{\"a/b\":\"value\"}");
        assertEquals("value", result);
    }

    @Test
    void escapedTildeInFieldName() {
        // ~0 → ~
        JsonPointer p = new JsonPointer("/a~0b");
        Object result = p.evaluate("{\"a~b\":\"value\"}");
        assertEquals("value", result);
    }

    @Test
    void nullPointerThrows() {
        assertThrows(JsonException.class, () -> new JsonPointer(null));
    }

    @Test
    void invalidPointerNotStartingWithSlashThrows() {
        assertThrows(JsonException.class, () -> new JsonPointer("abc"));
    }

    @Test
    void pathNotFoundThrows() {
        JsonPointer p = new JsonPointer("/nonexistent");
        assertThrows(JsonException.class, () -> p.evaluate("{\"a\":1}"));
    }

    @Test
    void arrayIndexOutOfBoundsThrows() {
        JsonPointer p = new JsonPointer("/items/10");
        assertThrows(JsonException.class, () -> p.evaluate("{\"items\":[1,2,3]}"));
    }

    @Test
    void arrayIndexMustBeInteger() {
        JsonPointer p = new JsonPointer("/items/abc");
        assertThrows(JsonException.class, () -> p.evaluate("{\"items\":[1,2,3]}"));
    }

    @Test
    void getPointerReturnsOriginalString() {
        JsonPointer p = new JsonPointer("/a/b/c");
        assertEquals("/a/b/c", p.getPointer());
    }

    @Test
    void appendCreatesNewPointer() {
        JsonPointer base = new JsonPointer("/user");
        JsonPointer extended = base.append("name");
        Object result = extended.evaluate("{\"user\":{\"name\":\"Bob\"}}");
        assertEquals("Bob", result);
    }

    @Test
    void headReturnsParentPointer() {
        JsonPointer p = new JsonPointer("/a/b/c");
        JsonPointer parent = p.head();
        assertEquals("/a/b", parent.getPointer());
    }

    @Test
    void headOfRootIsEmpty() {
        JsonPointer p = new JsonPointer("/a");
        JsonPointer parent = p.head();
        assertEquals("", parent.getPointer());
    }

    @Test
    void toStringContainsPointer() {
        JsonPointer p = new JsonPointer("/foo/bar");
        assertTrue(p.toString().contains("/foo/bar"));
    }
}
