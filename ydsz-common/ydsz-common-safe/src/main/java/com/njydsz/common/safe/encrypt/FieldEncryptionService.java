package com.njydsz.common.safe.encrypt;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.security.crypto.CryptoException;

/**
 * 字段加密服务
 *
 * <p>提供 AES-256-GCM 加密解密能力，支持密钥版本管理。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>AES-256-GCM 认证加密，同时保证机密性和完整性
 *   <li>每次加密使用 12 字节随机 IV，确保相同明文产生不同密文
 *   <li>密文格式：[version(1B)][iv(12B)][ciphertext+tag(16B)]
 *   <li>支持密钥版本管理，便于密钥轮换
 * </ul>
 *
 * @author ydsz-team
 * @author ydsz-team
 * @since 1.0.0
 */
public class FieldEncryptionService {

  private static final Logger LOG = LoggerFactory.getLogger(FieldEncryptionService.class);

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;
  private static final int VERSION_LENGTH = 1;

  private final Map<Integer, SecretKey> keyMap = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom = new SecureRandom();
  private final int defaultKeyVersion;

  /**
   * 构造加密服务
   *
   * @param keys 密钥映射（keyVersion -> base64EncodedKey）
   * @param defaultKeyVersion 默认密钥版本
   */
  public FieldEncryptionService(Map<Integer, String> keys, int defaultKeyVersion) {
    this.defaultKeyVersion = defaultKeyVersion;
    keys.forEach(
        (version, base64Key) -> {
          byte[] keyBytes = Base64.getDecoder().decode(base64Key);
          if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                "AES-256 密钥长度必须为 32 字节，当前版本 " + version + " 长度为 " + keyBytes.length);
          }
          keyMap.put(version, new SecretKeySpec(keyBytes, ALGORITHM));
          LOG.info("加载加密密钥版本: {}", version);
        });

    if (!keyMap.containsKey(defaultKeyVersion)) {
      throw new IllegalArgumentException("默认密钥版本 " + defaultKeyVersion + " 未配置");
    }
  }

  /**
   * 加密字符串
   *
   * @param plaintext 明文
   * @param keyVersion 密钥版本
   * @return Base64 编码的密文
   */
  public String encrypt(String plaintext, int keyVersion) {
    if (plaintext == null || plaintext.isEmpty()) {
      return plaintext;
    }

    try {
      SecretKey key = keyMap.get(keyVersion);
      if (key == null) {
        throw new IllegalArgumentException("未知的密钥版本: " + keyVersion);
      }

      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      ByteBuffer byteBuffer = ByteBuffer.allocate(VERSION_LENGTH + iv.length + ciphertext.length);
      byteBuffer.put((byte) keyVersion);
      byteBuffer.put(iv);
      byteBuffer.put(ciphertext);

      return Base64.getEncoder().encodeToString(byteBuffer.array());
    } catch (Exception e) {
      LOG.error("加密失败: keyVersion={}", keyVersion, e);
      throw new CryptoException("加密失败: keyVersion=" + keyVersion, e);
    }
  }

  /**
   * 加密字符串（使用默认密钥版本）
   *
   * @param plaintext 明文
   * @return Base64 编码的密文
   */
  public String encrypt(String plaintext) {
    return encrypt(plaintext, defaultKeyVersion);
  }

  /**
   * 解密字符串
   *
   * @param ciphertext Base64 编码的密文
   * @return 解密后的明文
   */
  public String decrypt(String ciphertext) {
    if (ciphertext == null || ciphertext.isEmpty()) {
      return ciphertext;
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(ciphertext);
      ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

      int keyVersion = byteBuffer.get();
      byte[] iv = new byte[GCM_IV_LENGTH];
      byteBuffer.get(iv);
      byte[] ciphertextBytes = new byte[byteBuffer.remaining()];
      byteBuffer.get(ciphertextBytes);

      SecretKey key = keyMap.get(keyVersion);
      if (key == null) {
        throw new IllegalArgumentException("未知的密钥版本: " + keyVersion);
      }

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

      byte[] plaintextBytes = cipher.doFinal(ciphertextBytes);
      return new String(plaintextBytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      LOG.error("解密失败", e);
      throw new CryptoException("解密失败", e);
    }
  }
}
