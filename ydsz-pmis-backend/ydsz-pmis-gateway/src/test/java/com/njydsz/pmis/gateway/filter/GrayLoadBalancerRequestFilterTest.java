package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.gateway.loadbalancer.GrayLoadBalancer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GrayLoadBalancerRequestFilter 单元测试
 *
 * <p>覆盖：请求头灰度标识透传、查询参数灰度标识注入、路径模式灰度标识注入、
 * 无灰度标识放行、已有灰度头不重复注入。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrayLoadBalancerRequestFilter 灰度请求过滤器测试")
class GrayLoadBalancerRequestFilterTest {

    @Mock
    private GatewayFilterChain chain;

    @InjectMocks
    private GrayLoadBalancerRequestFilter filter;

    @Test
    @DisplayName("正常场景：X-Gray-Tag=gray 头透传并写入 attribute")
    void grayHeaderShouldSetAttribute() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header(GrayLoadBalancer.GRAY_TAG_HEADER, "gray").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertEquals("gray", exchange.getAttributes().get(GrayLoadBalancer.GRAY_TAG_HEADER));
    }

    @Test
    @DisplayName("正常场景：X-Gray-Tag=stable 头透传并写入 attribute")
    void stableHeaderShouldSetAttribute() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users")
                        .header(GrayLoadBalancer.GRAY_TAG_HEADER, "stable").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertEquals("stable", exchange.getAttributes().get(GrayLoadBalancer.GRAY_TAG_HEADER));
    }

    @Test
    @DisplayName("正常场景：查询参数 gray=true 注入灰度标识")
    void queryParamTrueShouldInjectGrayTag() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users?gray=true").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertEquals("gray", exchange.getAttributes().get(GrayLoadBalancer.GRAY_TAG_HEADER));
    }

    @Test
    @DisplayName("正常场景：查询参数 gray=false 注入稳定标识")
    void queryParamFalseShouldInjectStableTag() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users?gray=false").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertEquals("stable", exchange.getAttributes().get(GrayLoadBalancer.GRAY_TAG_HEADER));
    }

    @Test
    @DisplayName("正常场景：路径 /canary/ 前缀自动注入灰度标识")
    void canaryPathShouldInjectGrayTag() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/canary/api/users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertEquals("gray", exchange.getAttributes().get(GrayLoadBalancer.GRAY_TAG_HEADER));
    }

    @Test
    @DisplayName("正常场景：无灰度标识时正常放行不设置 attribute")
    void noGrayTagShouldPassThroughWithoutAttribute() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertNull(exchange.getAttributes().get(GrayLoadBalancer.GRAY_TAG_HEADER));
    }

    @Test
    @DisplayName("正常场景：查询参数优先级低于请求头")
    void headerTakesPrecedenceOverQueryParam() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users?gray=true")
                        .header(GrayLoadBalancer.GRAY_TAG_HEADER, "stable").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any(ServerWebExchange.class));
        assertEquals("stable", exchange.getAttributes().get(GrayLoadBalancer.GRAY_TAG_HEADER));
    }

    @Test
    @DisplayName("正常场景：过滤器优先级为 HIGHEST_PRECEDENCE + 20")
    void orderShouldBeHighestPrecedencePlus20() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 20, filter.getOrder());
    }
}
