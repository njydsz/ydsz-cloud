package com.njydsz.gateway;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.service.ReactiveTokenBlacklistService;
import com.njydsz.common.safe.config.SecurityHeaderProperties;
import com.njydsz.gateway.config.CorsProperties;
import com.njydsz.gateway.config.GatewayHealthIndicator;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.config.IpAccessControlProperties;
import com.njydsz.gateway.config.RateLimitProperties;
import com.njydsz.gateway.filter.AuthGlobalFilter;

/**
 * API 网关启动类
 *
 * <p>统一入口：路由分发、鉴权、限流、跨域、链路追踪
 *
 * <h3>网关职责</h3>
 *
 * <ol>
 *   <li><b>路由转发</b>：基于 Nacos 动态路由 + Java 兜底路由
 *   <li><b>统一鉴权</b>：JWT 校验 + 内部头签名（HMAC-SHA256）
 *   <li><b>多维限流</b>：IP/用户二维令牌桶
 *   <li><b>安全防护</b>：IP 黑/白名单 + WebSocket 认证 + Payload 校验 + 安全响应头
 *   <li><b>链路追踪</b>：W3C Trace Context（traceparent）+ X-Trace-Id 兼容
 *   <li><b>灰度发布</b>：基于 Nacos metadata 的灰度路由 + 加权轮询
 *   <li><b>监控告警</b>：Prometheus 指标 + IM 告警
 * </ol>
 *
 * <h3>过滤器执行顺序</h3>
 *
 * <pre>
 *   HIGHEST_PRECEDENCE       W3CTraceContextFilter      (生成 traceparent)
 *   HIGHEST_PRECEDENCE + 1   AccessLogGlobalFilter     (结构化访问日志)
 *   HIGHEST_PRECEDENCE + 3   IpAccessControlFilter     (IP 黑名单 + 白名单)
 *   HIGHEST_PRECEDENCE + 4   PayloadValidationFilter   (请求体大小校验)
 *   HIGHEST_PRECEDENCE + 8   WebSocketAuthFilter       (WebSocket 独立鉴权)
 *   HIGHEST_PRECEDENCE + 10  AuthGlobalFilter          (主鉴权 + 内部头注入)
 *   HIGHEST_PRECEDENCE + 15  ApiKeyAuthFilter          (API Key 备选认证)
 *   HIGHEST_PRECEDENCE + 20  GrayLoadBalancerRequestFilter (灰度标识注入)
 *   HIGHEST_PRECEDENCE + 30  RateLimitFilter           (令牌桶限流)
 *   HIGHEST_PRECEDENCE + 35  AuditLogFilter            (审计日志)
 *   HIGHEST_PRECEDENCE + 100 ReactiveLoadBalancerClientFilter (Spring Cloud LB)
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({
  RateLimitProperties.class,
  SecurityHeaderProperties.class,
  IpAccessControlProperties.class,
  CorsProperties.class
})
public class GatewayApplication {

  /**
   * 网关启动入口。
   *
   * <p>通过 {@link SpringApplication} 引导 Spring Boot 应用，激活 {@code @EnableDiscoveryClient} 服务注册发现与
   * {@code @EnableConfigurationProperties} 配置属性绑定；所有过滤器、路由在安全校验后由 Spring 容器自动装配。
   *
   * @param args 命令行参数（如 {@code --spring.profiles.active=noroutes} 可禁用 Java 兜底路由）
   */
  public static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }

  /**
   * 注册网关健康指标，向 Actuator 暴露网关自身与核心依赖的健康度。
   *
   * <p>全部依赖均以 {@link ObjectProvider} 注入而非直接注入实例：健康检查属于 旁路能力，任一组件缺失（如未启用 Redis、未开启限流）时应降级为「该项跳过」，
   * 而不能因 Bean 找不到导致网关启动失败。
   *
   * @param redisTemplateProvider Reactive Redis 客户端，用于探测限流/黑名单所依赖的 Redis 连通性；缺失时该检查项跳过
   * @param securityHeaderProvider 安全响应头配置，用于校验安全头策略是否已生效
   * @param rateLimitPropertiesProvider 限流配置，用于上报当前令牌桶阈值
   * @param ipAccessControlProvider IP 访问控制配置，用于上报黑白名单启用状态
   * @param authFilterProvider 主鉴权过滤器，用于探测 JWT 密钥等鉴权前置条件
   * @param gatewayMetricsProvider 网关指标采集器，用于输出实时 QPS、错误率等运行指标
   * @return 健康指标实现，由 Actuator 以 {@code gateway} 为 key 聚合到 {@code /actuator/health}
   */
  @Bean
  public GatewayHealthIndicator gatewayHealthIndicator(
      ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
      ObjectProvider<SecurityHeaderProperties> securityHeaderProvider,
      ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider,
      ObjectProvider<IpAccessControlProperties> ipAccessControlProvider,
      ObjectProvider<AuthGlobalFilter> authFilterProvider,
      ObjectProvider<GatewayMetrics> gatewayMetricsProvider) {
    return new GatewayHealthIndicator(
        redisTemplateProvider,
        securityHeaderProvider,
        rateLimitPropertiesProvider,
        ipAccessControlProvider,
        authFilterProvider,
        gatewayMetricsProvider);
  }

  /**
   * 注册 Reactive Token 黑名单服务，用于登出/踢线后的 JWT 即时失效。
   *
   * <p>复用 ydsz-common-auth 的 SHA-256 摘要 key， 替代网关手写的 Redis 黑名单检查，保证网关与各业务服务的判定口径一致。
   *
   * <p><b>降级策略：</b>Redis 不可用时按「放行」处理，优先保障可用性， 已登出 Token 在此期间可能仍然有效，直至其自然过期。
   *
   * @param redisTemplateProvider Reactive Redis 客户端；未配置 Redis 时黑名单能力整体降级为不拦截
   * @param authProperties 鉴权配置，提供黑名单 key 前缀与 Token 有效期（决定黑名单条目 TTL）
   * @return 供 {@link AuthGlobalFilter} 在鉴权阶段调用的黑名单服务
   */
  @Bean
  public ReactiveTokenBlacklistService reactiveTokenBlacklistService(
      ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
      AuthProperties authProperties) {
    return new ReactiveTokenBlacklistService(redisTemplateProvider, authProperties);
  }

}
