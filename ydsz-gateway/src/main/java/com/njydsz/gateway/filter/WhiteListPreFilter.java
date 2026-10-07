package com.njydsz.gateway.filter;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;

/**
 * 白名单前置过滤器（order = HIGHEST_PRECEDENCE - 1）。
 *
 * <p>在所有过滤器之前执行，对匹配白名单前缀的路径设置 exchange 属性标记，
 * 后续过滤器（PayloadValidation、SqlInjection 等）检测到该标记后直接放行，
 * 避免白名单路径（/actuator、/v3/api-docs、/swagger-ui 等）无谓地经过业务逻辑。
 *
 * <h3>白名单路径</h3>
 *
 * <ul>
 *   <li>{@code /actuator} — 健康检查与指标暴露</li>
 *   <li>{@code /v3/api-docs} — OpenAPI 3 接口文档 JSON</li>
 *   <li>{@code /swagger-ui} — Swagger UI 静态资源</li>
 *   <li>{@code /favicon.ico} — 站点图标</li>
 *   <li>{@code /error} — Spring Boot 错误页面</li>
 *   <li>{@code /health} — 健康检查端点</li>
 * </ul>
 *
 * <h3>执行顺序</h3>
 *
 * <p>{@code HIGHEST_PRECEDENCE - 1}，仅高于 W3CTraceContextFilter（offset=0），
 * 由 WhiteListPreFilter 发出的标记将在所有后续过滤器中生效。
 *
 * @author ydsz
 * @since 26.10.01
 */
@Slf4j
@Component
public class WhiteListPreFilter implements GlobalFilter, Ordered {

  /**
   * 白名单路径前缀集合。
   *
   * <p>匹配规则为 {@link String#startsWith(String)}，覆盖：
   * <ul>
   *   <li>{@code /actuator} — 匹配 {@code /actuator/health}、{@code /actuator/metrics} 等所有子路径</li>
   *   <li>{@code /v3/api-docs} — 匹配 {@code /v3/api-docs/swagger-config} 等</li>
   *   <li>{@code /swagger-ui} — 匹配 {@code /swagger-ui/index.html} 等</li>
   *   <li>{@code /favicon.ico} — 精确匹配</li>
   *   <li>{@code /error} — 匹配 Spring Boot 错误端点</li>
   *   <li>{@code /health} — 匹配独立健康检查端点</li>
   * </ul>
   */
  private static final List<String> WHITE_PREFIXES = List.of(
      "/actuator",
      "/v3/api-docs",
      "/swagger-ui",
      "/favicon.ico",
      "/error",
      "/health");

  /**
   * Exchange 属性键名：标记当前请求已匹配白名单前缀。
   *
   * <p>后续过滤器通过 {@code Boolean.TRUE.equals(exchange.getAttributes().get(ATTR_WHITELIST))} 判断。
   */
  public static final String ATTR_WHITELIST = GatewayConstants.HEADER_TRACE_ID + ".whitelist";

  /**
   * 白名单前置过滤器入口。
   *
   * <p>检查请求 path 是否以任一白名单前缀开头。命中则将 exchange 属性标记为 true，
   * 后续过滤器据此跳过请求体校验、SQL 注入检测等逻辑。无论是否命中，均继续执行过滤器链
   * （W3CTraceContextFilter 仍需注入 traceId）。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String path = request.getURI().getPath();

    for (String prefix : WHITE_PREFIXES) {
      if (path.startsWith(prefix)) {
        exchange.getAttributes().put(ATTR_WHITELIST, Boolean.TRUE);
        log.debug("[WhiteListPre] path={} matched prefix={}, marked as whitelist", path, prefix);
        return chain.filter(exchange);
      }
    }

    return chain.filter(exchange);
  }

  /**
   * 过滤器执行顺序：在 W3CTraceContextFilter（offset=0）之前。
   *
   * @return 顺序值，对应 {@link GatewayFilterOrder#WHITE_LIST_PRE_FILTER}
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.WHITE_LIST_PRE_FILTER.getOrder();
  }
}
