package com.njydsz.pmis.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * PMIS OpenAPI 3.0 自动生成配置（批次 19 P2-1 落地）
 *
 * <p>基于 springdoc-openapi 2.6.0，在每个微服务启动时自动暴露：
 * <ul>
 *   <li>{@code /v3/api-docs}：OpenAPI 3.0 JSON 规范</li>
 *   <li>{@code /v3/api-docs.yaml}：YAML 规范</li>
 *   <li>{@code /swagger-ui.html}：交互式 API 文档</li>
 * </ul>
 *
 * <p>网关层可在 ydsz-pmis-gateway 聚合 14 个微服务的 /v3/api-docs 端点，
 * 通过 springdoc-openapi 的 GroupedOpenApi + spring-cloud-gateway 路由暴露统一门户。
 *
 * <p>启用条件：{@code pmis.openapi.enabled=true}（默认 true）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "pmis.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    /** 当前微服务应用名 */
    @Value("${spring.application.name:pmis-app}")
    private String applicationName;

    /** OpenAPI 规范版本号 */
    @Value("${pmis.openapi.version:1.0.0}")
    private String apiVersion;

    /** API 网关地址，用于 OpenAPI Server 声明 */
    @Value("${pmis.openapi.gateway-url:http://localhost:9000}")
    private String gatewayUrl;

    /**
     * 构建 PMIS OpenAPI 3.0 规范
     *
     * @return OpenAPI 实例
     */
    @Bean
    public OpenAPI pmisOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PMIS " + applicationName + " API")
                        .description("南京云顶 PMIS 系统 " + applicationName + " 模块 OpenAPI 3.0 规范")
                        .version(apiVersion)
                        .contact(new Contact()
                                .name("PMIS API Team")
                                .email("api-team@ydsz-pmis.cn")
                                .url("https://github.com/ydsz-pmis"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://ydsz-pmis.cn/license")))
                .servers(List.of(
                        new Server().url(gatewayUrl).description("API Gateway（推荐）"),
                        new Server().url("http://localhost:8080").description("Local Direct")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 令牌，Authorization: Bearer {token}")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
