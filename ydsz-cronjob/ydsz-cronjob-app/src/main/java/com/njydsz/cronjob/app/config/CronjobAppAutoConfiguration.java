package com.njydsz.cronjob.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.cronjob.app.health.CronjobAppHealthIndicator;
import com.njydsz.cronjob.app.openapi.CronjobAppOpenApiConfiguration;

/**
 * 定时任务模块 App 端自动配置。
 *
 * <p>仅在 {@code ydzz.platform.mode=app} 时生效，注册 App 端健康检查、OpenApi 配置等。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.cronjob", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({CronjobAppHealthIndicator.class, CronjobAppOpenApiConfiguration.class})
public class CronjobAppAutoConfiguration {
}
