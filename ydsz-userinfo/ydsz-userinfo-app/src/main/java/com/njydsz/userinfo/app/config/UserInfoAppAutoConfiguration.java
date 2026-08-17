package com.njydsz.userinfo.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.userinfo.app.health.UserInfoAppHealthIndicator;
import com.njydsz.userinfo.app.openapi.UserInfoAppOpenApiConfiguration;

/**
 * 用户信息模块 App 端自动配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.userinfo", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({UserInfoAppHealthIndicator.class, UserInfoAppOpenApiConfiguration.class})
public class UserInfoAppAutoConfiguration {
}
