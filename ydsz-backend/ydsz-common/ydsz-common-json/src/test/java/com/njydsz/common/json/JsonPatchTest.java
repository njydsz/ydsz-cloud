package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.patch.JsonPatch;

/**
 * JSON Patch (RFC 6902) 测试。
 *
 * @since 1.0.0
 */
class JsonPatchTest {

    @Test
    void testAddOperation() {
        String result = JsonPatch.apply(
                "[{\"op\":\"add\",\"path\":\"/email\",\"value\":\"test@test.com\"}]",
                "{\"name\":\"Alice\"}");
        assertNotNull(result);
        assertTrue(result.contains("\"email\":\"test@test.com\""));
        assertTrue(result.contains("\"name\":\"Alice\""));
    }

    @Test
    void testRemoveOperation() {
        String result = JsonPatch.apply(
                "[{\"op\":\"remove\",\"path\":\"/temp\"}]",
                "{\"name\":\"Alice\",\"temp\":true}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertFalse(result.contains("\"temp\""));
    }

    @Test
    void testReplaceOperation() {
        String result = JsonPatch.apply(
                "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Bob\"}]",
                "{\"name\":\"Alice\",\"age\":30}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Bob\""));
        assertFalse(result.contains("\"Alice\""));
        assertTrue(result.contains("\"age\":30"));
    }

    @Test
    void testMoveOperation() {
        String result = JsonPatch.apply(
                "[{\"op\":\"move\",\"from\":\"/oldName\",\"path\":\"/newName\"}]",
                "{\"oldName\":\"Alice\"}");
        assertNotNull(result);
        assertTrue(result.contains("\"newName\":\"Alice\""));
        assertFalse(result.contains("\"oldName\""));
    }

    @Test
    void testCopyOperation() {
        String result = JsonPatch.apply(
                "[{\"op\":\"copy\",\"from\":\"/name\",\"path\":\"/alias\"}]",
                "{\"name\":\"Alice\"}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertTrue(result.contains("\"alias\":\"Alice\""));
    }

    @Test
    void testTestOperationSuccess() {
        String result = JsonPatch.apply(
                "[{\"op\":\"test\",\"path\":\"/name\",\"value\":\"Alice\"}]",
                "{\"name\":\"Alice\"}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Alice\""));
    }

    @Test
    void testTestOperationFailure() {
        assertThrows(IllegalStateException.class, () -> JsonPatch.apply(
                "[{\"op\":\"test\",\"path\":\"/name\",\"value\":\"Bob\"}]",
                "{\"name\":\"Alice\"}"));
    }

    @Test
    void testMultipleOperations() {
        String result = JsonPatch.apply(
                "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Bob\"},"
                + "{\"op\":\"add\",\"path\":\"/email\",\"value\":\"bob@test.com\"},"
                + "{\"op\":\"remove\",\"path\":\"/temp\"}]",
                "{\"name\":\"Alice\",\"temp\":true}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Bob\""));
        assertTrue(result.contains("\"email\":\"bob@test.com\""));
        assertFalse(result.contains("\"temp\""));
    }

    @Test
    void testBuilderPattern() {
        String result = JsonPatch.builder()
                .replace("/name", "Charlie")
                .add("/age", 25)
                .applyTo("{\"name\":\"Alice\"}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Charlie\""));
        assertTrue(result.contains("\"age\":25"));
    }

    @Test
    void testBuilderWithTest() {
        String result = JsonPatch.builder()
                .test("/name", "Alice")
                .replace("/name", "Bob")
                .applyTo("{\"name\":\"Alice\"}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Bob\""));
    }

    @Test
    void testUnknownOperation() {
        assertThrows(IllegalArgumentException.class, () -> JsonPatch.apply(
                "[{\"op\":\"unknown\",\"path\":\"/name\"}]",
                "{\"name\":\"Alice\"}"));
    }
}
