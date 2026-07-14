package com.njydsz.pmis.common.base.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import com.njydsz.pmis.common.core.constant.HeaderConstants;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI 文档配置基类（Web/App 共享）
 *
 * <p>基于 springdoc-openapi 生成 Swagger 3.0 规范的 API 文档。
 * 子类覆盖 {@link #getTitle()}、{@link #getDescription()} 提供不同的文档标题和描述。
 *
 * <p><b>默认行为：</b>
 * <ul>
 *   <li>注册所有公共请求头（X-User-Id、X-Tenant-Id、X-Access-Token 等）</li>
 *   <li>注册 JWT Bearer Token 认证方案</li>
 *   <li>设置统一的联系信息和外部文档链接</li>
 * </ul>
 *
 * <p><b>激活条件：</b>需要通过配置 {@code ydsz.doc.enabled=true} 显式开启。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 3.5.0
 */
public abstract class BaseOpenApiConfiguration {

    /**
     * API 文档标题
     *
     * <p>子类必须覆盖以提供具体业务系统的名称，例如 "REMI 管理系统 API"。
     *
     * @return API 文档标题
     */
    protected abstract String getTitle();

    /**
     * API 文档描述
     *
     * <p>子类必须覆盖以提供具体业务系统的简要说明。
     *
     * @return API 文档描述
     */
    protected abstract String getDescription();

    /**
     * 构建 OpenAPI 文档
     *
     * <p>整合 Info、Components（Headers + SecuritySchemes）、ExternalDocs、SecurityRequirements。
     *
     * @return OpenAPI 文档实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "ydsz.doc", name = "enabled", havingValue = "true", matchIfMissing = false)
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(createInfo())
                .components(createComponents())
                .externalDocs(createExternalDocs())
                .security(createSecurityRequirements());
    }

    /**
     * 构建 API 基本信息（标题、描述、版本、联系信息）
     */
    private Info createInfo() {
        return new Info()
                .title(getTitle())
                .description(getDescription())
                .version("3.5.0")
                .contact(new Contact()
                        .name("Marvin Lee")
                        .email("1.3.0")
                        .url("https://njydsz.pmis.com.cn"));
    }

    /**
     * 构建 OpenAPI 组件（请求头、安全方案）
     */
    private Components createComponents() {
        return new Components()
                .headers(createHeaderParams())
                .securitySchemes(createSecuritySchemes());
    }

    /**
     * 构建请求头参数映射（用于文档展示）
     */
    private Map<String, Header> createHeaderParams() {
        Map<String, Header> headers = new LinkedHashMap<>();

        headers.put(HeaderConstants.X_SERVICE_TYPE, createHeader("服务类型", false));
        headers.put(HeaderConstants.X_USER_LANGUAGE, createHeader("用户系统语言", false));
        headers.put(HeaderConstants.X_UNIQUE_ID, createHeader("用户唯一ID", false));
        headers.put(HeaderConstants.X_ACCESS_TOKEN, createHeader("用户鉴权Token", false));
        headers.put(HeaderConstants.X_DISTINCT_ID, createHeader("设备唯一标识", false));
        headers.put(HeaderConstants.X_DATA_SCOPE, createHeader("数据权限范围类型", false));
        headers.put(HeaderConstants.X_TENANT_ID, createHeader("租户ID", false));
        headers.put(HeaderConstants.X_COMPANY_IDS, createHeader("公司ID集合", false));
        headers.put(HeaderConstants.X_DEPT_IDS, createHeader("部门ID集合", false));
        headers.put(HeaderConstants.X_PROJECT_IDS, createHeader("项目ID集合", false));
        headers.put(HeaderConstants.X_REGION_IDS, createHeader("区域ID集合", false));
        headers.put(HeaderConstants.X_VISIBLE_COLUMNS, createHeader("列可见规则", false));
        headers.put(HeaderConstants.X_EDITABLE_COLUMNS, createHeader("列可编辑规则", false));
        headers.put(HeaderConstants.X_REQUEST_SOURCE, createHeader("请求来源标识", false));
        headers.put(HeaderConstants.X_FORWARDED_FOR, createHeader("请求来源IP", false));

        return headers;
    }

    /**
     * 构建安全方案映射
     */
    private Map<String, SecurityScheme> createSecuritySchemes() {
        Map<String, SecurityScheme> schemes = new LinkedHashMap<>();
        schemes.put("Bearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Bearer Token 认证"));
        return schemes;
    }

    /**
     * 构建全局安全要求
     */
    private List<SecurityRequirement> createSecurityRequirements() {
        return List.of(new SecurityRequirement().addList("Bearer"));
    }

    /**
     * 构建外部文档引用
     */
    private ExternalDocumentation createExternalDocs() {
        return new ExternalDocumentation()
                .description("REMI 公共框架文档")
                .url("https://njydsz.pmis.com.cn");
    }

    /**
     * 创建 OpenAPI Header 描述对象
     *
     * @param description 描述
     * @param required    是否必填
     * @return Header 实例
     */
    private Header createHeader(String description, boolean required) {
        Header header = new Header();
        header.setDescription(description);
        header.setRequired(required);
        return header;
    }
}
