package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.gateway.config.IpWhitelistProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * IpWhitelistFilter 单元测试（P2-8 安全加固）
 *
 * <p>覆盖范围:
 * <ul>
 *   <li>开关关闭时放行</li>
 *   <li>白名单为空时放行</li>
 *   <li>跳过路径放行</li>
 *   <li>白名单 IP 放行</li>
 *   <li>非白名单 IP 返回 403</li>
 *   <li>执行顺序先于 AuthGlobalFilter</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("IpWhitelistFilter IP 白名单过滤器测试")
class IpWhitelistFilterTest {

    private IpWhitelistProperties properties;
    private IpWhitelistFilter filter;

    @BeforeEach
    void setUp() {
        properties = new IpWhitelistProperties();
        filter = new IpWhitelistFilter(properties);
    }

    /**
     * 构造带 X-Forwarded-For 头的请求与交换上下文
     *
     * @param path 请求路径
     * @param ip   X-Forwarded-For 头值
     * @return 模拟的 ServerWebExchange
     */
    private ServerWebExchange buildExchange(String path, String ip) {
        MockServerHttpRequest.BodyBuilder requestBuilder = MockServerHttpRequest.post(path);
        if (ip != null) {
            requestBuilder.header("X-Forwarded-For", ip);
        }
        MockServerHttpRequest request = requestBuilder.build();
        return MockServerWebExchange.from(request);
    }

    /**
     * 构造返回 Mono.empty() 的过滤器链 mock
     *
     * @return mock 的 GatewayFilterChain
     */
    private GatewayFilterChain mockChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        return chain;
    }

    @Test
    @DisplayName("开关关闭时应直接放行")
    void filter_disabled_shouldPassThrough() {
        properties.setIpWhitelistEnabled(false);
        properties.setIpWhitelist("10.0.0.1");

        ServerWebExchange exchange = buildExchange("/users", "1.2.3.4");
        GatewayFilterChain chain = mockChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("白名单为空时应放行所有")
    void filter_emptyWhitelist_shouldPassThrough() {
        properties.setIpWhitelistEnabled(true);
        properties.setIpWhitelist("");

        ServerWebExchange exchange = buildExchange("/users", "1.2.3.4");
        GatewayFilterChain chain = mockChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("跳过路径应放行不校验 IP")
    void filter_skipPath_shouldPassThrough() {
        properties.setIpWhitelistEnabled(true);
        properties.setIpWhitelist("10.0.0.1");
        properties.setIpWhitelistSkipPaths(List.of("/health", "/auth/login"));

        ServerWebExchange exchange = buildExchange("/health", "1.2.3.4");
        GatewayFilterChain chain = mockChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("跳过路径前缀匹配应放行子路径")
    void filter_skipPathPrefix_shouldPassThrough() {
        properties.setIpWhitelistEnabled(true);
        properties.setIpWhitelist("10.0.0.1");
        properties.setIpWhitelistSkipPaths(List.of("/auth/"));

        ServerWebExchange exchange = buildExchange("/auth/login", "1.2.3.4");
        GatewayFilterChain chain = mockChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }

    @Test
    @DisplayName("白名单内 IP 应放行")
    void filter_whitelistedIp_shouldPassThrough() {
        properties.setIpWhitelistEnabled(true);
        properties.setIpWhitelist("192.168.1.0/24,10.0.0.1");

        ServerWebExchange exchange = buildExchange("/users", "192.168.1.100");
        GatewayFilterChain chain = mockChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
        assertNotEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("非白名单 IP 应返回 403 且不调用后续过滤器")
    void filter_nonWhitelistedIp_shouldReturn403() {
        properties.setIpWhitelistEnabled(true);
        properties.setIpWhitelist("192.168.1.0/24");

        ServerWebExchange exchange = buildExchange("/users", "8.8.8.8");
        GatewayFilterChain chain = mockChain();

        filter.filter(exchange, chain).block();

        verify(chain, never()).filter(any(ServerWebExchange.class));
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("执行顺序应先于 AuthGlobalFilter（order 值更小）")
    void getOrder_shouldBeBeforeAuthFilter() {
        int authFilterOrder = Ordered.HIGHEST_PRECEDENCE + 10;
        int ipFilterOrder = filter.getOrder();
        assertTrue(ipFilterOrder < authFilterOrder,
                "IpWhitelistFilter order(" + ipFilterOrder + ") 应小于 AuthGlobalFilter order(" + authFilterOrder + ")");
    }

    @Test
    @DisplayName("多 IP 头场景下白名单应正确命中")
    void filter_multipleIpsInXff_shouldResolveFirst() {
        properties.setIpWhitelistEnabled(true);
        properties.setIpWhitelist("1.2.3.4");

        // X-Forwarded-For: 1.2.3.4, 5.6.7.8 → 取第一个 IP 1.2.3.4
        ServerWebExchange exchange = buildExchange("/users", "1.2.3.4, 5.6.7.8");
        GatewayFilterChain chain = mockChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any(ServerWebExchange.class));
    }
}
