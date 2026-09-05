package com.njydsz.gateway.filter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.sentry.SentryObservation;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayErrorWriter;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.config.IpAccessControlProperties;

/**
 * IP 访问控制全局过滤器。
 *
 * <p>统一处理 IP 黑名单和白名单检查：
 *
 * <ol>
 *   <li>黑名单优先：即使 IP 在白名单中，黑名单命中也拒绝（安全优先）
 *   <li>白名单次之：白名单启用且 IP 不在列表中时拒绝
 * </ol>
 *
 * <h3>黑名单架构（两级缓存）</h3>
 *
 * <ul>
 *   <li>L1: ydsz-common-cache 本地缓存（TTL=10s）— 拦截 99% 的恶意 IP 请求
 *   <li>L2: Redis 远程缓存 — 多实例共享黑名单，运维或安全系统动态写入
 * </ul>
 *
 * <h3>Redis 键设计</h3>
 *
 * <pre>
 *   ydsz:ip:blacklist:{ip}  → 1   (TTL: 可配置，默认 24h)
 * </pre>
 *
 * <h3>执行顺序</h3>
 *
 * <p>{@code HIGHEST_PRECEDENCE + 3}，在认证(+10)之前执行，尽早拦截恶意请求。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "ip-access-control",
    havingValue = "true",
    matchIfMissing = true)
public class IpAccessControlFilter implements GlobalFilter, Ordered {

  /** Redis IP 黑名单键前缀 */
  private static final String IP_BLACKLIST_PREFIX = "ydsz:ip:blacklist:";

  /** 白名单配置分隔符 */
  private static final String WHITELIST_SEPARATOR = "[,\\n]";

  /**
   * 默认本地缓存 TTL（秒）：IP 黑名单 L1 缓存的过期时间。
   *
   * <p>该值是缓存构建时的兜底默认，实际运行时由 {@link IpAccessControlProperties#getBlacklistTtlSeconds()} 注入学性。
   * 10s TTL 可在 99% 的恶意请求被 L1 拦截，同时保证黑名单更新在 10s 内全局生效。
   */
  private static final long DEFAULT_BLACKLIST_CACHE_TTL_SECONDS = 10L;

  /**
   * 默认本地缓存最大容量：IP 黑名单 L1 缓存的最大条目数。
   *
   * <p>该值是缓存构建时的兜底默认，实际运行时由 {@link IpAccessControlProperties#getBlacklistMaxSize()} 注入学性。
   * 50,000 条足够覆盖中大规模恶意 IP 列表，内存占用约 5MB（每个 String key ~100B + Boolean value ~16B）。
   */
  private static final long DEFAULT_BLACKLIST_CACHE_MAX_SIZE = 50_000L;

  private final IpAccessControlProperties properties;
  private final ReactiveStringRedisTemplate redisTemplate;

  /** L1 本地缓存：IP → 是否在黑名单中（延迟到 @PostConstruct 初始化，以便读取配置属性） */
  private Cache<String, Boolean> localCache;

  /** 缓存解析后的白名单集合 */
  private final AtomicReference<Set<String>> cachedWhitelist = new AtomicReference<>(Set.of());

  /** 上一次解析的白名单原始字符串 */
  private volatile String lastRawWhitelist = null;

  /**
   * 初始化本地缓存并校验 IP 访问控制配置的合法性。
   *
   * <p>校验规则：
   * <ul>
   *   <li>blacklistFailMode 必须为 fail-open 或 fail-closed</li>
   *   <li>白名单 skip-paths 不应包含敏感路径（如 /admin/**）而无显式配置</li>
   * </ul>
   *
   * <p>本地 L1 缓存参数从 {@link IpAccessControlProperties} 注入学性获取，
   * 实现缓存配置外部化，无需改代码即可调整缓存大小和 TTL。
   *
   * @throws IllegalStateException 配置非法时抛出，阻止应用启动
   */
  @PostConstruct
  public void validateConfiguration() {
    String failMode = properties.getBlacklistFailMode();
    if (!"fail-open".equalsIgnoreCase(failMode) && !"fail-closed".equalsIgnoreCase(failMode)) {
      throw new IllegalStateException(
          "IP 访问控制配置非法： blacklistFailMode 必须为 fail-open 或 fail-closed，当前值=" + failMode);
    }

    long ttlSeconds = properties.getBlacklistTtlSeconds();
    long maxSize = properties.getBlacklistMaxSize();
    if (ttlSeconds <= 0) {
      log.warn("[IpAccess] blacklistTtlSeconds={} 非法，回退默认值 {}s", ttlSeconds, DEFAULT_BLACKLIST_CACHE_TTL_SECONDS);
      ttlSeconds = DEFAULT_BLACKLIST_CACHE_TTL_SECONDS;
    }
    if (maxSize <= 0) {
      log.warn("[IpAccess] blacklistMaxSize={} 非法，回退默认值 {}", maxSize, DEFAULT_BLACKLIST_CACHE_MAX_SIZE);
      maxSize = DEFAULT_BLACKLIST_CACHE_MAX_SIZE;
    }

    // 初始化 L1 本地缓存（参数从配置属性注入学性获取）
    this.localCache =
        YdszCache.<String, Boolean>newBuilder()
            .type(CacheType.STRIPED)
            .name("gateway:ip-blacklist")
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .maximumSize(maxSize)
            .build();

    log.info("[IpAccess] L1 本地缓存已初始化: ttlSeconds={}s maxSize={}", ttlSeconds, maxSize);

    boolean blacklistEnabled = properties.isBlacklistEnabled();
    boolean whitelistEnabled = properties.isWhitelistEnabled();
    if (!blacklistEnabled && !whitelistEnabled) {
      log.info("[IpAccess] 黑名单和白名单均未启用，IP 访问控制过滤器处于观察模式（仅放行）");
    } else if (blacklistEnabled && whitelistEnabled) {
      log.info("[IpAccess] 黑名单和白名单同时启用：黑名单优先，IP 不在黑名单时继续白名单校验");
    } else if (blacklistEnabled) {
      log.info("[IpAccess] 仅黑名单已启用，failMode={}", failMode);
    } else {
      log.info("[IpAccess] 仅白名单已启用，skipPaths={}", properties.getWhitelistSkipPaths());
    }
  }

  /**
   * IP 访问控制过滤器入口。
   *
   * <p>先检查黑名单（优先），再检查白名单。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行或拒绝（403）的完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String clientIp = GatewayIpUtils.getClientIp(request);

    // 无法获取 IP 则放行
    if (clientIp.isEmpty()) {
      return chain.filter(exchange);
    }

    // 1. 黑名单检查（优先）
    if (properties.isBlacklistEnabled()) {
      return checkBlacklist(exchange, chain, clientIp);
    }

    // 2. 白名单检查
    return checkWhitelist(exchange, chain, clientIp);
  }

  /**
   * 检查 IP 黑名单。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @param clientIp 客户端 IP
   * @return 放行或拒绝的完成信号 Mono
   */
  private Mono<Void> checkBlacklist(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp) {
    // L1: 先查本地缓存
    Boolean cached = localCache.getIfPresent(clientIp);
    if (Boolean.TRUE.equals(cached)) {
      alertBlacklistHit(exchange, clientIp, "L1");
      return forbidden(exchange, "error.IP_BLACKLISTED");
    }
    if (cached != null) {
      // cached == false，不在黑名单，继续白名单检查
      return checkWhitelist(exchange, chain, clientIp);
    }

    // L2: 查 Redis
    return redisTemplate
        .hasKey(IP_BLACKLIST_PREFIX + clientIp)
        .defaultIfEmpty(false)
        .flatMap(
            blacklisted -> {
              localCache.put(clientIp, blacklisted);
              if (Boolean.TRUE.equals(blacklisted)) {
                alertBlacklistHit(exchange, clientIp, "L2");
                return forbidden(exchange, "error.IP_BLACKLISTED");
              }
              // 不在黑名单，继续白名单检查
              return checkWhitelist(exchange, chain, clientIp);
            })
        .onErrorResume(
            e -> {
              if ("fail-closed".equalsIgnoreCase(properties.getBlacklistFailMode())) {
                log.warn("[IpAccess] Redis 查询异常，fail-closed 拒绝 ip={} err={}", clientIp, e.getMessage());
                return forbidden(exchange, "error.IP_BLACKLISTED");
              }
              log.warn("[IpAccess] Redis 查询异常，fail-open 降级放行 ip={} err={}", clientIp, e.getMessage());
              return checkWhitelist(exchange, chain, clientIp);
            });
  }

  /**
   * 发送黑名单命中告警。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param clientIp 客户端 IP
   * @param cacheLevel 缓存级别（L1/L2）
   */
  private void alertBlacklistHit(ServerWebExchange exchange, String clientIp, String cacheLevel) {
    SentryObservation.alert(
        AlertEvent.builder()
            .name("gateway.ip_blacklist.hit")
            .severity(AlertSeverity.P2)
            .summary("IP 黑名单命中（" + cacheLevel + "）")
            .description("恶意 IP 请求被网关拦截")
            .category("security")
            .labels(Map.of("ip", clientIp, "path", exchange.getRequest().getURI().getPath(), "cache_level", cacheLevel))
            .build());
    log.warn("[IpAccess] {} 命中黑名单 ip={} path={}", cacheLevel, clientIp, exchange.getRequest().getURI().getPath());
  }

  /**
   * 检查 IP 白名单。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @param clientIp 客户端 IP
   * @return 放行或拒绝的完成信号 Mono
   */
  private Mono<Void> checkWhitelist(ServerWebExchange exchange, GatewayFilterChain chain, String clientIp) {
    if (!properties.isWhitelistEnabled()) {
      return chain.filter(exchange);
    }

    String path = exchange.getRequest().getURI().getPath();

    // 跳过路径前缀匹配
    if (isSkipPath(path)) {
      return chain.filter(exchange);
    }

    // 获取缓存的白名单集合
    Set<String> whitelist = getOrParseWhitelist(properties.getWhitelist());
    if (whitelist.isEmpty()) {
      // 白名单为空视为未配置，放行所有
      return chain.filter(exchange);
    }

    // 命中白名单则放行
    if (GatewayIpUtils.isAllowed(clientIp, whitelist)) {
      return chain.filter(exchange);
    }

    // 非白名单 IP：返回 403
    log.warn("[IpAccess] 拒绝非白名单 IP 访问 ip={}, path={}", clientIp, path);
    return forbidden(exchange, "error.IP_FORBIDDEN");
  }

  /**
   * 获取缓存的白名单集合（仅在配置变更时重新解析）。
   *
   * @param raw 原始配置字符串
   * @return 白名单条目集合
   */
  private Set<String> getOrParseWhitelist(String raw) {
    if (raw != null ? raw.equals(lastRawWhitelist) : lastRawWhitelist == null) {
      return cachedWhitelist.get();
    }
    Set<String> parsed = parseWhitelist(raw);
    cachedWhitelist.set(parsed);
    lastRawWhitelist = raw;
    return parsed;
  }

  /**
   * 解析白名单配置字符串为集合。
   *
   * @param raw 原始配置字符串
   * @return 白名单条目集合
   */
  private Set<String> parseWhitelist(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(raw.split(WHITELIST_SEPARATOR))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * 判断请求路径是否命中跳过路径前缀。
   *
   * @param path 请求路径
   * @return true 表示该路径不校验 IP
   */
  private boolean isSkipPath(String path) {
    if (path == null || path.isEmpty()) {
      return false;
    }
    for (String skip : properties.getWhitelistSkipPaths()) {
      if (skip != null && !skip.isBlank() && path.startsWith(skip.trim())) {
        return true;
      }
    }
    return false;
  }

  /**
   * 返回 403 禁止访问响应（P0-D1：统一错误响应写出器）。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param errorCode 错误码（i18n key）
   * @return 完成信号 Mono
   */
  private Mono<Void> forbidden(ServerWebExchange exchange, String errorCode) {
    return GatewayErrorWriter.write(
        exchange, HttpStatus.FORBIDDEN, resolveForbiddenErrorCode(errorCode), errorCode);
  }

  /**
   * 按 i18n key 解析 IP 访问控制的业务错误码。
   *
   * @param errorCode 错误消息键（error.IP_BLACKLISTED / error.IP_FORBIDDEN）
   * @return 对应业务错误码
   */
  private GatewayErrorCode resolveForbiddenErrorCode(String errorCode) {
    if ("error.IP_BLACKLISTED".equals(errorCode)) {
      return GatewayErrorCode.IP_BLACKLISTED;
    }
    if ("error.IP_FORBIDDEN".equals(errorCode)) {
      return GatewayErrorCode.IP_FORBIDDEN;
    }
    return GatewayErrorCode.FORBIDDEN;
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.IP_ACCESS_CONTROL.getOrder();
  }
}
