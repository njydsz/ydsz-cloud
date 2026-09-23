package com.njydsz.common.safe.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
 * }</pre>
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
}
