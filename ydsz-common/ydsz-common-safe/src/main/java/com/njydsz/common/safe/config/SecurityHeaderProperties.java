package com.njydsz.common.safe.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
 * @author ydsz-team
 * @since 1.0.0
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

    // ============================== 子配置类（P1-1：与 Gateway SecurityHeadersProperties 对齐） ==============================

    /** CSP (Content-Security-Policy) 配置 */
    private CspConfig csp = new CspConfig();

    /** HSTS (Strict-Transport-Security) 配置 */
    private HstsConfig hsts = new HstsConfig();

    /** COOP (Cross-Origin-Opener-Policy) 配置 */
    private CoopConfig coop = new CoopConfig();

    /** COEP (Cross-Origin-Embedder-Policy) 配置 */
    private CoepConfig coep = new CoepConfig();

    /** CORP (Cross-Origin-Resource-Policy) 配置 */
    private CorpConfig corp = new CorpConfig();

    /**
     * 排除路径列表
     *
     * <p>这些路径的响应不会添加安全头部，支持 Ant 风格路径匹配。
     * 通常用于静态资源、文件下载等不需要安全头部的端点。
     */
    private List<String> excludes = new ArrayList<>();

    /** CSP 配置 */
    @Data
    public static class CspConfig {
        private boolean enabled = true;
        /** 是否允许 unsafe-eval（仅开发环境） */
        private boolean unsafeEval = false;
        /** 显式 CSP 策略字符串（设置后忽略其他细粒度配置） */
        private String policy;
    }

    /** HSTS 配置 */
    @Data
    public static class HstsConfig {
        private boolean enabled = true;
        /** HSTS max-age（秒） */
        private long maxAge = 31536000;
        /** 是否包含子域名 */
        private boolean includeSubdomains = true;
        /** 是否启用 preload */
        private boolean preload = true;
    }

    /** COOP 配置 */
    @Data
    public static class CoopConfig {
        private boolean enabled = true;
        /** COOP 策略 */
        private String policy = "same-origin";
    }

    /** COEP 配置 */
    @Data
    public static class CoepConfig {
        private boolean enabled = true;
        /** COEP 策略：unsafe-none（默认，兼容性好）| require-corp（严格） */
        private String policy = "unsafe-none";
    }

    /** CORP 配置 */
    @Data
    public static class CorpConfig {
        private boolean enabled = true;
        /** CORP 策略 */
        private String policy = "same-origin";
    }
}
