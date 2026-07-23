package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.exception.YdszJsonException;
import com.njydsz.common.json.pointer.JsonPointer;

/**
 * JSON Pointer (RFC 6901) 测试。
 *
 * @since 1.4.0
 */
class JsonPointerTest {

    @Test
    void testRootPointer() {
        JsonPointer pointer = new JsonPointer("");
        Object result = pointer.evaluate("{\"a\":1}");
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void testSimpleProperty() {
        JsonPointer pointer = new JsonPointer("/foo");
        Object result = pointer.evaluate("{\"foo\":\"bar\"}");
        assertEquals("bar", result);
    }

    @Test
    void testNestedProperty() {
        JsonPointer pointer = new JsonPointer("/foo/bar");
        Object result = pointer.evaluate("{\"foo\":{\"bar\":42}}");
        assertEquals(42, result);
    }

    @Test
    void testArrayIndex() {
        JsonPointer pointer = new JsonPointer("/items/1");
        Object result = pointer.evaluate("{\"items\":[\"a\",\"b\",\"c\"]}");
        assertEquals("b", result);
    }

    @Test
    void testDeepNestedArray() {
        JsonPointer pointer = new JsonPointer("/a/b/0/c");
        Object result = pointer.evaluate("{\"a\":{\"b\":[{\"c\":\"found\"}]}}");
        assertEquals("found", result);
    }

    @Test
    void testEscapedSlash() {
        // ~1 表示 /
        JsonPointer pointer = new JsonPointer("/a~1b");
        Object result = pointer.evaluate("{\"a/b\":\"value\"}");
        assertEquals("value", result);
    }

    @Test
    void testEscapedTilde() {
        // ~0 表示 ~
        JsonPointer pointer = new JsonPointer("/a~0b");
        Object result = pointer.evaluate("{\"a~b\":\"value\"}");
        assertEquals("value", result);
    }

    @Test
    void testPathNotFound() {
        JsonPointer pointer = new JsonPointer("/missing");
        assertThrows(YdszJsonException.class, () -> pointer.evaluate("{\"a\":1}"));
    }

    @Test
    void testArrayOutOfBounds() {
        JsonPointer pointer = new JsonPointer("/items/10");
        assertThrows(YdszJsonException.class, () -> pointer.evaluate("{\"items\":[1,2,3]}"));
    }

    @Test
    void testInvalidPointerFormat() {
        assertThrows(YdszJsonException.class, () -> new JsonPointer("foo"));
        assertThrows(YdszJsonException.class, () -> new JsonPointer(null));
    }

    @Test
    void testGetPointer() {
        JsonPointer pointer = new JsonPointer("/foo/bar");
        assertEquals("/foo/bar", pointer.getPointer());
    }

    @Test
    void testToString() {
        JsonPointer pointer = new JsonPointer("/foo");
        String str = pointer.toString();
        assertNotNull(str);
        assertTrue(str.contains("/foo"));
    }

    @Test
    void testViaJsonFacade() {
        Object result = YdszJson.getByPointer("{\"user\":{\"name\":\"Alice\"}}", "/user/name");
        assertEquals("Alice", result);
    }
}
