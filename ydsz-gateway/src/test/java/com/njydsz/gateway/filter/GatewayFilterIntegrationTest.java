package com.njydsz.gateway.filter;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关全局过滤器集成测试。
 *
 * <p>使用 {@link MockServerWebExchange} 在真实 Reactor 链路上验证过滤器行为，
 * 覆盖此前完全没有测试的高风险链路：W3C 追踪上下文透传、安全响应头注入、
 * SQL 注入拦截。
 *
 * <p><b>为什么不启动完整 Spring Context：</b>网关启动依赖 Nacos 注册中心与配置中心，
 * 离线 CI 环境无法连接；本测试直接构造过滤器实例并驱动其 {@code filter} 方法，
 * 既覆盖过滤器真实逻辑，又保持测试可独立运行（无需 Docker / 网络）。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("网关全局过滤器 — 关键链路集成测试")
class GatewayFilterIntegrationTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  /** 构造一个立即完成的网关过滤器链（被测过滤器执行后链路结束）。 */
  private static GatewayFilterChain chain() {
    return exchange -> Mono.empty();
  }

  @Nested
  @DisplayName("W3C 追踪上下文透传")
  class W3CTraceContext {

    private static final String VALID_TRACEPARENT =
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    @DisplayName("请求携带合法 traceparent 时，响应头回显同一 traceId")
    void echoesTraceIdWhenTraceparentPresent() {
      W3CTraceContextFilter filter = new W3CTraceContextFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/test")
              .header("traceparent", VALID_TRACEPARENT)
              .build());

      filter.filter(exchange, chain()).block(TIMEOUT);

      assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
          .as("应延续上游 traceparent 中的 traceId")
          .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("请求未携带 traceparent 时，自动生成新的 traceId")
    void generatesTraceIdWhenAbsent() {
      W3CTraceContextFilter filter = new W3CTraceContextFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/test").build());

      filter.filter(exchange, chain()).block(TIMEOUT);

      String traceId = exchange.getResponse().getHeaders().getFirst("X-Trace-Id");
      assertThat(traceId).as("应生成 traceId").isNotBlank().hasSize(32);
    }

    @Test
    @DisplayName("上游 traceparent 格式非法时，不抛异常并生成新 traceId")
    void fallsBackWhenTraceparentMalformed() {
      W3CTraceContextFilter filter = new W3CTraceContextFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/test")
              .header("traceparent", "not-a-valid-traceparent")
              .build());

      filter.filter(exchange, chain()).block(TIMEOUT);

      assertThat(exchange.getResponse().getHeaders().getFirst("X-Trace-Id"))
          .as("非法输入应降级为自动生成，而非抛出异常")
          .isNotBlank()
          .hasSize(32);
    }

    @Test
    @DisplayName("响应头同时写入 traceparent（供上游链路继续传播）")
    void writesTraceparentResponseHeader() {
      W3CTraceContextFilter filter = new W3CTraceContextFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/test")
              .header("traceparent", VALID_TRACEPARENT)
              .build());

      filter.filter(exchange, chain()).block(TIMEOUT);

      String traceparent = exchange.getResponse().getHeaders().getFirst("traceparent");
      assertThat(traceparent).as("应写入 traceparent 响应头").isNotBlank();
      assertThat(traceparent).as("traceId 段应保持不变").startsWith("00-4bf92f3577b34da6a3ce929d0e0e4736-");
    }
  }

  @Nested
  @DisplayName("安全响应头注入")
  class SecurityHeaders {

    @Test
    @DisplayName("响应注入 X-Content-Type-Options / X-Frame-Options / CSP")
    void injectsSecurityHeaders() {
      SecurityHeadersGlobalFilter filter = new SecurityHeadersGlobalFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/test").build());

      filter.filter(exchange, e -> Mono.empty()).block(TIMEOUT);

      assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options"))
          .isEqualTo("nosniff");
      assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
          .isEqualTo("DENY");
      assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
          .isEqualTo("default-src 'self'");
    }

    @Test
    @DisplayName("覆盖调用方传入的安全头，防止下游伪造安全响应头")
    void overwritesCallerSuppliedSecurityHeaders() {
      SecurityHeadersGlobalFilter filter = new SecurityHeadersGlobalFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/test").build());
      // 下游服务若自行设置宽松的安全头，网关必须强制覆盖为安全值，
      // 否则 X-Frame-Options: SAMEORIGIN 之类会削弱点击劫持防护。
      exchange.getResponse().getHeaders().set("X-Frame-Options", "SAMEORIGIN");

      filter.filter(exchange, e -> Mono.empty()).block(TIMEOUT);

      assertThat(exchange.getResponse().getHeaders().getFirst("X-Frame-Options"))
          .as("必须强制覆盖为网关的安全值")
          .isEqualTo("DENY");
    }
  }

  @Nested
  @DisplayName("SQL 注入拦截")
  class SqlInjection {

    @Test
    @DisplayName("经典注入特征被识别并阻断为 400")
    void blocksClassicInjection() {
      SqlInjectionFilter filter = buildSqlInjectionFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/users?name=%27%20OR%201%3D1%20--").build());

      filter.filter(exchange, chain()).block(TIMEOUT);

      assertThat(exchange.getResponse().getStatusCode())
          .as("注入请求应返回 400")
          .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("正常查询参数放行（不设置状态码）")
    void allowsNormalQuery() {
      SqlInjectionFilter filter = buildSqlInjectionFilter();
      MockServerWebExchange exchange = MockServerWebExchange.from(
          MockServerHttpRequest.get("/api/users?name=zhangsan&page=1").build());

      filter.filter(exchange, chain()).block(TIMEOUT);

      assertThat(exchange.getResponse().getStatusCode())
          .as("正常请求不应被拦截")
          .isNull();
    }
  }

  private static SqlInjectionFilter buildSqlInjectionFilter() {
    com.njydsz.gateway.config.SqlInjectionProperties properties =
        new com.njydsz.gateway.config.SqlInjectionProperties();
    properties.setEnabled(true);
    properties.setMode("STANDARD");
    properties.setWhitelistParamNames(java.util.List.of("tenantId", "page", "size", "sort"));
    return new SqlInjectionFilter(properties);
  }
}
