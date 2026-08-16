package com.njydsz.gateway;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.service.ReactiveTokenBlacklistService;
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.common.safe.crypto.NonceCache;
import com.njydsz.gateway.config.CorsProperties;
import com.njydsz.gateway.config.GatewayAlertService;
import com.njydsz.gateway.config.GatewayHealthIndicator;
import com.njydsz.gateway.config.GatewayMetrics;
import com.njydsz.gateway.config.IpWhitelistProperties;
import com.njydsz.gateway.config.RateLimitProperties;
import com.njydsz.gateway.config.SecurityHeadersProperties;
import com.njydsz.gateway.filter.AuthGlobalFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * API 网关启动类
 *
 * <p>统一入口：路由分发、鉴权、限流、跨域、链路追踪
 *
 * <h3>网关职责</h3>
 *
 * <ol>
 *   <li><b>路由转发</b>：基于 Nacos 动态路由 + Java 兜底路由（{@link RouteConfig}）
 *   <li><b>统一鉴权</b>：JWT 校验 + 内部头签名（HMAC-SHA256 + nonce 防重放）
 *   <li><b>多维限流</b>：IP/用户/租户三维令牌桶（自建 RateLimitFilter）
 *   <li><b>安全防护</b>：IP 黑/白名单 + WebSocket 认证 + Payload 校验 + 安全响应头
 *   <li><b>链路追踪</b>：W3C Trace Context（traceparent）+ X-Trace-Id 兼容
 *   <li><b>灰度发布</b>：基于 Nacos metadata 的灰度路由 + 加权轮询
 *   <li><b>监控告警</b>：Prometheus 指标 + 钉钉/飞书 IM 告警
 * </ol>
 *
 * <h3>过滤器执行顺序</h3>
 *
 * <pre>
 *   HIGHEST_PRECEDENCE       W3CTraceContextFilter      (生成 traceparent)
 *   HIGHEST_PRECEDENCE + 1   AccessLogGlobalFilter     (结构化访问日志)
 *   HIGHEST_PRECEDENCE + 3   PayloadValidationFilter   (请求体大小校验)
 *   HIGHEST_PRECEDENCE + 3   IpBlacklistFilter         (动态 IP 黑名单)
 *   HIGHEST_PRECEDENCE + 5   IpWhitelistFilter         (IP 白名单)
 *   HIGHEST_PRECEDENCE + 8   WebSocketAuthFilter       (WebSocket 独立鉴权)
 *   HIGHEST_PRECEDENCE + 10  AuthGlobalFilter          (主鉴权 + 内部头注入)
 *   HIGHEST_PRECEDENCE + 15  ApiKeyAuthFilter          (API Key 备选认证)
 *   HIGHEST_PRECEDENCE + 20  GrayLoadBalancerRequestFilter (灰度标识注入)
 *   HIGHEST_PRECEDENCE + 25  ResponseCacheFilter       (响应缓存，P2-2)
 *   HIGHEST_PRECEDENCE + 30  RateLimitFilter           (令牌桶限流)
 *   HIGHEST_PRECEDENCE + 35  AuditLogFilter            (审计日志，P2-2)
 *   HIGHEST_PRECEDENCE + 100 ReactiveLoadBalancerClientFilter (Spring Cloud LB)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({
  RateLimitProperties.class,
  SecurityHeadersProperties.class,
  IpWhitelistProperties.class,
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
   * @param securityHeadersProvider 安全响应头配置，用于校验安全头策略是否已生效
   * @param rateLimitPropertiesProvider 限流配置，用于上报当前令牌桶阈值
   * @param ipWhitelistProvider IP 白名单配置，用于上报白名单启用状态与条目数
   * @param authFilterProvider 主鉴权过滤器，用于探测 JWT 密钥等鉴权前置条件
   * @param gatewayMetricsProvider 网关指标采集器，用于输出实时 QPS、错误率等运行指标
   * @return 健康指标实现，由 Actuator 以 {@code gateway} 为 key 聚合到 {@code /actuator/health}
   */
  @Bean
  public GatewayHealthIndicator gatewayHealthIndicator(
      ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
      ObjectProvider<SecurityHeadersProperties> securityHeadersProvider,
      ObjectProvider<RateLimitProperties> rateLimitPropertiesProvider,
      ObjectProvider<IpWhitelistProperties> ipWhitelistProvider,
      ObjectProvider<AuthGlobalFilter> authFilterProvider,
      ObjectProvider<GatewayMetrics> gatewayMetricsProvider) {
    return new GatewayHealthIndicator(
        redisTemplateProvider,
        securityHeadersProvider,
        rateLimitPropertiesProvider,
        ipWhitelistProvider,
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

  /**
   * 注册 Nonce 防重放缓存，配合内部头 HMAC 签名阻断请求重放攻击。
   *
   * <p>复用 ydsz-common-safe 的 NonceCache：网关为每个转发请求生成一次性 nonce 并写入缓存，同时通过 {@code X-Internal-Nonce}
   * 头透传；下游服务收到后调用 {@code verifyAndConsume()} 做「校验即消费」的双重确认，同一 nonce 二次出现 即判定为重放并拒绝。
   *
   * <p><b>线程安全：</b>NonceCache 内部基于并发容器实现，可被所有网关工作线程共享； 条目按签名有效期自动过期，无需外部清理。
   *
   * @return 单例 nonce 缓存，全局共享
   */
  @Bean
  public NonceCache nonceCache() {
    return new NonceCache();
  }

  /**
   * 注册网关告警通知服务，将入口层异常事件实时推送到运维 IM 群。
   *
   * <p>集成 ydsz-common-notify 的 {@link NotifyHelper}，在限流触发、IP 黑名单命中、 下游 502/504
   * 等关键事件时发送钉钉/飞书通知，使入口层故障不必等待监控轮询即可被感知。
   *
   * <p><b>降级策略：</b>{@link NotifyHelper} 未配置时以 {@link ObjectProvider} 形式
   * 优雅缺省，告警静默丢弃而不影响请求主链路；告警发送为异步旁路，不阻塞转发。
   *
   * @param notifyHelperProvider 通知辅助类；未配置时告警降级为空操作
   * @param alertWebhookUrl 告警目标 DingTalk Webhook URL（含 access_token）， 未配置时不发送告警
   * @return 网关告警服务，供各过滤器在异常分支调用
   */
  @Bean
  public GatewayAlertService gatewayAlertService(
      ObjectProvider<NotifyHelper> notifyHelperProvider,
      @Value("${ydsz.gateway.alert.dingtalk-webhook:}") String alertWebhookUrl) {
    return new GatewayAlertService(notifyHelperProvider, alertWebhookUrl);
  }
}
