package com.njydsz.userinfo.infra.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.config.MfaSecretEncryptor;

/**
 * AES-256-GCM 实现的 MFA 密钥加密器（生产环境）。
 *
 * <p>基于 AES-GCM 算法提供认证加密，防止密文被篡改。每次加密生成随机 12 字节 IV，
 * 密文格式为 {@code Base64(IV + ciphertext + GCM tag)}。
 *
 * <p><b>密钥来源：</b>通过 {@code ydsz.userinfo.mfa.encryption-key} 配置，必须为 32 字节（256 位）
 * Base64 编码字符串。
 *
 * <p><b>启用条件：</b>{@code ydsz.userinfo.mfa.encryption-key} 已配置。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.userinfo.mfa", name = "encryption-key")
public class AesMfaSecretEncryptor implements MfaSecretEncryptor {

  /** AES-GCM 算法名称 */
  private static final String ALGORITHM = "AES/GCM/NoPadding";

  /** AES-GCM 认证标签位长度 */
  private static final int GCM_TAG_LENGTH = 128;

  /** AES-GCM 推荐 IV 字节长度 */
  private static final int GCM_IV_LENGTH = 12;

  /** AES 密钥字节长度（256 位） */
  private static final int AES_KEY_LENGTH = 32;

  /** AES 秘钥 */
  private final SecretKey secretKey;

  /** 加密随机数生成器 */
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * 构造 AES-GCM 加密器。
   *
   * @param encryptionKey Base64 编码的 32 字节 AES 密钥
   * @throws IllegalArgumentException 密钥长度不为 32 字节时抛出
   */
  public AesMfaSecretEncryptor(String encryptionKey) {
    byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
    if (keyBytes.length != AES_KEY_LENGTH) {
      throw new IllegalArgumentException(
          "MFA encryption key must be 32 bytes (256 bits), actual: " + keyBytes.length);
    }
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
    log.info("AesMfaSecretEncryptor initialized (AES-256-GCM)");
  }

  @Override
  public String encrypt(String plainSecret) {
    if (plainSecret == null || plainSecret.isBlank()) {
      throw new IllegalArgumentException("MFA secret must not be null or blank");
    }
    try {
      // 生成随机 IV
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

      byte[] ciphertext = cipher.doFinal(plainSecret.getBytes(StandardCharsets.UTF_8));

      // 拼接 IV + ciphertext
      byte[] result = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, result, 0, iv.length);
      System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);

      return Base64.getEncoder().encodeToString(result);
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
      byte[] decoded = Base64.getDecoder().decode(cipherSecret);
      if (decoded.length < GCM_IV_LENGTH) {
        throw new IllegalArgumentException("Invalid MFA cipher secret: too short");
      }

      // 拆分 IV 和 ciphertext
      byte[] iv = new byte[GCM_IV_LENGTH];
      byte[] ciphertext = new byte[decoded.length - GCM_IV_LENGTH];
      System.arraycopy(decoded, 0, iv, 0, GCM_IV_LENGTH);
      System.arraycopy(decoded, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

      byte[] plaintext = cipher.doFinal(ciphertext);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("Failed to decrypt MFA secret: {}", e.getMessage(), e);
      throw new IllegalArgumentException("MFA secret decryption failed", e);
    }
  }
}
