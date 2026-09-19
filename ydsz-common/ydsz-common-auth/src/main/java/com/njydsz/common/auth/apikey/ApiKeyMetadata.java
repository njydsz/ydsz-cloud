package com.njydsz.common.auth.apikey;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import lombok.Getter;
import lombok.ToString;

/**
 * API Key 元数据。
 *
 * <p>存储在 Redis Hash 中的 API Key 信息，包含所属应用、权限范围、过期时间、速率限制配额等。
 *
 * <p>Redis Key: {@code ydsz:auth:apikey:{keyPrefix}}
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Getter
@ToString
public class ApiKeyMetadata implements Serializable {

  private static final long serialVersionUID = 1L;

  /** API Key 唯一 ID（UUID） */
  private final String keyId;

  /** 所属应用 ID */
  private final String appId;

  /** 权限范围（如 ["user:read", "order:read"]） */
  private final Set<String> scopes;

  /** 过期时间（毫秒时间戳），0 表示永不过期 */
  private final long expiresAtMs;

  /** 每分钟请求速率限制（QPS），0 表示不限 */
  private final int rateLimitPerMinute;

  /** 状态：ACTIVE / REVOKED */
  private final String status;

  /**
   * 构造 API Key 元数据。
   *
   * @param keyId 唯一 ID
   * @param appId 应用 ID
   * @param scopes 权限范围
   * @param expiresAtMs 过期时间毫秒
   * @param rateLimitPerMinute 每分钟速率限制
   * @param status 状态
   */
  public ApiKeyMetadata(
      String keyId,
      String appId,
      Set<String> scopes,
      long expiresAtMs,
      int rateLimitPerMinute,
      String status) {
    this.keyId = keyId;
    this.appId = appId;
    this.scopes = scopes != null ? Set.copyOf(scopes) : Collections.emptySet();
    this.expiresAtMs = expiresAtMs;
    this.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
    this.status = status;
  }

  /**
   * 检查 API Key 是否有效（未过期、未撤销）。
   *
   * @return 有效返回 true
   */
  public boolean isValid() {
    if (!"ACTIVE".equals(status)) {
      return false;
    }
    return expiresAtMs <= 0 || expiresAtMs > System.currentTimeMillis();
  }

  /**
   * 检查是否拥有指定权限范围。
   *
   * @param requiredScope 需要的权限
   * @return 拥有返回 true
   */
  public boolean hasScope(String requiredScope) {
    return scopes.contains(requiredScope) || scopes.contains("*");
  }
}
