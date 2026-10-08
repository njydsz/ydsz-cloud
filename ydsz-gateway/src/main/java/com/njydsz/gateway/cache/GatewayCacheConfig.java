package com.njydsz.gateway.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.common.cache.spring.SpringYdszCache;
import com.njydsz.common.cache.spring.YdszCacheManager;
import com.njydsz.common.cache.spring.YdszCacheProperties;

import lombok.Data;

/**
 * 网关缓存统一配置（P2-4 P3-1 整改：缓存实例纳入 YdszCacheManager 统一治理，接入 CacheActuator 指标采集）。
 *
 * <p>网关模块的本地缓存实例以往均为各组件 private field 直接 {@code YdszCache.newBuilder()} 创建，
 * 未通过 {@link YdszCacheManager} 管理，存在以下问题：
 *
 * <ul>
 *   <li>命中率/淘汰统计不可观测（Actuator 端点未激活）</li>
 *   <li>运维期无法通过统一接口动态清空或刷新</li>
 *   <li>与 YDIZ-COMMON-049 规范不符（@YdszCacheable 配套 YdszCacheManager）</li>
 * </ul>
 *
 * <p>本配置注册标准 {@link YdszCacheManager} 实例管理网关缓存，并暴露 4 个关键 Cache Bean
 * （rateLimit / ipBlacklist / jwtValidation / grayServiceList），
 * 使 {@code CacheMetricsAutoConfiguration}（Micrometer 绑定）与 {@code CacheActuatorAutoConfiguration}
 * （{@code /actuator/cache-metrics} 端点）在网关模块激活。
 *
 * <p>接入后可查询指标：
 *
 * <ul>
 *   <li>{@code cache.gateway:local-ratelimit.hit.rate} / {@code cache.gateway:ip-blacklist.hit.rate}</li>
 *   <li>{@code cache.gateway:local-ratelimit.size} / {@code cache.gateway:jwt-validation.size}</li>
 * </ul>
 *
 * <p><b>YDIZ-COMMON-048 合规：</b>所有缓存键通过 {@link CacheKeyBuilder} 构造。
 *
 * <p><b>YDIZ-COMMON-049 合规：</b>Cache 实例由 YdszCacheManager 创建并托管为 Spring Bean。
 *
 * @author ydsz-team
 * @since 26.09.29
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.cache")
public class GatewayCacheConfig {

  /** 本地令牌桶限流缓存最大条目数（IP / 用户独立一个桶） */
  private long rateLimitMaxSize = 50_000L;

  /** 本地令牌桶过期时间（分钟） */
  private long rateLimitExpireMinutes = 5L;

  /** IP 黑名单缓存最大条目数 */
  private long ipBlacklistMaxSize = 10_000L;

  /** IP 黑名单过期时间（分钟） */
  private long ipBlacklistExpireMinutes = 30L;

  /** JWT 验证结果缓存最大条目数 */
  private long jwtValidationMaxSize = 5_000L;

  /** JWT 验证结果过期时间（秒） */
  private long jwtValidationExpireSeconds = 10L;

  /** 灰度路由服务列表缓存最大条目数 */
  private long grayServiceListMaxSize = 500L;

  /** 灰度路由服务列表过期时间（分钟） */
  private long grayServiceListExpireMinutes = 5L;

  /** 缓存名常量: 本地令牌桶限流缓存 */
  public static final String CACHE_RATE_LIMIT = "gateway:local-ratelimit";

  /** 缓存名常量: IP 黑名单缓存 */
  public static final String CACHE_IP_BLACKLIST = "gateway:ip-blacklist";

  /** 缓存名常量: JWT 验证结果缓存 */
  public static final String CACHE_JWT_VALIDATION = "gateway:jwt-validation";

  /** 缓存名常量: 灰度路由服务列表缓存 */
  public static final String CACHE_GRAY_SERVICE_LIST = "gateway:gray-service-list";

  /**
   * 网关统一缓存管理器。
   *
   * <p>管理网关业务缓存（{@code gateway:local-ratelimit} / {@code gateway:ip-blacklist} /
   * {@code gateway:jwt-validation} / {@code gateway:gray-service-list}）。
   *
   * @return 网关专用缓存管理器
   */
  @Bean
  @Primary
  public YdszCacheManager gatewayCacheManager() {
    YdszCacheManager manager = new YdszCacheManager();
    manager.setCacheType(CacheType.STRIPED);
    manager.setCacheNames(
        java.util.List.of(
            CACHE_RATE_LIMIT, CACHE_IP_BLACKLIST,
            CACHE_JWT_VALIDATION, CACHE_GRAY_SERVICE_LIST));
    manager.setRecordStats(true);

    Map<String, YdszCacheProperties.CacheConfig> perCache = new HashMap<>();

    perCache.put(CACHE_RATE_LIMIT, buildRateLimitCfg());
    perCache.put(CACHE_IP_BLACKLIST, buildBlacklistCfg());
    perCache.put(CACHE_JWT_VALIDATION, buildJwtCfg());
    perCache.put(CACHE_GRAY_SERVICE_LIST, buildGrayCfg());

    manager.setPerCacheConfigs(perCache);
    return manager;
  }

  /**
   * 构建本地令牌桶缓存配置。
   *
   * @return 令牌桶 CacheConfig
   */
  private YdszCacheProperties.CacheConfig buildRateLimitCfg() {
    YdszCacheProperties.CacheConfig cfg = new YdszCacheProperties.CacheConfig();
    cfg.setType(CacheType.STRIPED);
    cfg.setMaximumSize(rateLimitMaxSize);
    cfg.setExpireAfterAccess(rateLimitExpireMinutes);
    cfg.setExpireTimeUnit(TimeUnit.MINUTES);
    cfg.setRecordStats(Boolean.TRUE);
    return cfg;
  }

  /**
   * 构建 IP 黑名单缓存配置。
   *
   * @return IP 黑名单 CacheConfig
   */
  private YdszCacheProperties.CacheConfig buildBlacklistCfg() {
    YdszCacheProperties.CacheConfig cfg = new YdszCacheProperties.CacheConfig();
    cfg.setType(CacheType.TINYLFU);
    cfg.setMaximumSize(ipBlacklistMaxSize);
    cfg.setExpireAfterWrite(ipBlacklistExpireMinutes);
    cfg.setExpireTimeUnit(TimeUnit.MINUTES);
    cfg.setRecordStats(Boolean.TRUE);
    return cfg;
  }

  /**
   * 构建 JWT 验证缓存配置。
   *
   * @return JWT 验证 CacheConfig
   */
  private YdszCacheProperties.CacheConfig buildJwtCfg() {
    YdszCacheProperties.CacheConfig cfg = new YdszCacheProperties.CacheConfig();
    cfg.setType(CacheType.TINYLFU);
    cfg.setMaximumSize(jwtValidationMaxSize);
    cfg.setExpireAfterWrite(jwtValidationExpireSeconds);
    cfg.setExpireTimeUnit(TimeUnit.SECONDS);
    cfg.setRecordStats(Boolean.TRUE);
    return cfg;
  }

  /**
   * 构建灰度路由服务列表缓存配置。
   *
   * @return 灰度路由 CacheConfig
   */
  private YdszCacheProperties.CacheConfig buildGrayCfg() {
    YdszCacheProperties.CacheConfig cfg = new YdszCacheProperties.CacheConfig();
    cfg.setType(CacheType.TINYLFU);
    cfg.setMaximumSize(grayServiceListMaxSize);
    cfg.setExpireAfterWrite(grayServiceListExpireMinutes);
    cfg.setExpireTimeUnit(TimeUnit.MINUTES);
    cfg.setRecordStats(Boolean.TRUE);
    return cfg;
  }

  /**
   * 本地令牌桶限流缓存 Bean。
   *
   * @param manager 网关缓存管理器
   * @return 本地令牌桶缓存
   */
  @Bean
  public SpringYdszCache rateLimitCache(YdszCacheManager manager) {
    return manager.getCache(CACHE_RATE_LIMIT);
  }

  /**
   * IP 黑名单 L1 缓存 Bean。
   *
   * @param manager 网关缓存管理器
   * @return IP 黑名单缓存
   */
  @Bean
  public SpringYdszCache ipBlacklistCache(YdszCacheManager manager) {
    return manager.getCache(CACHE_IP_BLACKLIST);
  }

  /**
   * JWT 验证结果缓存 Bean。
   *
   * @param manager 网关缓存管理器
   * @return JWT 验证结果缓存
   */
  @Bean
  public SpringYdszCache jwtValidationCache(YdszCacheManager manager) {
    return manager.getCache(CACHE_JWT_VALIDATION);
  }

  /**
   * 灰度路由服务列表缓存 Bean。
   *
   * @param manager 网关缓存管理器
   * @return 灰度路由服务列表缓存
   */
  @Bean
  public SpringYdszCache grayServiceListCache(YdszCacheManager manager) {
    return manager.getCache(CACHE_GRAY_SERVICE_LIST);
  }

  // validateCaches 已移除：避免在 PostConstruct 阶段造成循环引用
}
