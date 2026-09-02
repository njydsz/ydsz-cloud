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
 * <p><b>职责：</b>封装移动端（APP）专用的 OpenAPI 文档配置，与 PC 端 Web API 文档隔离。
 *
 * <p><b>激活条件：</b>
 *
 * <ul>
 *   <li>平台模式为 {@link PlatformMode#APP}（{@code ydsz.platform.mode=app}）
 *   <li>{@code ydsz.userinfo.enabled=true}（默认激活）
 * </ul>
 *
 * <p><b>设计说明：</b>独立模块而非放在 server 层的原因：
 *
 * <ul>
 *   <li>APP 端可能引入额外的 SDK 或过滤器（如设备指纹、App 版本校验），独立模块便于扩展
 *   <li>与 PC 端 Swagger 分组互斥，避免 APP 启动时加载不需要的 Bean
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.userinfo", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({UserInfoAppOpenApiConfiguration.class})
public class UserInfoAppAutoConfiguration {
}
