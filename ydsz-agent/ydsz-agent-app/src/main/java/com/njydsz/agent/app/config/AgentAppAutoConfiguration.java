package com.njydsz.agent.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

import com.njydsz.agent.app.health.AgentAppHealthIndicator;
import com.njydsz.agent.app.openapi.AgentAppOpenApiConfiguration;
import com.njydsz.common.base.config.ConditionalOnPlatform;
import com.njydsz.common.base.config.PlatformMode;

/**
 * Agent 模块 App 端自动配置。
 *
 * <p>仅在 {@code ydzz.platform.mode=app} 时生效（由 {@link ConditionalOnPlatform} 控制），
 * 注册 App 端健康检查、OpenAPI 配置等模块特有 Bean。
 *
 * <p>可通过 {@code ydsz.agent.enabled=false} 禁用整个 Agent 模块的 App 端装配。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@AutoConfiguration
@ConditionalOnPlatform(PlatformMode.APP)
@ConditionalOnProperty(prefix = "ydsz.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({AgentAppHealthIndicator.class, AgentAppOpenApiConfiguration.class})
public class AgentAppAutoConfiguration {
}
