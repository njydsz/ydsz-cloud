/**
 * ydsz-gateway 网关模块，提供请求路由、鉴权过滤、限流、灰度负载均衡、WebSocket 连接管理、CORS 等网关能力.
 *
 * <p>本模块作为整个 ydsz-cloud 平台的统一入口网关，基于 Spring Cloud Gateway 构建，负责所有下游微服务
 * 请求的转发与管控。通过全局过滤器链实现身份鉴权、API Key 校验、签名验证、限流熔断、灰度路由、
 * 跨域支持、访问审计与链路追踪等横切关注点，并将 W3C Trace Context 透传至下游服务。</p>
 *
 * <p>核心能力覆盖：</p>
 * <ul>
 *   <li>鉴权体系：{@code AuthGlobalFilter} 负责 JWT 校验；{@code ApiKeyAuthFilter} 处理 API Key 认证；
 *       {@code WebSocketAuthFilter} 保障 WebSocket 握手安全</li>
 *   <li>流量治理：{@code RateLimitFilter} 基于令牌桶算法实现限流；{@code CircuitBreakerGlobalFilter} 提供熔断降级</li>
 *   <li>灰度路由：{@code GrayLoadBalancer} 配合 {@code GrayLoadBalancerRequestFilter} 实现按标签的灰度流量分发</li>
 *   <li>安全防护：{@code IpAccessControlFilter} 实现 IP 黑白名单；{@code PayloadValidationFilter} 校验请求体合法性</li>
 *   <li>可观测性：{@code AccessLogGlobalFilter} 记录访问日志；{@code AuditLogFilter} 审计关键操作；
 *       {@code W3CTraceContextFilter} 传播追踪上下文</li>
 * </ul>
 *
 * <h3>配置与常量</h3>
 *
 * <ul>
 *   <li>{@code GatewayFilterConfig} / {@code GatewayFilterOrder} -- 过滤器注册与顺序定义</li>
 *   <li>{@code RateLimitProperties} / {@code CorsProperties} -- 限流与 CORS 配置</li>
 *   <li>{@code IpAccessControlProperties} -- IP 管控配置</li>
 *   <li>{@code NacosRouteDefinitionRepository} -- 基于 Nacos 的动态路由配置源</li>
 *   <li>{@code WebSocketConnectionLimiter} -- WebSocket 连接数限制器</li>
 *   <li>{@code GatewayMetrics} / {@code GatewayHealthIndicator} -- 网关指标与健康检查</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.gateway;
