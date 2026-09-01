package com.njydsz.message.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.message.app.health.MessageAppHealthIndicator;
import com.njydsz.message.app.openapi.MessageAppOpenApiConfiguration;

/**
 * 消息中心模块 App 端自动配置。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.message", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({MessageAppHealthIndicator.class, MessageAppOpenApiConfiguration.class})
public class MessageAppAutoConfiguration {
}
