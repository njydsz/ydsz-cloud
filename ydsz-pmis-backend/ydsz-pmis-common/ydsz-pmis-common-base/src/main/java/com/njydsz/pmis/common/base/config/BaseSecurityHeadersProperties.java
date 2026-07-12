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
 * remi:
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
 * <p><b>与 safe 模块的关系：</b>
 * 当项目中同时存在 safe/web/app 模块时，本配置不会被使用，
 * 安全响应头由 safe 模块的 {@code SecurityHeaderProperties}（前缀 {@code remi.safe.security-headers}）统一管理。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
@Data
@ConfigurationProperties(prefix = "remi.base.security-headers")
public class BaseSecurityHeadersProperties {

    /**
     * 是否启用安全响应头
     *
     * <p>默认值为 true，即启用所有配置的安全响应头。
     */
    private boolean enabled = true;

    /**
     * XSS 防护头部
     *
     * <p>启用浏览器的 XSS 过滤器。
     * 推荐值："1; mode=block" - 发现 XSS 时阻止页面渲染
     */
    private String xssProtection = "1; mode=block";

    /**
     * 内容类型选项头部
     *
     * <p>防止浏览器 MIME 类型嗅探。
     * 推荐值："nosniff" - 不猜测内容类型
     */
    private String contentTypeOptions = "nosniff";

    /**
     * 帧选项头部
     *
     * <p>防止页面被嵌入到 iframe 中，防止点击劫持攻击。
     * <ul>
     *   <li>DENY：禁止所有页面嵌入</li>
     *   <li>SAMEORIGIN：仅允许同源页面嵌入</li>
     * </ul>
     */
    private String frameOptions = "DENY";

    /**
     * 严格传输安全头部
     *
     * <p>强制浏览器使用 HTTPS 连接。
     */
    private String hsts = "max-age=31536000; includeSubDomains";

    /**
     * 内容安全策略头部
     *
     * <p>防止 XSS、数据注入等攻击，限制资源加载来源。
     */
    private String csp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'";

    /**
     * 引用策略头部
     *
     * <p>控制 Referer 头的发送策略。
     */
    private String referrerPolicy = "strict-origin-when-cross-origin";

    /**
     * 排除路径列表
     *
     * <p>这些路径的响应不会添加安全头部，支持 Ant 风格路径匹配。
     * 通常用于静态资源、文件下载等不需要安全头部的端点。
     */
    private List<String> excludes = new ArrayList<>();
}
