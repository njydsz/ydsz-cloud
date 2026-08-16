package com.njydsz.common.util.security.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.njydsz.common.util.api.Experimental;

/**
 * AES-GCM 加密提供者——实现 {@link CryptoProvider} 统一契约。
 *
 * <p>支持 128 位和 256 位密钥，默认 256 位。 内部使用 ThreadLocal Cipher 池避免 Provider 查找开销。
 *
 * <p><b>密文格式：</b>IV(12 bytes) || ciphertext + GCM tag(16 bytes)
 *
 * <p><b>线程安全：</b>Cipher 实例 ThreadLocal 隔离，generateKey/generateIv 使用共享 SecureRandom（内部同步），多线程安全。
 *
 * @author ydsz-team
 * @since 3.0.0
 */
@Experimental("SPI 仍在试用期；AAD 与密钥长度的默认行为可能调整")
public final class AesGcmCryptoProvider implements CryptoProvider {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_LENGTH = 12;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** ThreadLocal Cipher 池——避免每次 encrypt/decrypt 重复查找 Provider */
  private static final ThreadLocal<Cipher> CIPHER_POOL =
      ThreadLocal.withInitial(
          () -> {
            try {
              return Cipher.getInstance(TRANSFORMATION);
            } catch (Exception e) {
              throw new CryptoException("AES/GCM not available on current JVM", e);
            }
          });

  private final int keyLength;

  /** 默认构造——256 位密钥。 */
  public AesGcmCryptoProvider() {
    this(256);
  }

  /**
   * 指定位数构造。
   *
   * @param keyBits 密钥位数（128 或 256）
   * @throws IllegalArgumentException 密钥位数非 128/256 时
   */
  public AesGcmCryptoProvider(int keyBits) {
    if (keyBits != 128 && keyBits != 192 && keyBits != 256) {
      throw new IllegalArgumentException("AES keyBits must be 128, 192, or 256, got " + keyBits);
    }
    this.keyLength = keyBits / 8;
  }

  @Override
  public String algorithm() {
    return "AES-" + (keyLength * 8) + "-GCM";
  }

  @Override
  public int keyLength() {
    return keyLength;
  }

  @Override
  public int ivLength() {
    return IV_LENGTH;
  }

  @Override
  public byte[] generateKey() {
    byte[] key = new byte[keyLength];
    SECURE_RANDOM.nextBytes(key);
    return key;
  }

  @Override
  public byte[] generateIv() {
    byte[] iv = new byte[IV_LENGTH];
    SECURE_RANDOM.nextBytes(iv);
    return iv;
  }

  @Override
  public byte[] encrypt(byte[] plaintext, byte[] key, byte[] aad) {
    Objects.requireNonNull(plaintext, "plaintext must not be null");
    Objects.requireNonNull(key, "key must not be null");
    if (key.length != keyLength) {
      throw new IllegalArgumentException(
          "Key length mismatch: expected " + keyLength + " bytes, got " + key.length);
    }

    byte[] iv = generateIv();
    try {
      Cipher cipher = CIPHER_POOL.get();
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key, ALGORITHM),
          new GCMParameterSpec(GCM_TAG_BITS, iv));
      if (aad != null && aad.length > 0) {
        cipher.updateAAD(aad);
      }
      byte[] ciphertext = cipher.doFinal(plaintext);

      // 拼接: IV(12 bytes) || ciphertext(+16 bytes tag)
      return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
    } catch (CryptoException e) {
      throw e;
    } catch (Exception e) {
      throw new CryptoException("AES-GCM encryption failed", e);
    }
  }

  @Override
  public byte[] decrypt(byte[] ciphertext, byte[] key, byte[] aad) {
    Objects.requireNonNull(ciphertext, "ciphertext must not be null");
    Objects.requireNonNull(key, "key must not be null");
    if (key.length != keyLength) {
      throw new IllegalArgumentException(
          "Key length mismatch: expected " + keyLength + " bytes, got " + key.length);
    }
    if (ciphertext.length < IV_LENGTH + (GCM_TAG_BITS / 8)) {
      throw new IllegalArgumentException(
          "Ciphertext too short: minimum " + (IV_LENGTH + GCM_TAG_BITS / 8) + " bytes");
    }

    // 提取 IV
    byte[] iv = new byte[IV_LENGTH];
    System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);

    // 提取 ciphertext + tag
    int ctLen = ciphertext.length - IV_LENGTH;
    byte[] ct = new byte[ctLen];
    System.arraycopy(ciphertext, IV_LENGTH, ct, 0, ctLen);

    try {
      Cipher cipher = CIPHER_POOL.get();
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, ALGORITHM),
          new GCMParameterSpec(GCM_TAG_BITS, iv));
      if (aad != null && aad.length > 0) {
        cipher.updateAAD(aad);
      }
      return cipher.doFinal(ct);
    } catch (CryptoException e) {
      throw e;
    } catch (AEADBadTagException e) {
      throw new CryptoException("AES-GCM authentication failed: data may be tampered", e);
    } catch (Exception e) {
      throw new CryptoException("AES-GCM decryption failed", e);
    }
  }

  @Override
  public String toString() {
    return "AesGcmCryptoProvider{algorithm='" + algorithm() + "'}";
  }
}
