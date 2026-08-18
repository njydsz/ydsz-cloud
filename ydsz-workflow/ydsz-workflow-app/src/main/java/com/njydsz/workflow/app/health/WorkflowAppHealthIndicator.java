package com.njydsz.workflow.app.health;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

/**
 * 工作流引擎模块 App 端健康检查指示器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WorkflowAppHealthIndicator extends AbstractHealthIndicator {

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("module", "workflow");
    details.put("platform", "app");
    builder.up().withDetails(details);
  }
}
