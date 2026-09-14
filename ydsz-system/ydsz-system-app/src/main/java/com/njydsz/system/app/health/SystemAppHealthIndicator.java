package com.njydsz.system.app.health;

import org.springframework.boot.health.contributor.Health;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;

/**
 * 系统管理模块 App 端健康检查指示器。
 *
 * <p>继承 common-web 统一基类 {@link AbstractModuleHealthIndicator}（P1-5 整改：由
 * {@code implements HealthIndicator} 样板改为模板方法复用）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.14 改继承 AbstractModuleHealthIndicator，消除样板（P1-5 整改）
 */
public class SystemAppHealthIndicator extends AbstractModuleHealthIndicator {

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    builder.up();
    builder.withDetail("module", "system");
    builder.withDetail("platform", "app");
  }
}
