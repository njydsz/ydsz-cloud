package com.njydsz.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 网关安全响应头配置（P2-12）
 *
 * <p>可配置的安全响应头策略，对标 OWASP 安全标准。
 *
 * <h3>配置项</h3>
 * <pre>
 * ydsz:
 *   gateway:
 *     security-headers:
 *       enabled: true
 *       csp:
 *         enabled: true
 *         unsafe-eval: false  # 是否允许 unsafe-eval（仅开发环境）
 *       hsts:
 *         enabled: true
 *         max-age: 31536000  # 1 年
 *         include-subdomains: true
 *         preload: true
 *       coop:
 *         enabled: true
 *         policy: same-origin  # same-origin | same-origin-allow-popups | unsafe-none
 *       coep:
 *         enabled: true
 *         policy: require-corp  # require-corp | credentialless | unsafe-none
 *       corp:
 *         enabled: true
 *         policy: same-origin  # same-origin | same-site | cross-origin
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "ydsz.gateway.security-headers")
public class SecurityHeadersProperties {

    /** 是否启用安全响应头（全局开关） */
    private boolean enabled = true;

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

    /** CSP 配置 */
    @Data
    public static class CspConfig {
        private boolean enabled = true;
        /** 是否允许 unsafe-eval（仅开发环境） */
        private boolean unsafeEval = false;
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

    /** COEP 配置
     *
     * <p>P2-6: 默认策略改为 unsafe-none，避免阻止跨域静态资源（CDN 图片、字体、第三方脚本）导致页面白屏。
     * 金融/等高安全场景可在 Nacos 覆盖为 require-corp。
     */
    @Data
    public static class CoepConfig {
        private boolean enabled = true;
        /** COEP 策略：unsafe-none（默认，兼容性好）| require-corp（严格，需配合 CORP 使用） */
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
