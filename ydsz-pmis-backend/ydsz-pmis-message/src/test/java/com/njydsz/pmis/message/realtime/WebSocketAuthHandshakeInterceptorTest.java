package com.njydsz.pmis.message.realtime;

import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.message.constant.MessageConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0-4: WebSocketAuthHandshakeInterceptor 单元测试。
 *
 * <p>验证 JWT token 提取（查询参数 / Authorization 头）、校验、属性写入、拒绝逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
class WebSocketAuthHandshakeInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private ServerHttpResponse response;
    private WebSocketAuthHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        response = mock(ServerHttpResponse.class);
        interceptor = new WebSocketAuthHandshakeInterceptor(jwtTokenProvider);
    }

    @Test
    void beforeHandshake_acceptsTokenFromQueryParam() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setParameter(MessageConstants.WS_TOKEN_PARAM, "valid-token");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("valid-token")).thenReturn("user-001");
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("张三");

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertTrue(result);
        assertEquals("user-001", attributes.get(MessageConstants.WS_ATTR_USER_ID));
        assertEquals("张三", attributes.get(MessageConstants.WS_ATTR_USERNAME));
    }

    @Test
    void beforeHandshake_acceptsTokenFromAuthorizationHeader() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.addHeader(MessageConstants.WS_TOKEN_HEADER, "Bearer header-token");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();
        when(jwtTokenProvider.validateToken("header-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("header-token")).thenReturn("user-002");
        when(jwtTokenProvider.getUsername("header-token")).thenReturn("李四");

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertTrue(result);
        assertEquals("user-002", attributes.get(MessageConstants.WS_ATTR_USER_ID));
    }

    @Test
    void beforeHandshake_acceptsRawAuthorizationHeader() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.addHeader(MessageConstants.WS_TOKEN_HEADER, "raw-token-no-bearer");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();
        when(jwtTokenProvider.validateToken("raw-token-no-bearer")).thenReturn(true);
        when(jwtTokenProvider.getUserId("raw-token-no-bearer")).thenReturn("u3");
        when(jwtTokenProvider.getUsername("raw-token-no-bearer")).thenReturn("u3name");

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertTrue(result);
        assertEquals("u3", attributes.get(MessageConstants.WS_ATTR_USER_ID));
    }

    @Test
    void beforeHandshake_rejectsWhenTokenMissing() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertFalse(result);
    }

    @Test
    void beforeHandshake_rejectsWhenTokenInvalid() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setParameter(MessageConstants.WS_TOKEN_PARAM, "invalid-token");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();
        when(jwtTokenProvider.validateToken("invalid-token")).thenReturn(false);

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertFalse(result);
    }

    @Test
    void beforeHandshake_rejectsWhenParseFails() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setParameter(MessageConstants.WS_TOKEN_PARAM, "parse-error-token");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();
        when(jwtTokenProvider.validateToken("parse-error-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("parse-error-token"))
                .thenThrow(new RuntimeException("parse error"));

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertFalse(result);
    }

    @Test
    void beforeHandshake_prefersQueryParamOverHeader() {
        // 同时有 query param 和 header，应优先 query param
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setParameter(MessageConstants.WS_TOKEN_PARAM, "param-token");
        servletReq.addHeader(MessageConstants.WS_TOKEN_HEADER, "Bearer header-token");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();
        when(jwtTokenProvider.validateToken("param-token")).thenReturn(true);
        when(jwtTokenProvider.getUserId("param-token")).thenReturn("u-priority");
        when(jwtTokenProvider.getUsername("param-token")).thenReturn("name");

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertTrue(result);
        assertEquals("u-priority", attributes.get(MessageConstants.WS_ATTR_USER_ID));
    }

    @Test
    void beforeHandshake_handlesBlankToken() {
        MockHttpServletRequest servletReq = new MockHttpServletRequest();
        servletReq.setParameter(MessageConstants.WS_TOKEN_PARAM, "   ");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletReq);
        Map<String, Object> attributes = new HashMap<>();

        boolean result = interceptor.beforeHandshake(request, response, null, attributes);

        assertFalse(result);
    }
}
