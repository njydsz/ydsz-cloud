package com.njydsz.pmis.common.base.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 文档配置基类（Web/App 共享）
 *
 * <p>基于 springdoc-openapi 生成 Swagger 3.0 规范的 API 文档。
 * 子类覆盖 {@link #getTitle()}、{@link #getDescription()} 提供不同的文档标题和描述。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public abstract class BaseOpenApiConfiguration {

    /**
     * API 文档标题
     *
     * @return API 文档标题
     */
    protected abstract String getTitle();

    /**
     * API 文档描述
     *
     * @return API 文档描述
     */
    protected abstract String getDescription();

    /**
     * 构建 OpenAPI 文档
     *
     * @return OpenAPI 文档实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "pmis.doc", name = "enabled", havingValue = "true", matchIfMissing = false)
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(createInfo())
                .components(createComponents())
                .externalDocs(createExternalDocs())
                .security(createSecurityRequirements());
    }

    private Info createInfo() {
        return new Info()
                .title(getTitle())
                .description(getDescription())
                .version("1.0.0")
                .contact(new Contact()
                        .name("ydsz-pmis-team")
                        .url("https://njydsz.com"));
    }

    private Components createComponents() {
        return new Components()
                .headers(createHeaderParams())
                .securitySchemes(createSecuritySchemes());
    }

    private Map<String, Header> createHeaderParams() {
        Map<String, Header> headers = new LinkedHashMap<>();
        headers.put("X-Access-Token", createHeader("用户鉴权Token", false));
        headers.put("X-Tenant-Id", createHeader("租户ID", false));
        headers.put("X-User-Id", createHeader("用户ID", false));
        headers.put("X-Request-Id", createHeader("请求ID", false));
        return headers;
    }

    private Map<String, SecurityScheme> createSecuritySchemes() {
        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put("Bearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer Token 认证"));
        return schemes;
    }

    private List<SecurityRequirement> createSecurityRequirements() {
        return List.of(new SecurityRequirement().addList("Bearer"));
    }

    private ExternalDocumentation createExternalDocs() {
        return new ExternalDocumentation()
                .description("PMIS 公共框架文档")
                .url("https://njydsz.com");
    }

    private Header createHeader(String description, boolean required) {
        Header header = new Header();
        header.setDescription(description);
        header.setRequired(required);
        return header;
    }
}
