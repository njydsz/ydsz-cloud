package com.njydsz.nextwiki.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.nextwiki.app.config.NextwikiAppProperties;

/**
 * 知识库模块 App 端健康检查指示器。
 *
 * <p>由 {@link com.njydsz.nextwiki.app.config.NextwikiAppAutoConfiguration} 在 APP 平台
 * （{@code @ConditionalOnPlatform(PlatformMode.APP)}）下统一装配，仅保留唯一一个
 * {@code NextwikiAppHealthIndicator} Bean，消除与 {@code app.config} 包下历史占位实现
 * 的同名重复（P2-1 整改，见《云顶编码规范》§33 公共能力复用规范）。
 *
 * <p><b>检查项：</b>
 *
 * <ul>
 *   <li>{@code nextwiki.app.enabled=false} 时返回 {@code DOWN}，明确暴露"模块被禁用"而非误报健康
 *   <li>启用时返回 {@code UP}，并携带分页配置（defaultPageSize / maxPageSize）便于运维核对
 * </ul>
 *
 * <p><b>线程安全：</b>本类无状态，仅读取不可变配置属性，线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NextwikiAppHealthIndicator implements HealthIndicator {

  /** details 集合初始容量（模块标识 + 平台标识 + 分页配置共 4 项，预留余量取 16） */
  private static final int COLLECTION_CAPACITY = 16;

  private final NextwikiAppProperties appProperties;

  /**
   * 构造 App 端健康检查指示器。
   *
   * @param appProperties 移动端模块配置属性，不可为 null
   */
  public NextwikiAppHealthIndicator(NextwikiAppProperties appProperties) {
    this.appProperties = appProperties;
  }

  /**
   * 执行健康检查。
   *
   * @return 移动端模块被禁用时返回 DOWN（reason=module disabled）；否则返回 UP 并携带模块标识、平台标识与分页配置，不会返回 null
   */
  @Override
  public Health health() {
    if (!appProperties.isEnabled()) {
      return Health.down().withDetail("reason", "module disabled").build();
    }
    Map<String, Object> details = new LinkedHashMap<>(COLLECTION_CAPACITY);
    details.put("module", "nextwiki");
    details.put("platform", "app");
    details.put("defaultPageSize", appProperties.getDefaultPageSize());
    details.put("maxPageSize", appProperties.getMaxPageSize());
    return Health.up().withDetails(details).build();
  }
}
