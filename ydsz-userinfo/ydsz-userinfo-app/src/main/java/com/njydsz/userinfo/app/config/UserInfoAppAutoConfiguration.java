package com.njydsz.userinfo.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;
import com.njydsz.userinfo.app.openapi.UserInfoAppOpenApiConfiguration;

/**
 * 用户信息模块 App 端自动配置。
 *
 * <p>P2-1: 移除空壳的 {@code UserInfoAppHealthIndicator}（原实现返回写死的 UP，无真实健康探测；
 * 模块真实健康检查由 server 层 {@code UserInfoHealthIndicator} 提供）。此处仅保留移动端 OpenAPI 文档配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.userinfo", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({UserInfoAppOpenApiConfiguration.class})
public class UserInfoAppAutoConfiguration {
}
