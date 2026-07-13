package com.njydsz.pmis.common.json;

import com.njydsz.pmis.common.json.exception.YdszJsonException;
import com.njydsz.pmis.common.json.pointer.JsonPointer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YdszJson Pointer (RFC 6901) 测试")
class YdszJsonPointerTest {

    // ==================== 基础指针 ====================

    @Nested
    @DisplayName("基础指针测试")
    class BasicPointerTests {

        @Test
        @DisplayName("/foo 获取对象属性")
        void basicPointer() {
            String json = "{\"foo\":\"bar\"}";
            Object result = new JsonPointer("/foo").evaluate(json);
            assertEquals("bar", result);
        }

        @Test
        @DisplayName("/foo/bar 获取嵌套属性")
        void nestedPointer() {
            String json = "{\"foo\":{\"bar\":42}}";
            Object result = new JsonPointer("/foo/bar").evaluate(json);
            assertEquals(42L, ((Number) result).longValue());
        }

        @Test
        @DisplayName("多层嵌套指针")
        void deeplyNestedPointer() {
            String json = "{\"a\":{\"b\":{\"c\":\"deep\"}}}";
            Object result = new JsonPointer("/a/b/c").evaluate(json);
            assertEquals("deep", result);
        }
    }

    // ==================== 数组索引指针 ====================

    @Nested
    @DisplayName("数组索引指针测试")
    class ArrayIndexPointerTests {

        @Test
        @DisplayName("/foo/0 获取数组第一个元素")
        void arrayIndexPointer() {
            String json = "{\"foo\":[1,2,3]}";
            Object result = new JsonPointer("/foo/0").evaluate(json);
            assertEquals(1L, ((Number) result).longValue());
        }

        @Test
        @DisplayName("/foo/2 获取数组第三个元素")
        void arrayIndexPointerThird() {
            String json = "{\"foo\":[\"a\",\"b\",\"c\"]}";
            Object result = new JsonPointer("/foo/2").evaluate(json);
            assertEquals("c", result);
        }

        @Test
        @DisplayName("纯数组索引")
        void pureArrayIndex() {
            String json = "[10,20,30]";
            Object result = new JsonPointer("/1").evaluate(json);
            assertEquals(20L, ((Number) result).longValue());
        }

        @Test
        @DisplayName("数组越界抛出异常")
        void arrayIndexOutOfBounds() {
            String json = "[1,2,3]";
            assertThrows(YdszJsonException.class, () -> new JsonPointer("/5").evaluate(json));
        }

        @Test
        @DisplayName("负数索引抛出异常")
        void negativeArrayIndex() {
            String json = "[1,2,3]";
            assertThrows(YdszJsonException.class, () -> new JsonPointer("/-1").evaluate(json));
        }
    }

    // ==================== 转义字符 ====================

    @Nested
    @DisplayName("转义字符测试")
    class EscapedCharacterTests {

        @Test
        @DisplayName("~1 转义为 /")
        void tildeOneEscapesSlash() {
            String json = "{\"a/b\":42}";
            Object result = new JsonPointer("/a~1b").evaluate(json);
            assertEquals(42L, ((Number) result).longValue());
        }

        @Test
        @DisplayName("~0 转义为 ~")
        void tildeZeroEscapesTilde() {
            String json = "{\"a~b\":99}";
            Object result = new JsonPointer("/a~0b").evaluate(json);
            assertEquals(99L, ((Number) result).longValue());
        }
    }

    // ==================== 根指针 ====================

    @Nested
    @DisplayName("根指针测试")
    class RootPointerTests {

        @Test
        @DisplayName("空指针返回整个文档")
        void emptyPointerReturnsWholeDocument() {
            String json = "{\"foo\":\"bar\"}";
            Object result = new JsonPointer("").evaluate(json);
            assertNotNull(result);
        }

        @Test
        @DisplayName("getPointer 返回原始指针字符串")
        void getPointerReturnsOriginal() {
            JsonPointer pointer = new JsonPointer("/foo/bar");
            assertEquals("/foo/bar", pointer.getPointer());
        }
    }

    // ==================== 错误处理 ====================

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("null 指针抛出异常")
        void nullPointerThrows() {
            assertThrows(YdszJsonException.class, () -> new JsonPointer(null));
        }

        @Test
        @DisplayName("不以 / 开头的指针抛出异常")
        void pointerMustStartWithSlash() {
            assertThrows(YdszJsonException.class, () -> new JsonPointer("foo"));
        }

        @Test
        @DisplayName("不存在的路径抛出异常")
        void nonExistingPathThrows() {
            String json = "{\"foo\":\"bar\"}";
            assertThrows(YdszJsonException.class, () -> new JsonPointer("/baz").evaluate(json));
        }

        @Test
        @DisplayName("通过非对象/数组遍历抛出异常")
        void traverseThroughNonObjectArrayThrows() {
            String json = "{\"foo\":\"bar\"}";
            assertThrows(YdszJsonException.class, () -> new JsonPointer("/foo/baz").evaluate(json));
        }
    }

    // ==================== 通过 YdszJson.getByPointer ====================

    @Nested
    @DisplayName("YdszJson.getByPointer 测试")
    class GetByPointerTests {

        @Test
        @DisplayName("通过 YdszJson.getByPointer 获取值")
        void getByPointerFromYdszJson() {
            String json = "{\"foo\":{\"bar\":42}}";
            Object result = YdszJson.getByPointer(json, "/foo/bar");
            assertEquals(42L, ((Number) result).longValue());
        }

        @Test
        @DisplayName("通过 JsonPointer 对象获取值")
        void getByPointerObject() {
            String json = "{\"foo\":\"bar\"}";
            JsonPointer pointer = new JsonPointer("/foo");
            Object result = YdszJson.getByPointer(json, pointer);
            assertEquals("bar", result);
        }
    }

    // ==================== toString ====================

    @Test
    @DisplayName("toString 返回有意义的信息")
    void toStringReturnsMeaningfulInfo() {
        JsonPointer pointer = new JsonPointer("/foo/bar");
        String str = pointer.toString();
        assertTrue(str.contains("/foo/bar"));
    }
}
