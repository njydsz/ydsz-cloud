package com.njydsz.pmis.common.safe.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 安全响应头配置属性
 *
 * <p>用于配置 HTTP 响应的安全相关头部，防止常见 Web 安全威胁。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     security-headers:
 *       enabled: true
 *       order: 1
 *       xss-protection: "1; mode=block"
 *       content-type-options: "nosniff"
 *       frame-options: "SAMEORIGIN"
 *       hsts: "max-age=31536000; includeSubDomains"
 *       csp: "default-src 'self'"
 *       excludes:
 *         - /error
 *         - /actuator/**
 * }</pre>
 *
 * <p><b>头部说明：</b>
 * <ul>
 *   <li>X-XSS-Protection：启用浏览器 XSS 过滤器</li>
 *   <li>X-Content-Type-Options：防止 MIME 类型嗅探</li>
 *   <li>X-Frame-Options：防止点击劫持攻击</li>
 *   <li>Strict-Transport-Security：强制 HTTPS 连接</li>
 *   <li>Content-Security-Policy：内容安全策略</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.security-headers")
public class SecurityHeaderProperties {

    /**
     * 是否启用安全响应头
     *
     * <p>默认值为 true，即启用所有配置的安全响应头。
     */
    private boolean enabled = true;

    /**
     * 过滤器注册顺序
     *
     * <p>数值越小，优先级越高。
     * 建议在 XssFilter 之前执行，以便尽早设置安全头部。
     */
    private int order = 1;

    /**
     * XSS 防护头部
     *
     * <p>启用浏览器的 XSS 过滤器。
     * 推荐值："1; mode=block" - 发现 XSS 时阻止页面渲染
     * 旧版值："0" - 禁用 XSS 过滤器（不推荐）
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
     *   <li>ALLOW-FROM uri：允许指定 URI 嵌入（已废弃）</li>
     * </ul>
     */
    private String frameOptions = "SAMEORIGIN";

    /**
     * 严格传输安全头部
     *
     * <p>强制浏览器使用 HTTPS 连接。
     * <ul>
     *   <li>max-age：HSTS 缓存时间（秒）</li>
     *   <li>includeSubDomains：应用于所有子域名</li>
     *   <li>preload：支持 HSTS preload 列表</li>
     * </ul>
     */
    private String hsts = "max-age=31536000; includeSubDomains";

    /**
     * 内容安全策略头部
     *
     * <p>防止 XSS、数据注入等攻击，限制资源加载来源。
     * <p>常用配置示例：
     * <ul>
     *   <li>"default-src 'self'"：仅允许同源资源</li>
     *   <li>"default-src 'self'; script-src 'self' 'unsafe-inline'"：允许内联脚本</li>
     *   <li>"default-src 'self'; img-src 'self' data:; font-src 'self'"：自定义资源策略</li>
     * </ul>
     */
    private String csp = "default-src 'self'";

    /**
     * 引用策略头部
     *
     * <p>控制 Referer 头的发送策略。
     * <ul>
     *   <li>"no-referrer"：不发送 Referer</li>
     *   <li>"same-origin"：仅同源请求发送 Referer</li>
     *   <li>"strict-origin-when-cross-origin"：跨域时仅发送协议+主机</li>
     * </ul>
     */
    private String referrerPolicy = "strict-origin-when-cross-origin";

    /**
     * 权限策略头部
     *
     * <p>控制浏览器功能 API 的使用权限。
     * 例如：geolocation、microphone、camera 等
     */
    private String permissionsPolicy = "geolocation=(), microphone=(), camera=()";

    /**
     * 排除路径列表
     *
     * <p>这些路径的响应不会添加安全头部，支持 Ant 风格路径匹配。
     * 通常用于静态资源、文件下载等不需要安全头部的端点。
     */
    private List<String> excludes = new ArrayList<>();
}
