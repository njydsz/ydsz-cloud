package com.njydsz.userinfo.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * WebAuthn 凭证视图对象
 *
 * <p>存储用户注册的无密码认证凭证（公钥凭证），用于后续认证时验证签名。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WebAuthnCredentialVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 凭证 ID（Base64URL 编码） */
  private String credentialId;

  /** 用户 ID */
  private String userId;

  /** 公钥（COSE 密钥格式，Base64URL 编码） */
  private String publicKey;

  /** 签名计数器（防克隆检测） */
  private long signCount;

  /** 凭证类型（如 "public-key"） */
  private String credentialType;

  /** AAGUID（认证器唯一标识） */
  private String aaguid;

  /** 凭证友好名称（如 "iPhone FaceID"） */
  private String displayName;

  /** 注册时间 */
  private LocalDateTime registeredAt;

  /** 最后使用时间 */
  private LocalDateTime lastUsedAt;

  public String getCredentialId() {
    return credentialId;
  }

  public void setCredentialId(String credentialId) {
    this.credentialId = credentialId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public long getSignCount() {
    return signCount;
  }

  public void setSignCount(long signCount) {
    this.signCount = signCount;
  }

  public String getCredentialType() {
    return credentialType;
  }

  public void setCredentialType(String credentialType) {
    this.credentialType = credentialType;
  }

  public String getAaguid() {
    return aaguid;
  }

  public void setAaguid(String aaguid) {
    this.aaguid = aaguid;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public LocalDateTime getRegisteredAt() {
    return registeredAt;
  }

  public void setRegisteredAt(LocalDateTime registeredAt) {
    this.registeredAt = registeredAt;
  }

  public LocalDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  public void setLastUsedAt(LocalDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }
}
