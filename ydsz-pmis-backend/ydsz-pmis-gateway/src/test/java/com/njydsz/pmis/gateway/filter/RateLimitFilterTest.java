package com.njydsz.pmis.gateway.filter;

import com.njydsz.pmis.gateway.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

/**
 * {@link RateLimitFilter} 单元测试（P0-5）
 *
 * <p>覆盖限流开关、白名单路径、过滤器顺序。
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter 限流过滤器测试")
class RateLimitFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private RateLimitProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);

        properties.getPerIp().setEnabled(false);
        properties.getPerUser().setEnabled(false);
        properties.getPerTenant().setEnabled(false);

        filter = new RateLimitFilter(properties, redisTemplate);
    }

    @Test
    @DisplayName("限流关闭时应直接放行")
    void shouldPassThroughWhenDisabled() {
        properties.setEnabled(false);

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
    @DisplayName("验证码路径不限流")
    void shouldSkipCaptchaPath() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/auth/captcha")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, exchange12 -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("过滤器顺序应为 HIGHEST_PRECEDENCE + 30")
    void shouldHaveCorrectOrder() {
        assert filter.getOrder() == org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
