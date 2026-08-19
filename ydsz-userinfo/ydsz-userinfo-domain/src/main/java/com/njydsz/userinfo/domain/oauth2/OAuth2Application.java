package com.njydsz.userinfo.domain.oauth2;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * OAuth2 应用聚合根。
 *
 * <p>表示一个注册的 OAuth2 客户端应用，包含应用的基本信息、认证配置和权限范围。
 *
 * @param id 应用 ID（UUID）
 * @param clientId 客户端 ID（唯一标识）
 * @param clientName 应用名称
 * @param clientSecret 客户端密钥（BCrypt 加密存储）
 * @param clientType 客户端类型（CONFIDENTIAL/PUBLIC）
 * @param redirectUris 授权回调地址白名单
 * @param allowedScopes 允许申请的权限范围
 * @param allowedAudiences 允许的受众（资源服务）
 * @param status 应用状态
 * @param description 应用描述
 * @param iconUrl 应用图标 URL
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param createdBy 创建者用户 ID
 * @author ydsz-team
 * @since 2.18.0
 */
public record OAuth2Application(
    String id,
    String clientId,
    String clientName,
    String clientSecret,
    ClientType clientType,
    List<String> redirectUris,
    Set<String> allowedScopes,
    Set<String> allowedAudiences,
    ApplicationStatus status,
    String description,
    String iconUrl,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String createdBy) {

  /**
   * 客户端类型枚举。
   */
  public enum ClientType {
    /** 机密客户端（可安全存储密钥，如后端服务） */
    CONFIDENTIAL,
    /** 公共客户端（无法安全存储密钥，如 SPA、移动端） */
    PUBLIC
  }

  /**
   * 应用状态枚举。
   */
  public enum ApplicationStatus {
    /** 启用 */
    ENABLED,
    /** 禁用 */
    DISABLED
  }

  /**
   * 返回包含明文密钥的应用对象（仅创建/重置密钥时使用）。
   *
   * <p>注意：此方法仅用于创建/重置密钥后返回给调用方，不应持久化明文密钥。
   *
   * @param plainSecret 明文 clientSecret
   * @return 包含明文密钥的应用对象
   */
  public OAuth2Application withPlainSecret(String plainSecret) {
    return new OAuth2Application(
        id(),
        clientId(),
        clientName(),
        plainSecret,
        clientType(),
        redirectUris(),
        allowedScopes(),
        allowedAudiences(),
        status(),
        description(),
        iconUrl(),
        createdAt(),
        updatedAt(),
        createdBy());
  }
}
