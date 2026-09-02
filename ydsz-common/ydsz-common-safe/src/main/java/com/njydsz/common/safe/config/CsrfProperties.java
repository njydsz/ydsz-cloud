new ArrayList<>(16)l.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CSRF 防护配置属性
 *
 * <p>用于配置 CSRF 防护的行为。
 *
 * <h3>P0-2: JWT Bearer Token 架构下 CSRF 默认关闭</h3>
 *
 * <p>ydsz-cloud 采用 JWT Bearer Token 认证（{@code Authorization: Bearer <JWT>}）， 前端不依赖 Cookie
 * 进行身份认证，因此<b>不存在 CSRF 威胁</b>（攻击者无法通过跨域脚本 强制浏览器添加自定义 Authorization 头）。
 *
 * <p><b>仅以下场景需要启用 CSRF：</b>
 *
 * <ul>
 *   <li>存在基于 Cookie/Session 认证的端点（如后台管理系统使用 JSESSIONID）
 *   <li>第三方回调接口需要校验请求来源 Origin
 * </ul>
 *
 * <p>如确需启用，前端需在每次非 GET 请求中携带 CSRF 令牌（通过 Header 或 Parameter）。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   safe:
 *     csrf:
 *       enabled: false  # 默认 false（JWT 架构无需 CSRF）
 *       # enabled: true  # 仅 Cookie 认证场景启用
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
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.csrf")
public class CsrfProperties {

  /**
   * 是否启用 CSRF 防护
   *
   * <p><b>默认值为 false</b>。
   *
   * <p>ydsz-cloud 采用 JWT Bearer Token 架构，不存在 CSRF 威胁，默认关闭。 仅当存在 Cookie/Session 认证端点时启用。
   *
   * <p>启用后，所有非 GET 请求需在 Header ({@code X-CSRF-TOKEN}) 或 参数 ({@code _csrf}) 中携带 CSRF 令牌。
   */
  private boolean enabled = false;

  /**
   * CSRF 防护模式
   *
   * <ul>
   *   <li>SYNCHRONIZER：Synchronizer Token Pattern（服务端存储 Token，验证后刷新）
   *   <li>DOUBLE_SUBMIT：Double Submit Cookie（无状态，Cookie + Header 双重提交比对）
   * </ul>
   *
   * 默认为 SYNCHRONIZER。微服务/SPA 架构推荐使用 DOUBLE_SUBMIT（无需 Redis）。
   */
  private CsrfMode mode = CsrfMode.SYNCHRONIZER;

  /**
   * 过滤器注册顺序
   *
   * <p>数值越小，优先级越高。 建议在 XssFilter 之前执行。
   */
  private int order = 3;

  /**
   * CSRF 令牌请求头名称
   *
   * <p>客户端需要在请求头中携带此令牌。 默认值为 "X-CSRF-TOKEN"。
   */
  private String tokenHeader = "X-CSRF-TOKEN";

  /**
   * CSRF 令牌请求参数名称
   *
   * <p>客户端也可以通过表单参数提交令牌。 默认值为 "_csrf"。
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
   * <p>这些路径不需要 CSRF 验证，支持 Ant 风格路径匹配。 通常用于公开接口、GET 请求等。
   */
  private List<String> excludes = new ArrayList<>();

  /**
   * 会话 ID 请求头名称
   *
   * <p>用于从请求头中获取会话 ID。 如果为空，则使用 Cookie 中的 JSESSIONID。
   */
  private String sessionIdHeader = "X-Session-Id";

  /**
   * 是否启用 Origin/Referer 校验（第二道防线）
   *
   * <p>默认 true。在 Token 校验之前先校验请求来源，拒绝跨站请求。 即使攻击者通过 XSS 窃取了 Token，纯跨站请求仍会被 Origin/Referer 校验拦截。
   */
  private boolean checkOrigin = true;

  /**
   * 允许的 Origin 列表
   *
   * <p>为空时只允许 Origin 与请求 Host 一致的同源请求。 支持精确匹配和通配符匹配（如 {@code https://*.example.com}）。 仅在 {@link
   * #checkOrigin} 为 true 时生效。
   */
  private List<String> allowedOrigins = new ArrayList<>(4);

  /**
   * Cookie 的 Secure 标志
   *
   * <p>为 null 时根据请求的 isSecure() 动态决定（HTTP 请求下 Cookie 不标记 Secure）。 设为 true 时强制标记 Secure，确保 Cookie
   * 仅通过 HTTPS 传输（生产环境推荐）。 设为 false 时强制不标记 Secure（仅开发环境使用）。
   */
  private Boolean cookieSecure = null;

  /**
   * Cookie 的 SameSite 属性
   *
   * <p>可选值：Strict、Lax、None。
   *
   * <ul>
   *   <li>Strict：同站请求才发送 Cookie（最严格，但影响用户体验）
   *   <li>Lax：跨站 GET 请求发送 Cookie，其他不发送（默认，平衡安全与可用性）
   *   <li>None：跨站请求都发送 Cookie（需配合 Secure 标志）
   * </ul>
   */
  private String sameSite = "Lax";

  /** CSRF 防护模式枚举 */
  public enum CsrfMode {
    /** Synchronizer Token Pattern（服务端存储 Token，验证后刷新） */
    SYNCHRONIZER,
    /** Double Submit Cookie（无状态，Cookie + Header 双重提交比对） */
    DOUBLE_SUBMIT
  }
}
