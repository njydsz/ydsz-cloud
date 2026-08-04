package com.njydsz.common.safe.xss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EscapeUtils} 单元测试
 *
 * @since 1.0.0
 * @author ydsz-team
 */
class EscapeUtilsTest {

    @Test
    @DisplayName("HTML 转义应正确处理特殊字符")
    void testEscapeHtml() {
        assertEquals("&lt;script&gt;", EscapeUtils.escape("<script>"));
        assertEquals("&quot;test&quot;", EscapeUtils.escape("\"test\""));
        assertEquals("&#39;test&#39;", EscapeUtils.escape("'test'"));
        assertEquals("a&amp;b", EscapeUtils.escape("a&b"));
    }

    @Test
    @DisplayName("空字符串和 null 应原样返回")
    void testEscapeEmpty() {
        assertEquals("", EscapeUtils.escape(""));
        assertNull(EscapeUtils.escape(null));
    }

    @Test
    @DisplayName("无特殊字符的字符串应原样返回")
    void testEscapeNoSpecial() {
        assertEquals("hello world", EscapeUtils.escape("hello world"));
    }

    @Test
    @DisplayName("HTML 反转义应正确还原")
    void testUnescapeHtml() {
        assertEquals("<script>", EscapeUtils.unescape("&lt;script&gt;"));
        assertEquals("\"test\"", EscapeUtils.unescape("&quot;test&quot;"));
        assertEquals("'test'", EscapeUtils.unescape("&#39;test&#39;"));
        assertEquals("a&b", EscapeUtils.unescape("a&amp;b"));
    }

    @Test
    @DisplayName("JavaScript 转义应正确处理特殊字符")
    void testEscapeJavaScript() {
        assertEquals("\\\"test\\\"", EscapeUtils.escapeJavaScript("\"test\""));
        assertEquals("\\x3Cscript\\x3E", EscapeUtils.escapeJavaScript("<script>"));
        assertEquals("test\\nline", EscapeUtils.escapeJavaScript("test\nline"));
    }

    @Test
    @DisplayName("XML 转义应正确处理特殊字符")
    void testEscapeXml() {
        assertEquals("&lt;tag&gt;", EscapeUtils.escapeXML("<tag>"));
        assertEquals("a&amp;b", EscapeUtils.escapeXML("a&b"));
        assertEquals("&quot;test&quot;", EscapeUtils.escapeXML("\"test\""));
        assertEquals("&apos;test&apos;", EscapeUtils.escapeXML("'test'"));
    }

    @Test
    @DisplayName("XML 反转义应正确还原")
    void testUnescapeXml() {
        assertEquals("<tag>", EscapeUtils.unescapeXML("&lt;tag&gt;"));
        assertEquals("a&b", EscapeUtils.unescapeXML("a&amp;b"));
    }

    @Test
    @DisplayName("XSS 检测应正确识别攻击向量")
    void testContainsXSS() {
        assertTrue(EscapeUtils.containsXSS("<script>alert(1)</script>"));
        assertTrue(EscapeUtils.containsXSS("javascript:alert(1)"));
        assertTrue(EscapeUtils.containsXSS("<iframe src='evil.com'>"));
        assertTrue(EscapeUtils.containsXSS("onerror=alert(1)"));
        assertFalse(EscapeUtils.containsXSS("normal text"));
        assertFalse(EscapeUtils.containsXSS("hello world 123"));
    }

    @Test
    @DisplayName("clean 方法应移除危险标签")
    void testClean() {
        String cleaned = EscapeUtils.clean("<script>alert(1)</script>hello");
        assertNotNull(cleaned);
        assertFalse(cleaned.contains("<script>"));
        assertTrue(cleaned.contains("hello"));
    }

    @Test
    @DisplayName("Base64 编解码应正确工作")
    void testBase64() {
        String original = "Hello, World!";
        String encoded = EscapeUtils.encodeBase64(original);
        assertEquals(original, EscapeUtils.decodeBase64(encoded));
    }

    @Test
    @DisplayName("stripTags 应移除 HTML 标签")
    void testStripTags() {
        assertEquals("hello world", EscapeUtils.stripTags("<b>hello</b> <i>world</i>"));
        assertEquals("text", EscapeUtils.stripTags("<p>text</p>"));
    }

    @Test
    @DisplayName("JSON 值 XSS 清洗应保持 JSON 结构")
    void testCleanJsonValue() {
        String json = "{\"name\":\"<script>alert(1)</script>\"}";
        String cleaned = EscapeUtils.cleanJsonValue(json);
        assertNotNull(cleaned);
        assertFalse(cleaned.contains("<script>"));
        assertTrue(cleaned.contains("\"name\""));
    }
}
