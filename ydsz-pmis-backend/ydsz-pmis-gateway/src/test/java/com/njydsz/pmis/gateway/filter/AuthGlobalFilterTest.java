package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.common.token.JwtTokenProvider;
import com.njydsz.pmis.gateway.config.CachedJwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link AuthGlobalFilter} 单元测试（P0-5）
 *
 * <p>覆盖核心认证逻辑：路径穿越防护、白名单放行、Token 校验、黑名单检查、内部头注入。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthGlobalFilter 认证过滤器测试")
class AuthGlobalFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CachedJwtValidator cachedJwtValidator;
    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private AuthGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthGlobalFilter(jwtTokenProvider, cachedJwtValidator, redisTemplate);
        ReflectionTestUtils.setField(filter, "internalSignSecret", "test-secret-key-at-least-32-bytes-long");
        ReflectionTestUtils.setField(filter, "cspUnsafeEval", false);
    }

    @Test
    @DisplayName("路径穿越攻击应返回 400")
    void shouldRejectPathTraversal() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/login/../users/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.BAD_REQUEST;
    }

    @Test
    @DisplayName("白名单路径应直接放行")
    void shouldAllowWhitelistPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("OPTIONS 预检请求应直接放行")
    void shouldAllowOptionsRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .options("/users/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("缺少 Authorization 头应返回 401")
    void shouldReturn401WhenNoAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
    }

    @Test
    @DisplayName("无效 Token 应返回 401")
    void shouldReturn401WhenTokenInvalid() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .header("Authorization", "Bearer invalid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(cachedJwtValidator.validateAndParse("invalid-token")).thenReturn(null);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
    }

    @Test
    @DisplayName("有效 Token 且未在黑名单中应放行并注入用户头")
    void shouldAllowValidTokenAndInjectHeaders() {
        DefaultClaims claims = new DefaultClaims(Map.of(
                "sub", "user123",
                "username", "testuser",
                "type", "access",
                "roles", List.of("admin"),
                "permissions", List.of("user:read")
        ));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .header("Authorization", "Bearer valid-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(cachedJwtValidator.validateAndParse("valid-token")).thenReturn(claims);
        when(redisTemplate.hasKey("pmis:token:blacklist:valid-token"))
                .thenReturn(Mono.just(false));

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();

        // 验证下游请求头中注入了用户信息
        HttpHeaders headers = exchange.getRequest().getHeaders();
        assert "user123".equals(headers.getFirst("X-User-Id"));
        assert "testuser".equals(headers.getFirst("X-Username"));
    }

    @Test
    @DisplayName("Token 在黑名单中应返回 401")
    void shouldReturn401WhenTokenBlacklisted() {
        DefaultClaims claims = new DefaultClaims(Map.of(
                "sub", "user123",
                "username", "testuser",
                "type", "access"
        ));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .header("Authorization", "Bearer blacklisted-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(cachedJwtValidator.validateAndParse("blacklisted-token")).thenReturn(claims);
        when(redisTemplate.hasKey("pmis:token:blacklist:blacklisted-token"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
    }

    @Test
    @DisplayName("refresh Token 类型应返回 401")
    void shouldReturn401ForRefreshTokenType() {
        DefaultClaims claims = new DefaultClaims(Map.of(
                "sub", "user123",
                "username", "testuser",
                "type", "refresh"
        ));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .header("Authorization", "Bearer refresh-token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(cachedJwtValidator.validateAndParse("refresh-token")).thenReturn(claims);
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.UNAUTHORIZED;
    }

    @Test
    @DisplayName("过滤器顺序应为 HIGHEST_PRECEDENCE + 10")
    void shouldHaveCorrectOrder() {
        assert filter.getOrder() == org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
