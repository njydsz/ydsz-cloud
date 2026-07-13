package com.njydsz.pmis.common.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

@DisplayName("YdszJson Merge Patch (RFC 7396) 测试")
class YdszJsonMergePatchTest {

    // ==================== 基础合并 ====================

    @Nested
    @DisplayName("基础合并测试")
    class BasicMergeTests {

        @Test
        @DisplayName("合并两个简单对象")
        void mergeSimpleObjects() {
            String target = "{\"a\":1,\"b\":2}";
            String patch = "{\"b\":3,\"c\":4}";
            String result = YdszJson.merge(target, patch);
            assertTrue(result.contains("\"a\":1"));
            assertTrue(result.contains("\"b\":3"));
            assertTrue(result.contains("\"c\":4"));
            assertFalse(result.contains("\"b\":2"));
        }

        @Test
        @DisplayName("合并到空目标")
        void mergeToEmptyTarget() {
            String target = "{}";
            String patch = "{\"a\":1}";
            String result = YdszJson.merge(target, patch);
            assertTrue(result.contains("\"a\":1"));
        }

        @Test
        @DisplayName("空补丁不改变目标")
        void emptyPatchNoChange() {
            String target = "{\"a\":1}";
            String patch = "{}";
            String result = YdszJson.merge(target, patch);
            assertTrue(result.contains("\"a\":1"));
        }
    }

    // ==================== null 删除 ====================

    @Nested
    @DisplayName("null 删除测试")
    class NullDeletionTests {

        @Test
        @DisplayName("null 值删除字段")
        void nullDeletesField() {
            String target = "{\"a\":1,\"b\":2,\"c\":3}";
            String patch = "{\"b\":null}";
            String result = YdszJson.merge(target, patch);
            assertTrue(result.contains("\"a\":1"));
            assertFalse(result.contains("\"b\""));
            assertTrue(result.contains("\"c\":3"));
        }

        @Test
        @DisplayName("多个 null 值删除多个字段")
        void multipleNullDeletes() {
            String target = "{\"a\":1,\"b\":2,\"c\":3}";
            String patch = "{\"a\":null,\"c\":null}";
            String result = YdszJson.merge(target, patch);
            assertFalse(result.contains("\"a\""));
            assertTrue(result.contains("\"b\":2"));
            assertFalse(result.contains("\"c\""));
        }
    }

    // ==================== 嵌套合并 ====================

    @Nested
    @DisplayName("嵌套合并测试")
    class NestedMergeTests {

        @Test
        @DisplayName("递归合并嵌套对象")
        void recursiveNestedMerge() {
            String target = "{\"user\":{\"name\":\"John\",\"age\":30}}";
            String patch = "{\"user\":{\"age\":31}}";
            String result = YdszJson.merge(target, patch);
            assertTrue(result.contains("\"name\":\"John\""));
            assertTrue(result.contains("\"age\":31"));
            assertFalse(result.contains("\"age\":30"));
        }

        @Test
        @DisplayName("嵌套对象中 null 删除字段")
        void nestedNullDelete() {
            String target = "{\"user\":{\"name\":\"John\",\"email\":\"john@test.com\"}}";
            String patch = "{\"user\":{\"email\":null}}";
            String result = YdszJson.merge(target, patch);
            assertTrue(result.contains("\"name\":\"John\""));
            assertFalse(result.contains("email"));
        }

        @Test
        @DisplayName("添加新的嵌套字段")
        void addNestedField() {
            String target = "{\"user\":{\"name\":\"John\"}}";
            String patch = "{\"user\":{\"age\":30}}";
            String result = YdszJson.merge(target, patch);
            assertTrue(result.contains("\"name\":\"John\""));
            assertTrue(result.contains("\"age\":30"));
        }
    }

    // ==================== diff ====================

    @Nested
    @DisplayName("diff 差异计算测试")
    class DiffTests {

        @Test
        @DisplayName("计算简单差异")
        void simpleDiff() {
            String source = "{\"a\":1,\"b\":2}";
            String target = "{\"a\":1,\"b\":3,\"c\":4}";
            String diff = YdszJson.diff(source, target);
            assertTrue(diff.contains("\"b\":3"));
            assertTrue(diff.contains("\"c\":4"));
        }

        @Test
        @DisplayName("无差异返回空对象")
        void noDiffReturnsEmptyObject() {
            String source = "{\"a\":1}";
            String target = "{\"a\":1}";
            String diff = YdszJson.diff(source, target);
            assertEquals("{}", diff);
        }

        @Test
        @DisplayName("删除字段的差异")
        void diffWithDeletedField() {
            String source = "{\"a\":1,\"b\":2}";
            String target = "{\"a\":1}";
            String diff = YdszJson.diff(source, target);
            assertTrue(diff.contains("\"b\":null") || diff.contains("\"b\""));
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("null 目标返回补丁")
        void nullTargetReturnsPatch() {
            String result = YdszJson.merge(null, "{\"a\":1}");
            assertEquals("{\"a\":1}", result);
        }

        @Test
        @DisplayName("null 补丁返回目标")
        void nullPatchReturnsTarget() {
            String result = YdszJson.merge("{\"a\":1}", null);
            assertEquals("{\"a\":1}", result);
        }

        @Test
        @DisplayName("空字符串目标返回补丁")
        void emptyStringTargetReturnsPatch() {
            String result = YdszJson.merge("", "{\"a\":1}");
            assertEquals("{\"a\":1}", result);
        }

        @Test
        @DisplayName("空字符串补丁返回目标")
        void emptyStringPatchReturnsTarget() {
            String result = YdszJson.merge("{\"a\":1}", "");
            assertEquals("{\"a\":1}", result);
        }
    }
}
