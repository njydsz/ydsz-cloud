package com.njydsz.pmis.common.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XssFilter 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class XssFilterTest {

    private final XssFilter filter = new XssFilter();

    @Test
    void shouldPassNormalValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name", "张三");
        request.addParameter("email", "test@example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // 验证正常值未被修改（通过 wrapper 后正常参数应该保持不变）
        String name = request.getParameter("name");
        assertEquals("张三", name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "javascript:alert(1)",
            "vbscript:msgbox(1)",
            "<div onclick=alert(1)>click</div>",
            "<svg onload=alert(1)>",
    })
    void shouldStripXssPayloads(String malicious) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("input", malicious);

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String result = request.getParameter("input");
        // 过滤后不应包含未转义的 <script> 标签
        assertFalse(result.contains("<script>"), "应移除 <script> 标签: " + result);
        // 过滤后不应包含 javascript: 协议
        assertFalse(result.toLowerCase().contains("javascript:"), "应移除 javascript: 协议: " + result);
        // 过滤后不应包含 on* 事件
        assertFalse(result.toLowerCase().contains("onerror="), "应移除 onerror 事件: " + result);
        assertFalse(result.toLowerCase().contains("onclick="), "应移除 onclick 事件: " + result);
        assertFalse(result.toLowerCase().contains("onload="), "应移除 onload 事件: " + result);
    }

    @Test
    void shouldHandleNullValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 不添加参数

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilterInternal(request, response, chain));
    }

    @Test
    void shouldHandleEmptyValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("empty", "");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals("", request.getParameter("empty"));
    }

    @Test
    void shouldHtmlEscapeSpecialChars() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("text", "<div class=\"test\">Hello & World</div>");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        String result = request.getParameter("text");
        // HTML 特殊字符应被转义
        assertTrue(result.contains("&lt;") || !result.contains("<"), "尖括号应被转义");
        assertTrue(result.contains("&amp;") || !result.contains("&"), "& 应被转义");
    }
}