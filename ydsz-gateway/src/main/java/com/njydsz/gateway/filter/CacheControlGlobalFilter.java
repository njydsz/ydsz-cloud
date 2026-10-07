package com.njydsz.gateway.filter;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.GatewayFilterOrder;

/**
 * HTTP 缓存控制响应头注入过滤器。
 *
 * <p>对 GET 请求的只读配置类接口注入 {@code Cache-Control} 响应头，允许浏览器和 CDN
 * 缓存响应结果，减少重复请求频率，降低后端负载。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>默认 max-age = 60 秒（可通过 ydzs.gateway.cache-control.default-max-age 配置）
 *   <li>特定前缀可单独配置不同 max-age（/dict/ /config/ /variable/ 等低频变更接口）
 * </ul>
 *
 * <p><b>安全约束：</b>
 *
 * <ul>
 *   <li>仅对 GET 方法注入缓存头，POST/PUT/DELETE 不缓存
 *   <li>仅匹配 {@link CacheControlProperties#getPaths()} 中配置的路径前缀，避免误缓存业务接口
 * </ul>
 *
 * @author ydsz
 * @since 26.10.06
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.cache-control",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class CacheControlGlobalFilter implements GlobalFilter, Ordered {

  /** Cache-Control 响应头名称 */
  private static final String HEADER_CACHE_CONTROL_NAME = "Cache-Control";

  private static final String HEADER_VARY = "Vary";

  /** Vary 头值：告诉缓存服务器根据 Accept-Encoding 区分缓存版本 */
  private static final String VARY_ACCEPT_ENCODING = "Accept-Encoding";

  private final CacheControlProperties properties;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();

    // 仅对 GET 方法注入缓存头
    if (!HttpMethod.GET.equals(request.getMethod())) {
      return chain.filter(exchange);
    }

    String path = request.getURI().getPath();

    // 计算该路径应使用的 max-age
    Integer maxAge = resolveMaxAge(path);
    if (maxAge == null) {
      return chain.filter(exchange);
    }

    // 在响应提交前注入缓存头
    exchange
        .getResponse()
        .beforeCommit(
            () -> {
              try {
                injectCacheHeaders(exchange.getResponse(), maxAge);
              } catch (Exception e) {
                log.debug("[CacheControl] 注入缓存响应头失败: {}", e.getMessage());
              }
              return Mono.empty();
            });

    return chain.filter(exchange);
  }

  /**
   * 根据路径解析应使用的缓存过期时间（秒）。
   *
   * @param path 请求路径
   * @return 缓存秒数，null 表示不缓存
   */
  private Integer resolveMaxAge(String path) {
    if (properties.getPaths() == null || properties.getPaths().isEmpty()) {
      return null;
    }

    for (CacheControlPathConfig pathConfig : properties.getPaths()) {
      List<String> prefixes = pathConfig.getPrefixes();
      if (prefixes == null) {
        continue;
      }
      for (String prefix : prefixes) {
        if (path.startsWith(prefix)) {
          return pathConfig.getMaxAgeSeconds();
        }
      }
    }

    return null;
  }

  /**
   * 注入缓存响应头。
   *
   * @param response HTTP 响应
   * @param maxAgeSeconds 缓存过期时间（秒）
   */
  private void injectCacheHeaders(ServerHttpResponse response, int maxAgeSeconds) {
    HttpHeaders headers = response.getHeaders();

    // 仅当响应尚未设置 Cache-Control 头时才注入
    if (headers.get(HEADER_CACHE_CONTROL_NAME) != null) {
      return;
    }

    headers.set(HEADER_CACHE_CONTROL_NAME, "public, max-age=" + maxAgeSeconds);

    // 添加 Vary: Accept-Encoding 避免 CDN 缓存未压缩版本
    if (headers.get(HEADER_VARY) == null) {
      headers.set(HEADER_VARY, VARY_ACCEPT_ENCODING);
    }

    log.debug("[CacheControl] 已注入 max-age={}", maxAgeSeconds);
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.CACHE_CONTROL_HEADER.getOrder();
  }

  /**
   * 缓存控制配置属性。
   */
  @Data
  @Component
  @ConfigurationProperties(prefix = "ydsz.gateway.cache-control")
  public static class CacheControlProperties {

    /** 是否启用缓存控制过滤器 */
    private boolean enabled = true;

    /** 默认 max-age (秒) */
    private int defaultMaxAgeSeconds = 60;

    /** 路径级缓存配置 */
    private List<CacheControlPathConfig> paths = List.of(
        // 字典/参数/变量类 — 低频变更，缓存 5 分钟
        new CacheControlPathConfig(
            List.of("/system/dict", "/system/config", "/system/variable", "/system/tenant"),
            300),
        // 用户菜单/权限 — 中频变更，缓存 2 分钟
        new CacheControlPathConfig(
            List.of("/userinfo/menu", "/userinfo/role", "/userinfo/permission"),
            120),
        // 工作流分类/流程定义 — 低频变更，缓存 5 分钟
        new CacheControlPathConfig(
            List.of("/workflow/category", "/workflow/definition"),
            300),
        // 规则引擎规则定义 — 低频变更，缓存 5 分钟
        new CacheControlPathConfig(
            List.of("/literule/rule/def", "/literule/decision-table"),
            300));
  }

  /**
   * 单条路径缓存配置。
   */
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class CacheControlPathConfig {

    /** 路径前缀列表 */
    private List<String> prefixes;

    /** 缓存过期时间（秒） */
    private int maxAgeSeconds;
  }
}
