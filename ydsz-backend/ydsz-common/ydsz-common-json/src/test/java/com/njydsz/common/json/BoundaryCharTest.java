package com.njydsz.common.json;

import java.math.BigDecimal;
import java.util.Map;

import com.njydsz.common.json.autotype.AutoTypeChecker;
import com.njydsz.common.json.parser.JsonParserUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 边界字符与特殊值测试（P0）。
 *
 * <p>覆盖 UTF-8 多字节字符、转义符、超大整数、BigDecimal 精度、
 * 空白字符、嵌套引号等容易导致解析错误的场景。
 */
class BoundaryCharTest {

    @BeforeEach
    void setUp() {
        AutoTypeChecker.setSafeMode(false);
    }

    @AfterEach
    void tearDown() {
        AutoTypeChecker.setSafeMode(true);
    }

    @Test
    void utf8MultibyteCharacters() {
        String json = "{\"name\":\"张三李四王五\",\"city\":\"北京\"}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals("张三李四王五", map.get("name"));
        assertEquals("北京", map.get("city"));
    }

    @Test
    void emojiAndSpecialUnicode() {
        String json = "{\"emoji\":\"🎉\",\"text\":\"\\u0048\\u0065\\u006c\\u006c\\u006f\"}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals("🎉", map.get("emoji"));
    }

    @Test
    void escapeCharacters() {
        String json = "{\"path\":\"C:\\\\Users\\\\test\",\"newline\":\"a\\nb\",\"tab\":\"a\\tb\"}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals("C:\\Users\\test", map.get("path"));
        assertEquals("a\nb", map.get("newline"));
        assertEquals("a\tb", map.get("tab"));
    }

    @Test
    void quotesInsideString() {
        String json = "{\"msg\":\"He said \\\"Hello\\\"\"}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals("He said \"Hello\"", map.get("msg"));
    }

    @Test
    void largeLongValue() {
        String json = "{\"big\":9223372036854775807,\"neg\":-9223372036854775808}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals(9223372036854775807L, ((Number) map.get("big")).longValue());
        assertEquals(-9223372036854775808L, ((Number) map.get("neg")).longValue());
    }

    @Test
    void bigDecimalPrecision() {
        try {
            JsonParserUtil.setUseBigDecimal(true);
            String json = "{\"price\":123456789.123456789012345}";
            Map<String, Object> map = YdszJson.parseMap(json);
            Object value = map.get("price");
            assertTrue(value instanceof BigDecimal, "expected BigDecimal but got " + value.getClass());
            assertEquals(new BigDecimal("123456789.123456789012345"), value);
        } finally {
            JsonParserUtil.setUseBigDecimal(false);
        }
    }

    @Test
    void emptyStringAndBlankValues() {
        String json = "{\"empty\":\"\",\"blank\":\"   \"}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals("", map.get("empty"));
        assertEquals("   ", map.get("blank"));
    }

    @Test
    void deeplyNestedStructure() {
        String json = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"deep\"}}}}}";
        Map<String, Object> map = YdszJson.parseMap(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) map.get("a");
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) a.get("b");
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) b.get("c");
        @SuppressWarnings("unchecked")
        Map<String, Object> d = (Map<String, Object>) c.get("d");
        assertEquals("deep", d.get("e"));
    }

    @Test
    void specialJsonCharactersInKey() {
        String json = "{\"key with space\":\"v1\",\"key\\\"quoted\":\"v2\"}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals("v1", map.get("key with space"));
    }

    @Test
    void nullAndBooleanLiterals() {
        assertEquals("null", YdszJson.toJson(null));
        assertTrue(YdszJson.toObject("true", Boolean.class));
        assertFalse(YdszJson.toObject("false", Boolean.class));
        assertNull(YdszJson.toObject("null", Object.class));
    }

    @Test
    void arrayWithMixedTypes() {
        String json = "[1,\"two\",true,null,3.14]";
        var list = YdszJson.parseArray(json);
        assertEquals(5, list.size());
        assertEquals(1, ((Number) list.get(0)).intValue());
        assertEquals("two", list.get(1));
        assertEquals(true, list.get(2));
        assertNull(list.get(3));
    }

    @Test
    void numberWithScientificNotation() {
        String json = "{\"sci\":1.23E5,\"neg\":-3.14e-2}";
        Map<String, Object> map = YdszJson.parseMap(json);
        assertEquals(123000.0, ((Number) map.get("sci")).doubleValue(), 0.01);
        assertEquals(-0.0314, ((Number) map.get("neg")).doubleValue(), 0.0001);
    }
}
