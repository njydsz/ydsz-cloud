package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.token.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthGlobalFilter 单元测试
 *
 * <p>覆盖：路径穿越拦截、OPTIONS 预检放行、白名单放行、无 Token 拦截、
 * Token 校验失败、Token 黑名单、非 access 类型 Token、正常 access Token 透传。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthGlobalFilter 认证全局过滤器测试")
class AuthGlobalFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @InjectMocks
    private AuthGlobalFilter filter;

    @Mock
    private GatewayFilterChain chain;

    private static final String INTERNAL_SIGN_SECRET = "test-secret-key-for-unit-test-1234567890";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "internalSignSecret", INTERNAL_SIGN_SECRET);
    }

    @Test
    @DisplayName("正常场景：OPTIONS 预检请求直接放行并注入安全头")
    void optionsPreflightShouldPassThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/api/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertNotNull(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"));
    }

    @Test
    @DisplayName("正常场景：白名单路径 /auth/login 放行")
    void whitelistPathShouldPassThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("正常场景：白名单路径 /health 放行")
    void healthPathShouldPassThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("异常场景：路径穿越攻击 .. 被拦截返回 400")
    void pathTraversalShouldBeRejectedWith400() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/auth/login/../users/list").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("异常场景：无 Authorization 头返回 401")
    void noAuthorizationHeaderShouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("异常场景：Authorization 非 Bearer 前缀返回 401")
    void nonBearerTokenShouldReturn401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users")
                .header("Authorization", "Basic abc123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("异常场景：Token 校验失败返回 401")
    void invalidTokenShouldReturn401() {
        String jwt = "invalid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users")
                .header("Authorization", "Bearer " + jwt).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(jwtTokenProvider.validateToken(jwt)).thenReturn(false);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("异常场景：Token 在黑名单中返回 401")
    void blacklistedTokenShouldReturn401() {
        String jwt = "valid.but.blacklisted.jwt";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users")
                .header("Authorization", "Bearer " + jwt).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(jwtTokenProvider.validateToken(jwt)).thenReturn(true);
        when(redisTemplate.hasKey("pmis:token:blacklist:" + jwt)).thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("异常场景：Token 类型为 refresh 而非 access 返回 401")
    void refreshTokenShouldReturn401() {
        String jwt = "refresh.type.jwt";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users")
                .header("Authorization", "Bearer " + jwt).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(jwtTokenProvider.validateToken(jwt)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(jwtTokenProvider.parseClaims(jwt)).thenReturn(claims);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("正常场景：有效 access Token 放行并注入用户信息头")
    void validAccessTokenShouldPassThroughWithUserHeaders() {
        String jwt = "valid.access.jwt";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users")
                .header("Authorization", "Bearer " + jwt).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(jwtTokenProvider.validateToken(jwt)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("1001");
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.get("username", String.class)).thenReturn("testuser");
        when(claims.get("roles")).thenReturn(List.of("ADMIN", "USER"));
        when(claims.get("permissions")).thenReturn(List.of("user:read", "user:write"));
        when(jwtTokenProvider.parseClaims(jwt)).thenReturn(claims);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertNotNull(exchange.getResponse().getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID));
    }

    @Test
    @DisplayName("正常场景：响应头注入安全防护头")
    void securityHeadersShouldBeInjected() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/health").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertEquals("nosniff", exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals("DENY", exchange.getResponse().getHeaders().getFirst("X-Frame-Options"));
        assertEquals("1; mode=block", exchange.getResponse().getHeaders().getFirst("X-XSS-Protection"));
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"));
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Permissions-Policy"));
    }

    @Test
    @DisplayName("正常场景：过滤器优先级为 HIGHEST_PRECEDENCE + 10")
    void orderShouldBeHighestPrecedencePlus10() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 10, filter.getOrder());
    }
}
