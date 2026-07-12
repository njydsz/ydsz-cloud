package com.njydsz.pmis.common.safe.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * CSRF 防护配置属性
 *
 * <p>用于配置 CSRF 防护的行为。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
 *   safe:
 *     csrf:
 *       enabled: true
 *       token-header: X-CSRF-TOKEN
 *       token-parameter: _csrf
 *       expiration-seconds: 3600
 *       check-origin: true
 *       allowed-origins:
 *         - https://example.com
 *         - https://*.example.com
 *       cookie-secure: true
 *       same-site: Lax
 *       excludes:
 *         - /error
 *         - /actuator/**
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@ConfigurationProperties(prefix = "remi.safe.csrf")
public class CsrfProperties {

    /**
     * 是否启用 CSRF 防护
     *
     * <p>默认值为 true。
     */
    private boolean enabled = true;

    /**
     * 过滤器注册顺序
     *
     * <p>数值越小，优先级越高。
     * 建议在 XssFilter 之前执行。
     */
    private int order = 3;

    /**
     * CSRF 令牌请求头名称
     *
     * <p>客户端需要在请求头中携带此令牌。
     * 默认值为 "X-CSRF-TOKEN"。
     */
    private String tokenHeader = "X-CSRF-TOKEN";

    /**
     * CSRF 令牌请求参数名称
     *
     * <p>客户端也可以通过表单参数提交令牌。
     * 默认值为 "_csrf"。
     */
    private String tokenParameter = "_csrf";

    /**
     * CSRF 令牌过期时间
     *
     * <p>单位为秒，默认 3600（1小时）。
     */
    private long expirationSeconds = 3600;

    /**
     * 排除路径列表
     *
     * <p>这些路径不需要 CSRF 验证，支持 Ant 风格路径匹配。
     * 通常用于公开接口、GET 请求等。
     */
    private List<String> excludes = new ArrayList<>();

    /**
     * 会话 ID 请求头名称
     *
     * <p>用于从请求头中获取会话 ID。
     * 如果为空，则使用 Cookie 中的 JSESSIONID。
     */
    private String sessionIdHeader = "X-Session-Id";

    /**
     * 是否启用 Origin/Referer 校验（第二道防线）
     *
     * <p>默认 true。在 Token 校验之前先校验请求来源，拒绝跨站请求。
     * 即使攻击者通过 XSS 窃取了 Token，纯跨站请求仍会被 Origin/Referer 校验拦截。
     */
    private boolean checkOrigin = true;

    /**
     * 允许的 Origin 列表
     *
     * <p>为空时只允许 Origin 与请求 Host 一致的同源请求。
     * 支持精确匹配和通配符匹配（如 {@code https://*.example.com}）。
     * 仅在 {@link #checkOrigin} 为 true 时生效。
     */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * Cookie 的 Secure 标志
     *
     * <p>为 null 时根据请求的 isSecure() 动态决定（HTTP 请求下 Cookie 不标记 Secure）。
     * 设为 true 时强制标记 Secure，确保 Cookie 仅通过 HTTPS 传输（生产环境推荐）。
     * 设为 false 时强制不标记 Secure（仅开发环境使用）。
     */
    private Boolean cookieSecure = null;

    /**
     * Cookie 的 SameSite 属性
     *
     * <p>可选值：Strict、Lax、None。
     * <ul>
     *   <li>Strict：同站请求才发送 Cookie（最严格，但影响用户体验）</li>
     *   <li>Lax：跨站 GET 请求发送 Cookie，其他不发送（默认，平衡安全与可用性）</li>
     *   <li>None：跨站请求都发送 Cookie（需配合 Secure 标志）</li>
     * </ul>
     */
    private String sameSite = "Lax";
}
