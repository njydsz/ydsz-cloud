package com.njydsz.cronjob.app.health;

import org.springframework.boot.health.contributor.Health;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;

/**
 * 定时任务模块 App 端健康检查指示器。
 *
 * <p>继承 common-web 统一基类 {@link AbstractModuleHealthIndicator}（P1-5 整改：由
 * {@code implements HealthIndicator} 样板改为模板方法复用）。
 *
 * <p>Spring Boot 4.x 已将 Health/HealthIndicator 从 actuator 拆分至 spring-boot-health 模块
 * （包 {@code org.springframework.boot.health.contributor}），依赖由 ydsz-cronjob-app/pom.xml 的 spring-boot-health 提供。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.14 改继承 AbstractModuleHealthIndicator，消除样板（P1-5 整改）
 */
public class CronjobAppHealthIndicator extends AbstractModuleHealthIndicator {

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    builder.up();
    builder.withDetail("module", "cronjob");
    builder.withDetail("platform", "app");
  }
}
