package com.njydsz.pmis.common.json;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.json.provider.ValueWriter;

/**
 * ValueWriter 值写入器测试（类型代码 / 字符串转义 / 小整数缓存 / Optional / UUID）。
 *
 * @since 1.4.0
 */
class ValueWriterTest {

    @Test
    void testWriteNull() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(null, sb);
        assertEquals("null", sb.toString());
    }

    @Test
    void testWriteString() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeString("hello", sb);
        assertEquals("\"hello\"", sb.toString());
    }

    @Test
    void testWriteEmptyString() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeString("", sb);
        assertEquals("\"\"", sb.toString());
    }

    @Test
    void testWriteStringWithQuotes() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeString("say \"hello\"", sb);
        assertEquals("\"say \\\"hello\\\"\"", sb.toString());
    }

    @Test
    void testWriteStringWithBackslash() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeString("path\\to\\file", sb);
        assertEquals("\"path\\\\to\\\\file\"", sb.toString());
    }

    @Test
    void testWriteStringWithNewline() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeString("line1\nline2", sb);
        assertEquals("\"line1\\nline2\"", sb.toString());
    }

    @Test
    void testWriteStringWithTab() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeString("a\tb", sb);
        assertEquals("\"a\\tb\"", sb.toString());
    }

    @Test
    void testWriteStringWithControlChar() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeString("a\u0001b", sb);
        assertEquals("\"a\\u0001b\"", sb.toString());
    }

    @Test
    void testWriteIntSmall() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeInt(42, sb);
        assertEquals("42", sb.toString());
    }

    @Test
    void testWriteIntLarge() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeInt(999999, sb);
        assertEquals("999999", sb.toString());
    }

    @Test
    void testWriteLongSmall() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeLong(100L, sb);
        assertEquals("100", sb.toString());
    }

    @Test
    void testWriteLongLarge() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeLong(9999999999L, sb);
        assertEquals("9999999999", sb.toString());
    }

    @Test
    void testWriteDoubleNormal() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeDouble(3.14, sb);
        assertEquals("3.14", sb.toString());
    }

    @Test
    void testWriteDoubleNaN() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeDouble(Double.NaN, sb);
        assertEquals("null", sb.toString());
    }

    @Test
    void testWriteDoubleInfinite() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeDouble(Double.POSITIVE_INFINITY, sb);
        assertEquals("null", sb.toString());
    }

    @Test
    void testWriteBoolean() {
        StringBuilder sb1 = new StringBuilder();
        ValueWriter.writeBoolean(true, sb1);
        assertEquals("true", sb1.toString());

        StringBuilder sb2 = new StringBuilder();
        ValueWriter.writeBoolean(false, sb2);
        assertEquals("false", sb2.toString());
    }

    @Test
    void testWriteChar() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeChar('A', sb);
        assertEquals("\"A\"", sb.toString());
    }

    @Test
    void testWriteBigDecimal() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(new BigDecimal("123.456"), sb);
        assertEquals("123.456", sb.toString());
    }

    @Test
    void testWriteBigInteger() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(new BigInteger("999999999999"), sb);
        assertEquals("999999999999", sb.toString());
    }

    @Test
    void testWriteUuid() {
        StringBuilder sb = new StringBuilder();
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        ValueWriter.writeValue(uuid, sb);
        assertEquals("\"550e8400-e29b-41d4-a716-446655440000\"", sb.toString());
    }

    @Test
    void testWriteOptionalPresent() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(java.util.Optional.of("hello"), sb);
        assertEquals("\"hello\"", sb.toString());
    }

    @Test
    void testWriteOptionalEmpty() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(java.util.Optional.empty(), sb);
        assertEquals("null", sb.toString());
    }

    @Test
    void testWriteOptionalInt() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(java.util.Optional.of(42), sb);
        assertEquals("42", sb.toString());
    }

    @Test
    void testWriteList() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(List.of(1, 2, 3), sb);
        String json = sb.toString();
        assertNotNull(json);
        assertTrue(json.startsWith("["));
        assertTrue(json.endsWith("]"));
        assertTrue(json.contains("1"));
        assertTrue(json.contains("2"));
        assertTrue(json.contains("3"));
    }

    @Test
    void testWriteMap() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(Map.of("key", "value"), sb);
        String json = sb.toString();
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"key\":\"value\""));
    }

    @Test
    void testWriteArray() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeValue(new int[]{1, 2, 3}, sb);
        String json = sb.toString();
        assertNotNull(json);
        assertTrue(json.contains("1"));
        assertTrue(json.contains("2"));
        assertTrue(json.contains("3"));
    }

    @Test
    void testWriteHtmlSafeString() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeHtmlSafeString("<script>alert('xss')</script>", sb);
        String json = sb.toString();
        // HTML 标签应被转义
        assertTrue(json.contains("\\u003c"));
        assertTrue(json.contains("\\u003e"));
    }

    @Test
    void testWriteStringInlineNoEscape() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeStringInline("plain text", sb);
        assertEquals("\"plain text\"", sb.toString());
    }

    @Test
    void testWriteStringInlineWithEscape() {
        StringBuilder sb = new StringBuilder();
        ValueWriter.writeStringInline("a\"b", sb);
        assertEquals("\"a\\\"b\"", sb.toString());
    }
}
