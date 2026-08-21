package com.njydsz.system.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.system.app.health.SystemAppHealthIndicator;
import com.njydsz.system.app.openapi.SystemAppOpenApiConfiguration;

/**
 * 系统管理模块 App 端自动配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.system", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({SystemAppHealthIndicator.class, SystemAppOpenApiConfiguration.class})
public class SystemAppAutoConfiguration {
}
