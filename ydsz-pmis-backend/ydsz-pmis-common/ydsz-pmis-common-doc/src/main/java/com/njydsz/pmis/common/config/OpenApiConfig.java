package com.njydsz.pmis.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * PMIS OpenAPI 3.0 自动生成配置（P2-5 增强）
 *
 * <p>基于 springdoc-openapi + Knife4j，在每个微服务启动时自动暴露：
 * <ul>
 *   <li>{@code /v3/api-docs}：OpenAPI 3.0 JSON 规范</li>
 *   <li>{@code /v3/api-docs.yaml}：YAML 规范</li>
 *   <li>{@code /swagger-ui.html}：交互式 API 文档</li>
 *   <li>{@code /doc.html}：Knife4j 增强文档 UI</li>
 * </ul>
 *
 * <p>增强点（P2-5）：
 * <ul>
 *   <li>Bearer JWT + API Key 双安全方案</li>
 *   <li>全局响应头声明（traceId）</li>
 *   <li>外部文档链接（Wiki / Confluence）</li>
 *   <li>生产环境自动关闭（knife4j.production=true）</li>
 * </ul>
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

    /** 外部文档地址（Wiki / Confluence） */
    @Value("${pmis.openapi.external-docs-url:}")
    private String externalDocsUrl;

    /**
     * 构建 PMIS OpenAPI 3.0 规范
     *
     * <p>包含：
     * <ul>
     *   <li>API 元信息（标题、描述、版本、联系方式、许可证）</li>
     *   <li>服务器列表（网关地址 + 本地直连）</li>
     *   <li>安全方案：Bearer JWT（用户认证）+ API Key（服务间调用）</li>
     *   <li>全局响应头：traceId</li>
     *   <li>外部文档链接（可选）</li>
     * </ul>
     *
     * @return OpenAPI 实例
     */
    @Bean
    public OpenAPI pmisOpenApi() {
        OpenAPI openAPI = new OpenAPI()
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
                                .description("JWT 令牌，Authorization: Bearer {token}"))
                        .addSecuritySchemes("apiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API Key 认证（服务间调用）"))
                        .addHeaders("traceId", new Header()
                                .description("链路追踪 ID")
                                .schema(new StringSchema())))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));

        // 外部文档链接（可选）
        if (externalDocsUrl != null && !externalDocsUrl.isBlank()) {
            openAPI.externalDocs(new ExternalDocumentation()
                    .description("PMIS 系统设计文档")
                    .url(externalDocsUrl));
        }

        return openAPI;
    }
}
