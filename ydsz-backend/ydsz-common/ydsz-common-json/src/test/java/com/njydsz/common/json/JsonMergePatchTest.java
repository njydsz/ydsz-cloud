package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.merge.JsonMergePatch;

/**
 * JSON Merge Patch (RFC 7396) 测试。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
class JsonMergePatchTest {

    @Test
    void testSimpleMerge() {
        String result = JsonMergePatch.merge(
                "{\"a\":1,\"b\":2}",
                "{\"b\":3,\"c\":4}");
        assertNotNull(result);
        assertTrue(result.contains("\"a\":1"));
        assertTrue(result.contains("\"b\":3"));
        assertTrue(result.contains("\"c\":4"));
    }

    @Test
    void testNullDeletesField() {
        String result = JsonMergePatch.merge(
                "{\"a\":1,\"b\":2}",
                "{\"b\":null}");
        assertNotNull(result);
        assertTrue(result.contains("\"a\":1"));
        assertFalse(result.contains("\"b\""));
    }

    @Test
    void testNestedMerge() {
        String result = JsonMergePatch.merge(
                "{\"user\":{\"name\":\"Alice\",\"age\":30}}",
                "{\"user\":{\"age\":31}}");
        assertNotNull(result);
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertTrue(result.contains("\"age\":31"));
    }

    @Test
    void testReplaceNonObject() {
        String result = JsonMergePatch.merge(
                "{\"value\":1}",
                "{\"value\":\"string\"}");
        assertNotNull(result);
        assertTrue(result.contains("\"value\":\"string\""));
    }

    @Test
    void testDiff() {
        String result = JsonMergePatch.diff(
                "{\"a\":1,\"b\":2,\"c\":3}",
                "{\"a\":1,\"b\":5,\"d\":4}");
        assertNotNull(result);
        // a 不变，不应出现在 diff 中
        assertFalse(result.contains("\"a\""));
        // b 变了
        assertTrue(result.contains("\"b\":5"));
        // c 删除了
        assertTrue(result.contains("\"c\":null"));
        // d 新增了
        assertTrue(result.contains("\"d\":4"));
    }

    @Test
    void testMergeEmptyTarget() {
        String result = JsonMergePatch.merge("", "{\"a\":1}");
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void testMergeEmptyPatch() {
        String result = JsonMergePatch.merge("{\"a\":1}", "");
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void testViaJsonFacade() {
        String result = YdszJson.merge(
                "{\"a\":1,\"b\":2}",
                "{\"b\":3,\"c\":4}");
        assertNotNull(result);
        assertTrue(result.contains("\"a\":1"));
        assertTrue(result.contains("\"b\":3"));
        assertTrue(result.contains("\"c\":4"));
    }
}
