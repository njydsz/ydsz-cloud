package com.njydsz.gateway.config;

import org.springframework.core.Ordered;

/**
 * 网关过滤器执行顺序统一常量。
 *
 * <p>所有 {@link org.springframework.cloud.gateway.filter.GlobalFilter} 通过本枚举统一管理顺序偏移量，
 * 保证偏移量全局唯一，消除隐性顺序冲突。
 *
 * <p><b>使用方式：</b>过滤器 {@code getOrder()} 统一返回 {@code GatewayFilterOrder.XXX.getOrder()}，禁止再使用魔法数字。
 *
 * <p><b>顺序约定（offset 越小越先执行）：</b>
 *
 * <pre>
 *   0   W3CTraceContextFilter     链路追踪（最先）
 *   1   AccessLogGlobalFilter     访问日志
 *   3   IpAccessControlFilter     IP 访问控制（黑名单 + 白名单）
 *   4   PayloadValidationFilter   请求体校验
 *   8   WebSocketAuthFilter       WebSocket 认证
 *   10  AuthGlobalFilter          主鉴权 + 内部头注入
 *   12  AuthorizationFilter       网关层粗粒度鉴权（RBAC）
 *   15  ApiKeyAuthFilter          API Key 认证
 *   20  GrayLoadBalancerRequestFilter 灰度标识注入
 *   30  RateLimitFilter           限流
 *   35  AuditLogFilter            审计日志
 *   45  CircuitBreakerGlobalFilter 熔断
 *   200 ApiVersionHeaderFilter    API 版本响应头（响应阶段）
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public enum GatewayFilterOrder {

  /** W3C 链路追踪过滤器 */
  W3C_TRACE(0),
  /** 访问日志过滤器 */
  ACCESS_LOG(1),
  /** IP 访问控制过滤器（黑名单 + 白名单） */
  IP_ACCESS_CONTROL(3),
  /** 请求体安全校验过滤器 */
  PAYLOAD_VALIDATION(4),
  /** WebSocket 认证过滤器 */
  WEBSOCKET_AUTH(8),
  /** 主鉴权过滤器 */
  AUTH(10),
  /** 网关层粗粒度鉴权（RBAC）过滤器 */
  AUTHORIZATION(12),
  /** API Key 认证过滤器 */
  API_KEY_AUTH(15),
  /** 灰度路由标识注入过滤器 */
  GRAY_LOADBALANCER(20),
  /** 灰度路由响应头过滤器（可观测性） */
  GRAY_RESPONSE_HEADER(150),
  /** 限流过滤器 */
  RATE_LIMIT(30),
  /** 审计日志过滤器 */
  AUDIT_LOG(35),
  /** 熔断过滤器 */
  CIRCUIT_BREAKER(45),
  /** API 版本响应头过滤器 */
  API_VERSION_HEADER(200);

  /** 相对 {@link Ordered#HIGHEST_PRECEDENCE} 的偏移量 */
  private final int offset;

  GatewayFilterOrder(int offset) {
    this.offset = offset;
  }

  /**
   * 获取过滤器在 Spring 全局过滤器链中的执行顺序值。
   *
   * @return 基于 {@link Ordered#HIGHEST_PRECEDENCE} 的顺序值
   */
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + offset;
  }
}
