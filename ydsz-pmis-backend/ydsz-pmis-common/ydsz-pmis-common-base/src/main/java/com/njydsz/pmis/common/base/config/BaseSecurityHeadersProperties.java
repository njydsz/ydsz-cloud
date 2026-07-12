package com.njydsz.pmis.common.base.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Base 模块安全响应头配置属性
 *
 * <p>用于配置 HTTP 响应的安全相关头部，防止常见 Web 安全威胁。
 * 仅在业务方直接使用 base 模块（未引入 web/app/safe 模块）时生效。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * pmis:
 *   base:
 *     security-headers:
 *       enabled: true
 *       xss-protection: "1; mode=block"
 *       content-type-options: "nosniff"
 *       frame-options: "DENY"
 *       hsts: "max-age=31536000; includeSubDomains"
 *       csp: "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"
 *       referrer-policy: "strict-origin-when-cross-origin"
 *       excludes:
 *         - /error
 *         - /actuator/**
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "pmis.base.security-headers")
public class BaseSecurityHeadersProperties {

    /**
     * 是否启用安全响应头
     */
    private boolean enabled = true;

    /**
     * XSS 防护头部
     */
    private String xssProtection = "1; mode=block";

    /**
     * 内容类型选项头部
     */
    private String contentTypeOptions = "nosniff";

    /**
     * 帧选项头部
     */
    private String frameOptions = "DENY";

    /**
     * 严格传输安全头部
     */
    private String hsts = "max-age=31536000; includeSubDomains";

    /**
     * 内容安全策略头部
     */
    private String csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'";

    /**
     * 引用策略头部
     */
    private String referrerPolicy = "strict-origin-when-cross-origin";

    /**
     * 排除路径列表
     */
    private List<String> excludes = new ArrayList<>();
}
