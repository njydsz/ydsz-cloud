package com.njydsz.gateway.config;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.njydsz.common.auth.model.UserInfo;
import com.njydsz.common.auth.token.TokenService;
import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.safe.sensitive.SensitiveUtil;

/**
 * JWT 校验结果本地缓存。
 *
 * <p>使用 ydsz-common-cache 本地缓存 JWT 解析结果，避免每个请求重复执行 JWT 解析的 CPU 开销。
 *
 * <h3>缓存策略</h3>
 *
 * <ul>
 *   <li>缓存键: JWT Token 字符串
 *   <li>缓存值: Optional&lt;UserInfo&gt; 解析结果（空表示无效 Token）
 *   <li>TTL: 可配置（默认 10 秒）
 *   <li>最大容量: 10,000 条（防止内存溢出）
 * </ul>
 *
 * <h3>防护机制</h3>
 *
 * <ul>
 *   <li>防击穿: 使用 {@code Cache#getWithProtection} 保证同一 Token 并发请求仅一个线程执行 JWT 解析
 *   <li>防穿透: 无效 Token 以空值占位符短时缓存（2-5s 随机抖动）
 * </ul>
 *
 * <h3>多实例一致性</h3>
 *
 * <p>本实现采用 TTL 过期策略保证多实例间最终一致性。黑名单生效延迟最长为 TTL 时间（默认 10s）。
 * 对于需要即时失效的场景，建议缩短 JWT Token 过期时间（如 15min）配合 refresh token 机制。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
public class CachedJwtValidator {

  /** 缓存最大容量 */
  private static final long CACHE_MAX_SIZE = 10_000L;

  /** 空值占位最小过期时间（毫秒）——防穿透 */
  private static final long NULL_CACHE_MIN_MS = 2_000L;

  /** 空值占位最大过期时间（毫秒）——随机抖动防雪崩 */
  private static final long NULL_CACHE_MAX_MS = 5_000L;

  /** 缓存 TTL（秒） */
  private final long cacheTtlSeconds;

  /** 缓存命中计数器 */
  private final AtomicLong cacheHitCount = new AtomicLong(0);

  /** 缓存未命中计数器 */
  private final AtomicLong cacheMissCount = new AtomicLong(0);

  /** 本地缓存实例 */
  private final Cache<String, Optional<UserInfo>> claimsCache;

  /** Token 服务 */
  private final TokenService tokenService;

  /** 网关指标组件（可选） */
  private final GatewayMetrics gatewayMetrics;

  /**
   * 构造 JWT 缓存校验器。
   *
   * @param tokenService Token 服务
   * @param gatewayMetrics 网关指标组件（可选，用于记录 JWT 校验耗时）
   * @param cacheTtlSeconds 缓存 TTL（秒），通过配置注入
   */
  public CachedJwtValidator(
      TokenService tokenService,
      GatewayMetrics gatewayMetrics,
      @Value("${ydsz.gateway.jwt.cache-ttl-seconds:10}") long cacheTtlSeconds) {
    this.tokenService = tokenService;
    this.gatewayMetrics = gatewayMetrics;
    this.cacheTtlSeconds = cacheTtlSeconds;
    this.claimsCache =
        YdszCache.<String, Optional<UserInfo>>newBuilder()
            .type(CacheType.STRIPED)
            .name("gateway:jwt-validation")
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .maximumSize(CACHE_MAX_SIZE)
            .recordStats()
            .build();
    log.info("[JwtCache] JWT 校验缓存初始化完成, TTL={}s, maxSize={}", cacheTtlSeconds, CACHE_MAX_SIZE);

    // 注册缓存命中/未命中 Prometheus 指标
    if (gatewayMetrics != null) {
      gatewayMetrics.registerJwtCacheCounters(cacheHitCount, cacheMissCount);
    }
  }

  /**
   * 获取缓存命中率。
   *
   * @return 缓存命中率（0.0 ~ 1.0），无请求时返回 -1.0
   */
  public double getCacheHitRate() {
    long hits = cacheHitCount.get();
    long total = hits + cacheMissCount.get();
    return total > 0 ? (double) hits / total : -1.0;
  }

  /**
   * 校验并解析 JWT Token（带缓存 + 防击穿/防穿透）。
   *
   * <p>优先从 Caffeine 缓存读取解析结果；缓存未命中时通过 {@link
   * com.njydsz.common.cache.api.Cache#getWithProtection} 执行解析。
   *
   * @param jwt JWT Token 字符串
   * @return UserInfo 解析结果，Token 无效时返回 null
   */
  public UserInfo validateAndParse(String jwt) {
    if (jwt == null || jwt.isBlank()) {
      return null;
    }

    long startTime = System.currentTimeMillis();
    Optional<UserInfo> cached = claimsCache.getIfPresent(jwt);
    long duration = System.currentTimeMillis() - startTime;

    boolean isCached = cached != null;
    if (isCached) {
      cacheHitCount.incrementAndGet();
      recordMetrics(duration, true);
      return cached.orElse(null);
    }

    // 缓存未命中，使用带防护的加载（防击穿 + 防穿透）
    cacheMissCount.incrementAndGet();
    startTime = System.currentTimeMillis();
    Optional<UserInfo> result = claimsCache.getWithProtection(jwt, this::parseToken, NULL_CACHE_MIN_MS, NULL_CACHE_MAX_MS);
    duration = System.currentTimeMillis() - startTime;

    recordMetrics(duration, false);
    return result == null ? null : result.orElse(null);
  }

  /**
   * 执行实际 JWT 解析（作为 CacheProtectionGuard 的加载器）。
   *
   * <p>防击穿保证同一 key 并发时该方法仅被调用一次；返回 {@code Optional.empty()} 表示无效 Token。
   *
   * @param jwt JWT Token 字符串
   * @return 解析结果包装，无效返回 empty
   */
  private Optional<UserInfo> parseToken(String jwt) {
    if (!tokenService.validateAccessToken(jwt)) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(tokenService.parseAccessToken(jwt));
    } catch (Exception e) {
      log.warn("[JwtCache] 解析 JWT 失败: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 失效单个 Token（黑名单加入后清除缓存）。
   *
   * <p>多实例场景下，其他实例通过 TTL 过期自动同步（延迟最长 {@code cacheTtlSeconds} 秒）。
   *
   * @param jwt 需要失效的 JWT Token
   */
  public void invalidate(String jwt) {
    if (jwt == null || jwt.isBlank()) {
      return;
    }
    claimsCache.invalidate(jwt);
    log.debug("[JwtCache] Token 已从本地缓存移除 jwt={}", maskToken(jwt));
  }

  /** 手动清除缓存（供配置刷新时调用） */
  public void invalidateAll() {
    claimsCache.invalidateAll();
    log.info("[JwtCache] 缓存已手动清除");
  }

  /**
   * 获取缓存统计信息。
   *
   * @return Caffeine 缓存统计快照的字符串表示
   */
  public String getCacheStats() {
    return claimsCache.getStats().toString();
  }

  /**
   * 记录 JWT 校验耗时指标。
   *
   * @param durationMs 耗时（毫秒）
   * @param cached 是否命中缓存
   */
  private void recordMetrics(long durationMs, boolean cached) {
    if (gatewayMetrics == null) {
      return;
    }
    try {
      gatewayMetrics.recordJwtValidationDuration(durationMs, cached);
    } catch (Exception e) {
      // 指标记录失败不影响主流程
      log.debug("[JwtCache] 记录指标失败: {}", e.getMessage());
    }
  }

  /**
   * Token 脱敏。
   *
   * @param jwt JWT Token
   * @return 脱敏后的字符串
   */
  private String maskToken(String jwt) {
    return SensitiveUtil.defaultDesensitize(jwt, '*');
  }

  /** 销毁时清理缓存 */
  @PreDestroy
  public void cleanup() {
    claimsCache.invalidateAll();
    log.info("[JwtCache] 缓存已清理");
  }
}
