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
 *  -1   WhiteListPreFilter        白名单前置过滤器（最先，标记后后续过滤器直接放行）
 *   0   W3CTraceContextFilter     链路追踪
 *   1   AccessLogGlobalFilter     访问日志
 *   3   IpAccessControlFilter     IP 访问控制（黑名单 + 白名单）
 *   4   PayloadValidationFilter   请求体校验
 *   5   SqlInjectionFilter        SQL 注入检测
 *   8   WebSocketAuthFilter       WebSocket 认证
 *   10  AuthGlobalFilter          主鉴权 + 内部头注入
 *   11  IdempotentGlobalFilter     幂等拦截（X-Idempotency-Key 窗口去重）
 *   15  GatewayApiKeyAuthFilter  API Key 认证
 *   20  GrayLoadBalancerRequestFilter 灰度标识注入
 *   35  AuditLogFilter            审计日志（记录被限流/熔断的请求）
 *   45  CircuitBreakerGlobalFilter 熔断（先判断下游健康状态）
 *   50  RateLimitFilter           限流（熔断通过后执行，避免浪费限流配额）
 *   200 ApiVersionHeaderFilter    API 版本响应头（响应阶段）
 * </pre>
 *
 * @since 26.10.01
 * @author ydsz-team
 */
public enum GatewayFilterOrder {

  /** 白名单前置过滤器（最早执行，对匹配白名单的路径跳过后续所有过滤器的业务逻辑） */
  WHITE_LIST_PRE_FILTER(-1, "白名单前置过滤器"),
  /** W3C 链路追踪过滤器 */
  W3C_TRACE(0, "W3C 链路追踪过滤器"),
  /** 访问日志过滤器 */
  ACCESS_LOG(1, "访问日志过滤器"),
  /** IP 访问控制过滤器（黑名单 + 白名单） */
  IP_ACCESS_CONTROL(3, "IP 访问控制过滤器"),
  /** 请求体安全校验过滤器 */
  PAYLOAD_VALIDATION(4, "请求体安全校验过滤器"),
  /** SQL 注入检测过滤器 */
  SQL_INJECTION(5, "SQL 注入检测过滤器"),
  /** WebSocket 认证过滤器 */
  WEBSOCKET_AUTH(8, "WebSocket 认证过滤器"),
  /** 主鉴权过滤器 */
  AUTH(10, "主鉴权过滤器"),
  /** 幂等拦截过滤器（X-Idempotency-Key 窗口去重） */
  IDEMPOTENT(11, "幂等拦截过滤器"),
  /** 网关 API Key 认证过滤器（区分 userinfo-web 同名过滤器） */
  API_KEY_AUTH(15, "API Key 认证过滤器"),
  /** 灰度路由标识注入过滤器 */
  GRAY_LOADBALANCER(20, "灰度路由标识注入过滤器"),
  /** 灰度路由响应头过滤器（可观测性） */
  GRAY_RESPONSE_HEADER(150, "灰度路由响应头过滤器"),
  /** 审计日志过滤器（记录被限流/熔断的请求） */
  AUDIT_LOG(35, "审计日志过滤器"),
  /** 熔断过滤器（先判断下游健康状态，避免不健康的请求占用限流配额） */
  CIRCUIT_BREAKER(45, "熔断过滤器"),
  /** 限流过滤器（熔断通过后执行，确保仅对下游可用的请求做限流统计） */
  RATE_LIMIT(50, "限流过滤器"),
  /** API 版本响应头过滤器 */
  API_VERSION_HEADER(200, "API 版本响应头过滤器"),
  /** HTTP 缓存控制响应头注入过滤器 */
  CACHE_CONTROL_HEADER(180, "HTTP 缓存控制响应头过滤器");

  /** 相对 {@link Ordered#HIGHEST_PRECEDENCE} 的偏移量 */
  private final int offset;
  /** 过滤器描述 */
  private final String description;

  GatewayFilterOrder(int offset, String description) {
    this.offset = offset;
    this.description = description;
  }

  public String getDescription() {
    return description;
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
