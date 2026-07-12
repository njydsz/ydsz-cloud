package com.njydsz.pmis.common.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API 分组配置（P2-5）
 *
 * <p>在基础 {@link OpenApiConfig} 之上，为 Knife4j UI 提供 API 分组能力：
 * <ul>
 *   <li>{@code controller} 分组：业务接口（/api/**）</li>
 *   <li>{@code actuator} 分组：运维端点（/actuator/**）</li>
 * </ul>
 *
 * <p>Knife4j UI 访问地址：{@code /doc.html}
 *
 * <p>启用条件：classpath 存在 springdoc 类且 {@code pmis.openapi.enabled=true}
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Configuration
@ConditionalOnClass(name = "org.springdoc.core.models.GroupedOpenApi")
@ConditionalOnProperty(prefix = "pmis.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Knife4jConfig {

    /**
     * API 分组：Controller 层业务接口
     *
     * <p>扫描所有 {@code com.njydsz.pmis} 包下的接口，
     * 匹配路径 {@code /api/**}。
     *
     * @return API 分组
     */
    @Bean
    public GroupedOpenApi controllerGroup() {
        return GroupedOpenApi.builder()
                .group("controller")
                .packagesToScan("com.njydsz.pmis")
                .pathsToMatch("/api/**")
                .build();
    }

    /**
     * API 分组：Actuator 运维端点
     *
     * <p>独立分组暴露 Actuator 端点文档，便于运维人员查阅。
     *
     * @return API 分组
     */
    @Bean
    public GroupedOpenApi actuatorGroup() {
        return GroupedOpenApi.builder()
                .group("actuator")
                .pathsToMatch("/actuator/**")
                .build();
    }
}
