package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.gateway.config.IpWhitelistProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * {@link IpWhitelistFilter} 单元测试（P0-5）
 *
 * <p>覆盖 IP 白名单开关、CIDR 匹配、跳过路径、拒绝非白名单 IP。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IpWhitelistFilter IP 白名单过滤器测试")
class IpWhitelistFilterTest {

    @Mock
    private IpWhitelistProperties properties;

    private IpWhitelistFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IpWhitelistFilter(properties);
    }

    @Test
    @DisplayName("白名单关闭时应直接放行")
    void shouldPassThroughWhenDisabled() {
        when(properties.isIpWhitelistEnabled()).thenReturn(false);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("白名单为空时应放行所有")
    void shouldAllowAllWhenWhitelistEmpty() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("");

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("跳过路径不校验 IP")
    void shouldSkipPath() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("10.0.0.1");
        when(properties.getIpWhitelistSkipPaths()).thenReturn(List.of("/auth/login"));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/login")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("非白名单 IP 应返回 403")
    void shouldRejectNonWhitelistedIp() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("10.0.0.1");
        when(properties.getIpWhitelistSkipPaths()).thenReturn(List.of());

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/users/list")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();

        assert exchange.getResponse().getStatusCode() == HttpStatus.FORBIDDEN;
    }

    @Test
    @DisplayName("过滤器顺序应为 HIGHEST_PRECEDENCE + 5")
    void shouldHaveCorrectOrder() {
        assert filter.getOrder() == (org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10) - 5;
    }
}
