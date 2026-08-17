package com.njydsz.nextwiki.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.nextwiki.app.health.NextwikiAppHealthIndicator;
import com.njydsz.nextwiki.app.openapi.NextwikiAppOpenApiConfiguration;

/**
 * 知识库模块 App 端自动配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.nextwiki", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({NextwikiAppHealthIndicator.class, NextwikiAppOpenApiConfiguration.class})
public class NextwikiAppAutoConfiguration {
}
