package com.njydsz.literule.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.literule.app.health.LiteRuleAppHealthIndicator;
import com.njydsz.literule.app.openapi.LiteRuleAppOpenApiConfiguration;

/**
 * 规则引擎模块 App 端自动配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.literule", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({LiteRuleAppHealthIndicator.class, LiteRuleAppOpenApiConfiguration.class})
public class LiteRuleAppAutoConfiguration {
}
