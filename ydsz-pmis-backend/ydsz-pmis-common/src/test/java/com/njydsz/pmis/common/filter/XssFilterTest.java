package com.njydsz.pmis.common.filter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XssFilter 单元测试
 *
 * <p>XssFilter 通过 {@link jakarta.servlet.http.HttpServletRequestWrapper} 包装请求，
 * 测试时从 {@link MockFilterChain#getRequest()} 获取包装后的 HttpServletRequest。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class XssFilterTest {

    private final XssFilter filter = new XssFilter();

    /**
     * 执行过滤器并返回包装后的请求
     */
    private HttpServletRequest runFilter(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void shouldPassNormalValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("name", "张三");
        request.addParameter("email", "test@example.com");

        HttpServletRequest wrapped = runFilter(request);

        assertEquals("张三", wrapped.getParameter("name"));
        assertEquals("test@example.com", wrapped.getParameter("email"));
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

        HttpServletRequest wrapped = runFilter(request);

        String result = wrapped.getParameter("input");
        assertNotNull(result, "过滤后参数不应为 null");
        assertFalse(result.contains("<script>"), "应移除 <script> 标签: " + result);
        assertFalse(result.toLowerCase().contains("javascript:"), "应移除 javascript: 协议: " + result);
        assertFalse(result.toLowerCase().contains("onerror="), "应移除 onerror 事件: " + result);
        assertFalse(result.toLowerCase().contains("onclick="), "应移除 onclick 事件: " + result);
        assertFalse(result.toLowerCase().contains("onload="), "应移除 onload 事件: " + result);
    }

    @Test
    void shouldHandleNullValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertDoesNotThrow(() -> runFilter(request));
    }

    @Test
    void shouldHandleEmptyValues() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("empty", "");

        HttpServletRequest wrapped = runFilter(request);

        assertEquals("", wrapped.getParameter("empty"));
    }

    @Test
    void shouldHtmlEscapeSpecialChars() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("text", "<div class=\"test\">Hello & World</div>");

        HttpServletRequest wrapped = runFilter(request);

        String result = wrapped.getParameter("text");
        assertNotNull(result);
        assertTrue(result.contains("&lt;") || !result.contains("<"), "尖括号应被转义: " + result);
        assertTrue(result.contains("&amp;") || !result.contains("&"), "& 应被转义: " + result);
    }
}