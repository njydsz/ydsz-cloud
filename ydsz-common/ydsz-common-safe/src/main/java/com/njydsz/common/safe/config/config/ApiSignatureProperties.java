package com.njydsz.common.safe.config;

import java.util.ArrayList;
import java.util.List;
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
 *       app-secret: "Base64EncodedSecretKey"
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
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.api-signature")
public class ApiSignatureProperties {

  /** 是否启用 API 签名验证 */
  private boolean enabled = false;

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
  private List<String> excludes = new ArrayList<>();
}
