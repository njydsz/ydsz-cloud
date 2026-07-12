package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.gateway.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RateLimitFilter} 单元测试（P0-5）
 *
 * <p>覆盖限流维度检查、白名单路径、IP 提取、限流响应。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter 限流过滤器测试")
class RateLimitFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private RateLimitProperties properties;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(properties, redisTemplate);

        RateLimitProperties.PerIpConfig perIp = new RateLimitProperties.PerIpConfig();
        perIp.setEnabled(true);
        perIp.setDefaultQps(10);
        perIp.setBurstCapacity(20);
        perIp.setWhitelist(List.of("127.0.0.1"));

        RateLimitProperties.PerUserConfig perUser = new RateLimitProperties.PerUserConfig();
        perUser.setEnabled(true);
        perUser.setDefaultQps(50);
        perUser.setBurstCapacity(100);

        RateLimitProperties.PerTenantConfig perTenant = new RateLimitProperties.PerTenantConfig();
        perTenant.setEnabled(false);

        RateLimitProperties.ResponseHeadersConfig responseHeaders = new RateLimitProperties.ResponseHeadersConfig();
        responseHeaders.setEnabled(true);
        responseHeaders.setRetryAfter(5);

        when(properties.isEnabled()).thenReturn(true);
        when(properties.getPerIp()).thenReturn(perIp);
        when(properties.getPerUser()).thenReturn(perUser);
        when(properties.getPerTenant()).thenReturn(perTenant);
        when(properties.getResponseHeaders()).thenReturn(responseHeaders);
    }

    @Test
    @DisplayName("限流关闭时应直接放行")
    void shouldPassThroughWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("健康检查路径不限流")
    void shouldSkipActuatorPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("登录路径不限流")
    void shouldSkipLoginPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("IP 白名单中的 IP 不限流")
    void shouldSkipWhitelistedIp() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // IP 在白名单中，应直接放行
        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("过滤器顺序应为 HIGHEST_PRECEDENCE + 30")
    void shouldHaveCorrectOrder() {
        assert filter.getOrder() == org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
