package com.njydsz.gateway.config;

import org.springframework.core.Ordered;

/**
 * 网关过滤器执行顺序统一常量（Q3 治理）。
 *
 * <p>历史版本所有 {@link org.springframework.cloud.gateway.filter.GlobalFilter}
 * 通过魔法数字 {@code Ordered.HIGHEST_PRECEDENCE + N} 各自维护顺序，
 * 曾出现 {@code PayloadValidationFilter} 与 {@code IpBlacklistFilter} 同为
 * {@code +3} 的顺序冲突。本枚举将全部过滤器的偏移量集中管理，
 * 并保证偏移量全局唯一，消除隐性顺序炸弹。
 *
 * <p><b>使用方式：</b>过滤器 {@code getOrder()} 统一返回
 * {@code GatewayFilterOrder.XXX.getOrder()}，禁止再使用魔法数字。
 *
 * <p><b>顺序约定（offset 越小越先执行）：</b>
 * <pre>
 *   0   W3CTraceContextFilter     链路追踪（最先）
 *   1   AccessLogGlobalFilter     访问日志
 *   3   IpBlacklistFilter         IP 黑名单
 *   4   PayloadValidationFilter   请求体校验（原 +3 与黑名单冲突，调整至 +4）
 *   5   IpWhitelistFilter         IP 白名单
 *   8   WebSocketAuthFilter       WebSocket 认证
 *   10  AuthGlobalFilter          主鉴权 + 内部头注入
 *   15  ApiKeyAuthFilter          API Key 认证
 *   20  GrayLoadBalancerRequestFilter 灰度标识注入
 *   25  ResponseCacheFilter       响应缓存
 *   28  IdempotencyFilter         幂等性检查（P3-7）
 *   30  RateLimitFilter           限流
 *   35  AuditLogFilter            审计日志
 *   45  CircuitBreakerGlobalFilter 熔断（+100 之前、转发之前生效）
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
    /** IP 黑名单过滤器 */
    IP_BLACKLIST(3),
    /** 请求体安全校验过滤器 */
    PAYLOAD_VALIDATION(4),
    /** IP 白名单过滤器 */
    IP_WHITELIST(5),
    /** WebSocket 认证过滤器 */
    WEBSOCKET_AUTH(8),
    /** 主鉴权过滤器 */
    AUTH(10),
    /** API Key 认证过滤器 */
    API_KEY_AUTH(15),
    /** 灰度路由标识注入过滤器 */
    GRAY_LOADBALANCER(20),
    /** 响应缓存过滤器 */
    RESPONSE_CACHE(25),
    /** 幂等性检查过滤器（P3-7） */
    IDEMPOTENCY(28),
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
