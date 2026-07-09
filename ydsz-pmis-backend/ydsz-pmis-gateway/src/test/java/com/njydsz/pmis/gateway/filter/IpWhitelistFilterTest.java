package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.gateway.config.IpWhitelistProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IpWhitelistFilter 单元测试
 *
 * <p>覆盖：开关关闭放行、白名单为空放行、跳过路径放行、
 * IP 命中白名单放行、IP 非白名单返回 403。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IpWhitelistFilter IP白名单过滤器测试")
class IpWhitelistFilterTest {

    @Mock
    private IpWhitelistProperties properties;

    @Mock
    private GatewayFilterChain chain;

    @InjectMocks
    private IpWhitelistFilter filter;

    @Test
    @DisplayName("正常场景：白名单开关关闭直接放行")
    void disabledWhitelistShouldPassThrough() {
        when(properties.isIpWhitelistEnabled()).thenReturn(false);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("正常场景：白名单为空放行所有请求")
    void emptyWhitelistShouldPassThrough() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("");
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("正常场景：跳过路径 /health 不校验 IP")
    void skipPathShouldPassThrough() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("192.168.1.0/24");
        when(properties.getIpWhitelistSkipPaths()).thenReturn(List.of("/health"));
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/health").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("正常场景：IP 命中白名单放行")
    void ipInWhitelistShouldPassThrough() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("192.168.1.0/24");
        when(properties.getIpWhitelistSkipPaths()).thenReturn(List.of());
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("X-Forwarded-For", "192.168.1.100").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("异常场景：IP 不在白名单返回 403")
    void ipNotInWhitelistShouldReturn403() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("192.168.1.0/24");
        when(properties.getIpWhitelistSkipPaths()).thenReturn(List.of());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("X-Forwarded-For", "10.0.0.1").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("正常场景：单 IP 精确匹配白名单放行")
    void singleIpMatchShouldPassThrough() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("10.0.0.1,10.0.0.2");
        when(properties.getIpWhitelistSkipPaths()).thenReturn(List.of());
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("X-Real-IP", "10.0.0.1").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("正常场景：逗号换行分隔的白名单正确解析")
    void multilineWhitelistShouldParse() {
        when(properties.isIpWhitelistEnabled()).thenReturn(true);
        when(properties.getIpWhitelist()).thenReturn("192.168.1.0/24\n10.0.0.1");
        when(properties.getIpWhitelistSkipPaths()).thenReturn(List.of());
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header("X-Forwarded-For", "10.0.0.1").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("正常场景：过滤器顺序在 AuthGlobalFilter 之前")
    void orderShouldBeBeforeAuthFilter() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 5, filter.getOrder());
    }
}
