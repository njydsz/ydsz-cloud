package com.njydsz.workflow.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.workflow.app.health.WorkflowAppHealthIndicator;
import com.njydsz.workflow.app.openapi.WorkflowAppOpenApiConfiguration;

/**
 * 工作流引擎模块 App 端自动配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({WorkflowAppHealthIndicator.class, WorkflowAppOpenApiConfiguration.class})
public class WorkflowAppAutoConfiguration {
}
