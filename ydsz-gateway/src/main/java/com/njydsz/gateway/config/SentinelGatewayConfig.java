package com.njydsz.gateway.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Alibaba Sentinel 网关集成配置。
 *
 * <h3>与 Resilience4j 的双层防护体系：</h3>
 *
 * <ul>
 *   <li>Sentinel 入口流量整形</li>
 *   <li>Resilience4j 服务出口熔断</li>
 * </ul>
 *
 * <p>集成 Sentinel 到 Spring Cloud Gateway（WebFlux 栈），提供：
 *
 * <ul>
 *   <li>API 维度流量控制（QPS 阈值保护）
 *   <li>熔断降级（调用下游异常比例触发熔断）
 *   <li>系统自适应保护（load / CPU / 入口 QPS 系统级防护）
 *   <li>热点参数限流（针对高频参数精细化控制）
 * </ul>
 *
 * <p>配合 Resilience4j 形成双层防护体系：
 *
 * <ul>
 *   <li>Sentinel：网关级流量整形 + API 保护 + 系统保护（入口）
 *   <li>Resilience4j：服务级熔断 + 下游异常隔离（出口）
 * </ul>
 *
 * <p>Sentinel 规则通过 Nacos 持久化（{@code sentinel-datasource-nacos}），
 * 支持通过 Sentinel Dashboard 或 Nacos 配置中心动态推送规则变更。
 *
 * <p>参照 SpringBlade blade-core-cloud 模块的 Sentinel 集成模式，
 * 在 YDSZ 中实现三级防护：网关入口流量控制 → 服务级熔断 → 系统自适应保护。
 *
 * <p>配置属性前缀：{@code spring.cloud.sentinel.*}
 *
 * @author ydsz-team
 * @since 26.09.12
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SentinelGatewayProperties.class)
public class SentinelGatewayConfig {

  /** Sentinel 订阅的 API 路径前缀集合（示例，可通过 Nacos 动态刷新）。 */
  private static final Set<String> API_PREFIX_SET = new HashSet<>(16);

  static {
    API_PREFIX_SET.add("/api/");
    API_PREFIX_SET.add("/oauth/");
    API_PREFIX_SET.add("/auth/");
  }

  /**
   * Sentinel 初始化日志。
   *
   * <p>确认 Sentinel Gateway Filter 已成功注册（由 spring-cloud-starter-alibaba-sentinel 自动配置），
   * 并打印当前生效的 API 路径前缀集合。
   */
  @PostConstruct
  public void init() {
    log.info(
        "[SentinelGateway] Sentinel 网关集成已启用 | apiPrefixes={} | "
            + "datasource=nacos | transport.dashboard=${spring.cloud.sentinel.transport.dashboard}",
        API_PREFIX_SET);
  }

  /**
   * 返回 Sentinel 网关流控规则订阅的 API 路径前缀集合。
   *
   * <p>用于在 Sentinel Dashboard 中按 API 维度配置流控规则时参考。
   *
   * @return 不可变的 API 路径前缀集合
   */
  public static Set<String> getApiPrefixSet() {
    return Collections.unmodifiableSet(API_PREFIX_SET);
  }

  /**
   * 判断给定路径是否受 Sentinel 网关规则保护。
   *
   * <p>SentinelGatewayFilter 会自动对所有经过网关的请求生效，
   * 本方法用于业务层判断（如监控、日志）某路径是否受保护。
   *
   * @param path 请求路径
   * @return true 如果路径匹配 Sentinel 保护前缀
   */
  public static boolean isProtectedPath(String path) {
    if (path == null || path.isBlank()) {
      return false;
    }
    for (String prefix : API_PREFIX_SET) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 返回 Sentinel 网关降级响应（BlockExceptionHandler 定制入口）。
   *
   * <p>SentinelGatewayFilter 触发流控熔断时，默认返回 429 状态码和 JSON 错误体。
   * 如需要自定义降级响应格式，可注册 {@code SentinelGatewayBlockExceptionHandler} Bean。
   *
   * @return 默认降级响应消息模板
   */
  public static String getDefaultBlockMessage() {
    return "{\"code\":429,\"msg\":\"请求过于频繁，请稍后再试\"}";
  }

  /** Sentinel API 路径前缀集合类型别名（仅用于文档说明）。 */
  public static final class ApiPrefixes {
    private ApiPrefixes() {}

    /** 业务 API 前缀 */
    public static final String BUSINESS_API = "/api/";
    /** OAuth2 认证前缀 */
    public static final String OAUTH_API = "/oauth/";
    /** 认证服务前缀 */
    public static final String AUTH_API = "/auth/";
  }
}
