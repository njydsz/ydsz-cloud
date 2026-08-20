package com.njydsz.workflow.app.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.web.health.AbstractModuleHealthIndicator;

/**
 * 工作流 App 端健康检查指示器。
 *
 * <p><b>架构合规说明（v2.23 DDD 分层规范）：</b>App 端独立健康检查入口（符合 §34.2.5）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@ConditionalOnClass(HealthIndicator.class)
public class WorkflowAppHealthIndicator extends AbstractModuleHealthIndicator {

  @Override
  protected void doHealthCheck(Health.Builder builder) {
    // App 端健康检查逻辑（预留）
  }
}
