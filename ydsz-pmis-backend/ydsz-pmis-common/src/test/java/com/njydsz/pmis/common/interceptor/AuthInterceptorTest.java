package com.njydsz.pmis.common.interceptor;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthInterceptor 鉴权拦截器单元测试
 *
 * <p>覆盖多种 Token 来源（Authorization / X-Access-Token / query）、非法 Token 拦截、
 * refresh token 拒绝与上下文清理。Token 解析委托给真实的 {@link JwtTokenProvider}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AuthInterceptor 鉴权拦截器测试")
class AuthInterceptorTest {

    private static final String SECRET = "pmis-default-jwt-secret-key-please-change-in-production-environment-must-be-256-bits";
    private JwtTokenProvider jwtTokenProvider;
    private AuthInterceptor interceptor;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        // 构造真实的 JwtTokenProvider 并初始化（构建缓存 parser），再注入拦截器
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "issuer", "pmis");
        ReflectionTestUtils.invokeMethod(jwtTokenProvider, "init");
        interceptor = new AuthInterceptor(jwtTokenProvider);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void cleanUp() {
        SecurityContext.clear();
    }

    @Test
    @DisplayName("缺少 Token 应抛 UNAUTHORIZED")
    void missingToken() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        when(req.getHeader("X-Access-Token")).thenReturn(null);
        when(req.getParameter("access_token")).thenReturn(null);

        assertThatThrownBy(() -> interceptor.preHandle(req, resp, new Object()))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    @DisplayName("Bearer Token 解析后应放入 SecurityContext")
    void bearerToken() {
        String token = buildAccessToken(100L, "zhangsan");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);

        boolean ok = interceptor.preHandle(req, resp, new Object());
        assertThat(ok).isTrue();
        assertThat(SecurityContext.getCurrentOrNull()).isNotNull();
        assertThat(SecurityContext.getUserId()).isEqualTo(100L);
        assertThat(SecurityContext.getUsername()).isEqualTo("zhangsan");
    }

    @Test
    @DisplayName("X-Access-Token 头应被识别")
    void accessTokenHeader() {
        String token = buildAccessToken(200L, "lisi");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        when(req.getHeader("X-Access-Token")).thenReturn(token);

        boolean ok = interceptor.preHandle(req, resp, new Object());
        assertThat(ok).isTrue();
        assertThat(SecurityContext.getUserId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("access_token 查询参数应被识别")
    void queryParam() {
        String token = buildAccessToken(300L, "wangwu");
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Authorization")).thenReturn(null);
        when(req.getHeader("X-Access-Token")).thenReturn(null);
        when(req.getParameter("access_token")).thenReturn(token);

        boolean ok = interceptor.preHandle(req, resp, new Object());
        assertThat(ok).isTrue();
        assertThat(SecurityContext.getUserId()).isEqualTo(300L);
    }

    @Test
    @DisplayName("非法 Token 应抛 TOKEN_INVALID")
    void invalidToken() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer not.a.jwt");

        assertThatThrownBy(() -> interceptor.preHandle(req, resp, new Object()))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("refresh token 应被拒绝（仅允许 access token 访问业务接口）")
    void refreshTokenRejected() {
        String token = Jwts.builder()
                .claims(Map.of("userId", 100L, "type", "refresh"))
                .subject(String.valueOf(100L))
                .issuer("pmis")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600 * 1000))
                .signWith(key)
                .compact();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token);

        assertThatThrownBy(() -> interceptor.preHandle(req, resp, new Object()))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("afterCompletion 应清空 SecurityContext")
    void afterCompletion() {
        SecurityContext.setCurrent(com.njydsz.pmis.common.security.LoginUser.builder().userId(1L).build());
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        interceptor.afterCompletion(req, resp, new Object(), null);
        assertThat(SecurityContext.getCurrentOrNull()).isNull();
    }

    private String buildAccessToken(Long userId, String username) {
        return Jwts.builder()
                .claims(Map.of("userId", userId, "username", username,
                        "roles", List.of("ADMIN"),
                        "permissions", List.of("user:list", "user:create"),
                        "type", "access"))
                .subject(String.valueOf(userId))
                .issuer("pmis")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600 * 1000))
                .signWith(key)
                .compact();
    }
}
