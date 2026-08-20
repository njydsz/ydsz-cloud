package com.njydsz.userinfo.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.context.annotation.Import;

import com.njydsz.userinfo.app.health.AppHealthIndicator;
import com.njydsz.userinfo.app.openapi.AppOpenApiConfiguration;

/**
 * App 端自动配置聚合（P1-2 双入口架构）。
 *
 * <p>仅在 {@code ydsz.userinfo.platform=app} 时激活，聚合 App 端专属组件：
 *
 * <ul>
 *   <li>{@link AppHealthIndicator} — App 端健康检查指标</li>
 *   <li>{@link AppOpenApiConfiguration} — App 端 OpenAPI 文档配置</li>
 * </ul>
 *
 * <p>通过 {@link ConditionalOnPlatform} 注解控制激活条件，与 Web 端组件互斥。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@AutoConfiguration
@ConditionalOnPlatform("app")
@AutoConfigureOrder(200)
@Import({
    AppHealthIndicator.class,
    AppOpenApiConfiguration.class
})
public class AppAutoConfiguration {
  // 聚合配置入口，组件通过 @Import 注册
}
