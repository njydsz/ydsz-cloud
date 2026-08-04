package com.njydsz.common.json;

import com.njydsz.common.json.merge.JsonMergePatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON Merge Patch（RFC 7396）单元测试（P1）。
 */
class JsonMergePatchTest {

    @Test
    void mergeAddsNewField() {
        String result = JsonMergePatch.merge(
            "{\"a\":1,\"b\":2}",
            "{\"c\":3}");
        // result: {"a":1,"b":2,"c":3}
        assertTrue(result.contains("\"a\":1"));
        assertTrue(result.contains("\"b\":2"));
        assertTrue(result.contains("\"c\":3"));
    }

    @Test
    void mergeOverwritesExistingField() {
        String result = JsonMergePatch.merge(
            "{\"a\":1,\"b\":2}",
            "{\"b\":3}");
        assertTrue(result.contains("\"a\":1"));
        assertTrue(result.contains("\"b\":3"));
    }

    @Test
    void mergeNullDeletesField() {
        String result = JsonMergePatch.merge(
            "{\"a\":1,\"b\":2}",
            "{\"b\":null}");
        assertTrue(result.contains("\"a\":1"));
        assertTrue(!result.contains("\"b\""));
    }

    @Test
    void mergeDeepRecursive() {
        String result = JsonMergePatch.merge(
            "{\"user\":{\"name\":\"Alice\",\"age\":30}}",
            "{\"user\":{\"age\":31}}");
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertTrue(result.contains("\"age\":31"));
    }

    @Test
    void mergeNullTargetReturnsPatch() {
        String result = JsonMergePatch.merge(null, "{\"a\":1}");
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void mergeEmptyTargetReturnsPatch() {
        String result = JsonMergePatch.merge("", "{\"a\":1}");
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void mergeNullPatchReturnsTarget() {
        String result = JsonMergePatch.merge("{\"a\":1}", null);
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void mergeEmptyPatchReturnsTarget() {
        String result = JsonMergePatch.merge("{\"a\":1}", "");
        assertEquals("{\"a\":1}", result);
    }

    @Test
    void mergeNonObjectPatchReplacesEntireTarget() {
        // RFC 7396: if patch is not an object, the result is patch
        String result = JsonMergePatch.merge("{\"a\":1}", "[1,2,3]");
        assertTrue(result.startsWith("["));
        assertTrue(result.endsWith("]"));
    }

    @Test
    void mergeNestedNullDeletesDeepField() {
        String result = JsonMergePatch.merge(
            "{\"user\":{\"name\":\"Alice\",\"email\":\"a@b.com\"}}",
            "{\"user\":{\"email\":null}}");
        assertTrue(result.contains("\"name\":\"Alice\""));
        assertTrue(!result.contains("email"));
    }
}
