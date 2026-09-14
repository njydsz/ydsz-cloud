package com.njydsz.nextwiki.app.health;

import org.springframework.boot.health.contributor.Health;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;
import com.njydsz.nextwiki.app.config.NextwikiAppProperties;

/**
 * 知识库模块 App 端健康检查指示器。
 *
 * <p>由 {@code NextwikiAppAutoConfiguration} 在 APP 平台（{@code @ConditionalOnPlatform(PlatformMode.APP)}）
 * 下统一装配，仅保留唯一一个 {@code NextwikiAppHealthIndicator} Bean（P2-1 整改，
 * 见《云顶编码规范》§33 公共能力复用规范）。
 *
 * <p>继承 common-web 统一基类 {@link AbstractModuleHealthIndicator}（P1-5 整改：由
 * {@code implements HealthIndicator} 样板改为模板方法复用）。
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
 * @since 26.09.14 改继承 AbstractModuleHealthIndicator，消除样板（P1-5 整改）
 */
public class NextwikiAppHealthIndicator extends AbstractModuleHealthIndicator {

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
   * 执行健康检查（模板方法）。
   *
   * <p>模块被禁用时调用 {@code builder.down()} 并给出原因；否则标记 UP 并携带分页配置。
   *
   * @param builder 健康状态构建器
   */
  @Override
  protected void doHealthCheck(Health.Builder builder) {
    if (!appProperties.isEnabled()) {
      builder.down().withDetail("reason", "module disabled");
      return;
    }
    builder.up();
    builder.withDetail("module", "nextwiki");
    builder.withDetail("platform", "app");
    builder.withDetail("defaultPageSize", appProperties.getDefaultPageSize());
    builder.withDetail("maxPageSize", appProperties.getMaxPageSize());
  }
}
