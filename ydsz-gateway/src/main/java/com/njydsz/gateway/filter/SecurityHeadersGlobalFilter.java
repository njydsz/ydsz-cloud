package com.njydsz.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 全局安全响应头注入过滤器（WebFlux {@link WebFilter}）。
 *
 * <p>为所有经过网关的 HTTP 响应注入以下安全头，独立于认证过滤器：
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — 防止 MIME 类型嗅探
 *   <li>{@code X-Frame-Options: DENY} — 防止点击劫持
 *   <li>{@code X-XSS-Protection: 1; mode=block} — 启用 XSS 过滤器并阻止渲染
 *   <li>{@code Strict-Transport-Security: max-age=31536000; includeSubDomains} — 强制 HTTPS（HSTS）
 *   <li>{@code Content-Security-Policy: default-src 'self'} — 内容安全策略
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — 控制 Referer 发送策略
 * </ul>
 *
 * <p><b>执行顺序：</b>{@code HIGHEST_PRECEDENCE + 100}，确保在认证过滤器（AUTH=10）之后、
 * 业务过滤器（RATE_LIMIT=50 等）之前执行。
 *
 * <p><b>设计说明：</b>
 *
 * <ul>
 *   <li>与 {@code AuthGlobalFilter#withSecurityHeaders} 互补：AuthGlobalFilter 仅在认证链路中注入安全头，
 *       本过滤器作为兜底覆盖所有响应（含白名单路径、OPTIONS 预检等）
 *   <li>使用 {@link WebFilter} 接口而非 Gateway GlobalFilter，使其作为响应阶段的安全头注入兜底
 * </ul>
 *
 * @author ydsz
 * @since 26.10.14
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class SecurityHeadersGlobalFilter implements WebFilter {

  private static final String HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
  private static final String HEADER_FRAME_OPTIONS = "X-Frame-Options";
  private static final String HEADER_XSS_PROTECTION = "X-XSS-Protection";
  private static final String HEADER_HSTS = "Strict-Transport-Security";
  private static final String HEADER_CSP = "Content-Security-Policy";
  private static final String HEADER_REFERRER_POLICY = "Referrer-Policy";

  private static final String VALUE_CONTENT_TYPE_OPTIONS = "nosniff";
  private static final String VALUE_FRAME_OPTIONS = "DENY";
  private static final String VALUE_XSS_PROTECTION = "1; mode=block";
  private static final String VALUE_HSTS = "max-age=31536000; includeSubDomains";
  private static final String VALUE_CSP = "default-src 'self'";
  private static final String VALUE_REFERRER_POLICY = "strict-origin-when-cross-origin";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    HttpHeaders headers = exchange.getResponse().getHeaders();

    headers.set(HEADER_CONTENT_TYPE_OPTIONS, VALUE_CONTENT_TYPE_OPTIONS);
    headers.set(HEADER_FRAME_OPTIONS, VALUE_FRAME_OPTIONS);
    headers.set(HEADER_XSS_PROTECTION, VALUE_XSS_PROTECTION);
    headers.set(HEADER_HSTS, VALUE_HSTS);
    headers.set(HEADER_CSP, VALUE_CSP);
    headers.set(HEADER_REFERRER_POLICY, VALUE_REFERRER_POLICY);

    return chain.filter(exchange);
  }
}
