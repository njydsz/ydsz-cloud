package com.njydsz.common.safe.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/**
 * API 签名验证配置属性
 *
 * <p>配置前缀 {@code ydsz.safe.api-signature}，用于控制 API 请求签名验证行为。
 *
 * <p><b>配置示例：</b>
 *
 * <pre>{@code
 * ydsz:
 *   safe:
 *     api-signature:
 *       enabled: true
 *       app-id: "ydsz-web"
 *       app-secret: "${API_SIGNATURE_SECRET}"
 *       timestamp-tolerance-seconds: 300
 *       nonce-expire-seconds: 600
 *       header-timestamp: X-Timestamp
 *       header-nonce: X-Nonce
 *       header-signature: X-Signature
 *       header-app-id: X-App-Id
 *       excludes:
 *         - /api/public/**
 *         - /actuator/**
 *       signing-protocol: standard   # standard | legacy-raw-body
 *       filter-order: 2147483627    # HIGHEST_PRECEDENCE + 20; 默认 HIGHEST_PRECEDENCE + 4
 *       success-attribute: ""         # 校验通过后注入 request 的属性名（空=不注入）
 * }</pre>
 *
 * <p><b>签名协议说明：</b>
 *
 * <ul>
 *   <li>{@code standard}（默认）：签名串 = {@code method + "\n" + path + "\n" + normalizedQuery + "\n" +
 *       timestamp + "\n" + nonce + "\n" + bodySha256}，其中 body 取 SHA-256 十六进制摘要、Query
 *       按 key 字典序排序后规范化，符合安全最佳实践。
 *   <li>{@code legacy-raw-body}：签名串 = {@code method + "\n" + path + "\n" + query + "\n" + body +
 *       "\n" + timestamp + "\n" + nonce}，body 取原始内容，query 不排序。用于兼容 ydsz-userinfo
 *       内部 API 旧版签名算法；新接入方一律使用 {@code standard}。
 * </ul>
 *
 * <p><b>过滤器顺序：</b>通过 {@code filter-order} 可覆盖默认顺序（{@link Ordered#HIGHEST_PRECEDENCE} +
 * 4），便于多过滤器场景协调先后。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.api-signature")
public class ApiSignatureProperties {

  /** 占位密钥默认值（生产环境必须覆盖） */
  private static final String PLACEHOLDER_SECRET = "change-me-in-production";

  /** 是否启用 API 签名验证 */
  private boolean isEnabled = false;

  /** 应用 ID，用于区分不同接入方 */
  private String appId;

  /** 应用密钥（Base64 编码），用于签名计算 */
  private String appSecret;

  /** 时间戳容差（秒），请求时间戳与服务端时间差超过此值则拒绝 */
  private long timestampToleranceSeconds = 300;

  /** Nonce 过期时间（秒），防重放缓存的 TTL */
  private long nonceExpireSeconds = 600;

  /** 时间戳请求头名称 */
  private String headerTimestamp = "X-Timestamp";

  /** Nonce 请求头名称 */
  private String headerNonce = "X-Nonce";

  /** 签名请求头名称 */
  private String headerSignature = "X-Signature";

  /** 应用 ID 请求头名称 */
  private String headerAppId = "X-App-Id";

  /** 排除签名验证的路径列表（Ant 风格） */
  private List<String> excludes = new ArrayList<>(4);

  /**
   * 签名协议版本（默认 {@code standard}）。
   *
   * <p>{@code standard} 使用 body SHA-256 摘要与规范化 Query；{@code legacy-raw-body} 使用原始 body
   * 与原始 Query，仅用于向后兼容旧版内部 API 调用方。
   */
  private SigningProtocol signingProtocol = SigningProtocol.STANDARD;

  /**
   * 过滤器顺序（默认 {@code Ordered.HIGHEST_PRECEDENCE + 4}）。
   *
   * <p>在多模块共享 Spring Context 的场景（如 ydzs-web 聚合部署），可独立调整本过滤器的
   * 顺序，避免与其他安全过滤器冲突。
   */
  private int filterOrder = Ordered.HIGHEST_PRECEDENCE + 4;

  /**
   * 签名校验通过后注入到 request attribute 的名称（为空则不注入）。
   *
   * <p>下游过滤器或切面可通过此属性判断签名校验已通过并跳过后续校验逻辑。
   * 典型用途：ydz-suserinfo 模块通过此属性通知 {@code RequireInternalAspect} 跳过内部调用 IP 白名单校验。
   */
  private String successAttribute = "";

  /**
   * 启动校验：若启用签名验证但密钥为占位值则拒绝启动。
   *
   * <p>防止生产环境使用默认占位密钥导致签名形同虚设。
   */
  @PostConstruct
  public void validateConfig() {
    if (isEnabled && PLACEHOLDER_SECRET.equals(appSecret)) {
      throw new IllegalStateException(
          "ydsz.safe.api-signature 已启用但 appSecret 使用了占位值 "
              + PLACEHOLDER_SECRET
              + "，请在生产环境通过环境变量注入真实密钥");
    }
  }

  /**
   * 签名协议枚举。
   */
  public enum SigningProtocol {
    /**
     * 标准签名协议（推荐，安全性更高）。
     *
     * <p>签名串包含 body SHA-256 摘要与规范化 Query，可防 body/Query 参数的中间人篡改。
     */
    STANDARD,
    /**
     * 旧版原始 body 签名协议（向后兼容）。
     *
     * <p>签名串包含原始 body 与原始 Query，与 ydz-userinfo 旧版内部 API 一致。
     * 新接入方不应使用此模式。
     */
    LEGACY_RAW_BODY
  }
}
