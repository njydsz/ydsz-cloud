package com.njydsz.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.njydsz.common.json.parser.YdszJsonParser;

/**
 * YdszJsonParser 核心功能测试。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
class JsonParserTest {

    @Test
    void testParseSimpleObject() {
        Map<String, Object> result = YdszJsonParser.parseObject("{\"name\":\"John\",\"age\":30}");
        assertEquals("John", result.get("name"));
        assertEquals(30, result.get("age"));
    }

    @Test
    void testParseFieldNameWithEscape() {
        // 字段名包含转义字符：\"user\\\"name\" 应解析为 user"name
        Map<String, Object> result = YdszJsonParser.parseObject("{\"user\\\"name\":\"value\"}");
        assertNotNull(result);
        assertTrue(result.containsKey("user\"name"));
        assertEquals("value", result.get("user\"name"));
    }

    @Test
    void testParseFieldNameWithUnicodeEscape() {
        // 字段名包含 Unicode 转义：\"\\u0041\" 应解析为 "A"
        Map<String, Object> result = YdszJsonParser.parseObject("{\"\\u0041bc\":\"value\"}");
        assertNotNull(result);
        assertTrue(result.containsKey("Abc"));
        assertEquals("value", result.get("Abc"));
    }

    @Test
    void testParseFieldNameWithBackslash() {
        // 字段名包含反斜杠转义：\"path\\\\dir\" 应解析为 "path\dir"
        Map<String, Object> result = YdszJsonParser.parseObject("{\"path\\\\dir\":\"value\"}");
        assertNotNull(result);
        assertTrue(result.containsKey("path\\dir"));
    }

    @Test
    void testParseNumberPrecision() {
        // 大整数超过 double 精度范围（2^53），应回退到 Double.parseDouble
        Map<String, Object> result = YdszJsonParser.parseObject("{\"big\":9007199254740993}");
        assertNotNull(result);
        Object big = result.get("big");
        assertNotNull(big);
        // 9007199254740993 > 2^53，应作为 long 返回
        assertEquals(9007199254740993L, big);
    }

    @Test
    void testParseNumberWithManyDecimalDigits() {
        // 超过 22 位小数，应回退到 Double.parseDouble
        Map<String, Object> result = YdszJsonParser.parseObject(
                "{\"val\":3.141592653589793238462643383279}");
        assertNotNull(result);
        Object val = result.get("val");
        assertNotNull(val);
        assertTrue(val instanceof Double);
    }

    @Test
    void testParseBigDecimal() {
        YdszJsonParser.setUseBigDecimal(true);
        try {
            Map<String, Object> result = YdszJsonParser.parseObject("{\"price\":123.456}");
            assertNotNull(result);
            Object price = result.get("price");
            assertTrue(price instanceof BigDecimal);
            assertEquals(0, new BigDecimal("123.456").compareTo((BigDecimal) price));
        } finally {
            YdszJsonParser.setUseBigDecimal(false);
        }
    }

    @Test
    void testParseArray() {
        var result = YdszJsonParser.parseArray("[1,2,3,\"four\",true,null]");
        assertNotNull(result);
        assertEquals(6, result.size());
        assertEquals(1, result.get(0));
        assertEquals("four", result.get(3));
        assertEquals(true, result.get(4));
        assertNull(result.get(5));
    }

    @Test
    void testParseNestedObject() {
        Map<String, Object> result = YdszJsonParser.parseObject(
                "{\"outer\":{\"inner\":\"value\"},\"arr\":[1,2]}");
        assertNotNull(result);
        assertNotNull(result.get("outer"));
        assertNotNull(result.get("arr"));
    }

    @Test
    void testParseNull() {
        assertNull(YdszJsonParser.parseObject(null));
        assertNull(YdszJsonParser.parseObject(""));
    }

    @Test
    void testParseEmptyObject() {
        Map<String, Object> result = YdszJsonParser.parseObject("{}");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseEmptyArray() {
        var result = YdszJsonParser.parseArray("[]");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
