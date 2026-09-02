package com.njydsz.common.cache.health;

import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.cache.spring.YdszCacheManager;

/**
 * Spring Boot Actuator HealthIndicator 适配器 — 对接 {@link CacheHealthIndicator}
 *
 * <p>自动注册 {@link YdszCacheManager} 中的所有缓存到 {@link CacheHealthIndicator}， 并通过 Spring Boot Actuator
 * 暴露缓存健康状态。
 *
 * <p>状态映射：
 *
 * <ul>
 *   <li>UP → Health.up()
 *   <li>WARN → Health.up() with warning detail
 *   <li>DOWN → Health.down()
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class SpringCacheHealthIndicator implements HealthIndicator {

  private final CacheHealthIndicator delegate;
  private final YdszCacheManager cacheManager;

  /**
   * 创建 Spring Boot HealthIndicator 适配器
   *
   * @param cacheManager YdszCacheManager（用于自动注册缓存）
   */
  public SpringCacheHealthIndicator(YdszCacheManager cacheManager) {
    this.cacheManager = cacheManager;
    this.delegate = new CacheHealthIndicator();
    // 自动注册所有已知缓存
    for (String cacheName : cacheManager.getCacheNames()) {
      var springCache = cacheManager.getCache(cacheName);
      if (springCache != null) {
        delegate.registerCache(cacheName, springCache.getNativeCache());
      }
    }
  }

  /**
   * 获取缓存健康状态并转换为 Actuator Health。
   *
   * <p>状态映射约定：UP 与 WARN 均映射为 {@code Health.up()}（WARN 仅通过 withDetail 附带 warning 字段告警），DOWN 映射为
   * {@code Health.down()}， 其余未知状态映射为 unknown。
   *
   * @return Actuator Health 对象，含全部缓存明细信息
   */
  @Override
  public Health health() {
    CacheHealthIndicator.HealthResult result = delegate.health();
    Health.Builder healthBuilder;

    switch (result.getStatus()) {
      case UP:
        healthBuilder = Health.up();
        break;
      case WARN:
        healthBuilder = Health.up();
        break;
      case DOWN:
        healthBuilder = Health.down();
        break;
      default:
        healthBuilder = Health.unknown();
        break;
    }

    for (Map.Entry<String, Object> entry : result.getDetails().entrySet()) {
      healthBuilder.withDetail(entry.getKey(), entry.getValue());
    }

    return healthBuilder.build();
  }

  /**
   * 获取底层 CacheHealthIndicator（用于手动注册额外缓存）
   *
   * @return 被委派的 {@link CacheHealthIndicator} 实例，不会为 {@code null}；
   *     通过它注册的缓存会一并纳入 {@link #health()} 的明细输出
   */
  public CacheHealthIndicator getDelegate() {
    return delegate;
  }

  /**
   * 动态注册新创建的缓存到健康检查
   *
   * @param cacheName 缓存名称
   */
  public void registerCache(String cacheName) {
    var springCache = cacheManager.getCache(cacheName);
    if (springCache != null) {
      delegate.registerCache(cacheName, springCache.getNativeCache());
    }
  }
}
