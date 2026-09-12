package com.njydsz.userinfo.infra.config;

import java.util.Base64;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.util.security.crypto.CryptoUtils;
import com.njydsz.userinfo.domain.config.MfaSecretEncryptor;

/**
 * AES-256-GCM 实现的 MFA 密钥加密器（生产环境）。
 *
 * <p>加密能力委托 {@link CryptoUtils}（默认算法 AES-256-GCM，密文格式
 * {@code Base64(IV + ciphertext + GCM tag)}，与本类历史密文格式逐字节兼容，存量数据无需迁移）。</p>
 *
 * <p><b>密钥来源：</b>通过 {@code ydsz.userinfo.mfa.encryption-key} 配置，必须为 32 字节（256 位）
 * Base64 编码字符串。</p>
 *
 * <p><b>启用条件：</b>{@code ydsz.userinfo.mfa.encryption-key} 已配置。</p>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.userinfo.mfa", name = "encryption-key")
public class AesMfaSecretEncryptor implements MfaSecretEncryptor {

  /** AES 密钥字节长度（256 位） */
  private static final int AES_KEY_LENGTH = 32;

  /** AES 密钥（Base64 解码后的字节数组） */
  private final byte[] keyBytes;

  /**
   * 构造 AES-GCM 加密器。
   *
   * @param encryptionKey Base64 编码的 32 字节 AES 密钥（从配置 ydsz.userinfo.mfa.encryption-key 注入）
   * @throws IllegalArgumentException 密钥长度不为 32 字节时抛出
   */
  public AesMfaSecretEncryptor(@Value("${ydsz.userinfo.mfa.encryption-key}") String encryptionKey) {
    byte[] decoded = Base64.getDecoder().decode(encryptionKey);
    if (decoded.length != AES_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "MFA encryption key must be 32 bytes (256 bits), actual: " + decoded.length);
    }
    this.keyBytes = decoded;
    log.info("AesMfaSecretEncryptor initialized (AES-256-GCM, delegated to CryptoUtils)");
  }

  @Override
  public String encrypt(String plainSecret) {
    if (plainSecret == null || plainSecret.isBlank()) {
      throw new IllegalArgumentException("MFA secret must not be null or blank");
    }
    try {
      return CryptoUtils.encrypt(plainSecret, keyBytes);
    } catch (Exception e) {
      log.error("Failed to encrypt MFA secret: {}", e.getMessage(), e);
      throw new IllegalArgumentException("MFA secret encryption failed", e);
    }
  }

  @Override
  public String decrypt(String cipherSecret) {
    if (cipherSecret == null || cipherSecret.isBlank()) {
      throw new IllegalArgumentException("MFA cipher secret must not be null or blank");
    }
    try {
      return CryptoUtils.decrypt(cipherSecret, keyBytes);
    } catch (Exception e) {
      log.error("Failed to decrypt MFA secret: {}", e.getMessage(), e);
      throw new IllegalArgumentException("MFA secret decryption failed", e);
    }
  }
}
