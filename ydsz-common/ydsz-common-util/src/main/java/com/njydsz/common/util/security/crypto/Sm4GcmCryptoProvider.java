package com.njydsz.common.util.security.crypto;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Objects;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.njydsz.common.util.api.Experimental;

/**
 * SM4-GCM 加密提供者——国密合规场景使用。
 *
 * <p>实现与 {@link AesGcmCryptoProvider} 完全一致的 {@link CryptoProvider} 契约， 业务方可通过配置 {@code
 * crypto.algorithm=SM4-GCM} 一键切换到国密算法。
 *
 * <p>依赖 BouncyCastle Provider，首次加载时自动注册。
 *
 * <p><b>密文格式：</b>IV(12 bytes) || ciphertext + GCM tag(16 bytes)
 *
 * @author ydsz-team
 * @since 3.0.0
 */
@Experimental("SPI 仍在试用期；AAD 与密钥长度的默认行为可能调整")
public final class Sm4GcmCryptoProvider implements CryptoProvider {

  private static final String ALGORITHM = "SM4";
  private static final String TRANSFORMATION = "SM4/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_LENGTH = 12;
  private static final int KEY_LENGTH = 16;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  // CHECKSTYLE.OFF: RegexpSinglelineJava — ThreadLocal 字段，已在使用处/清理方法中调用 remove()（云顶规范 15.1）
  private static final ThreadLocal<Cipher> CIPHER_POOL =
  // CHECKSTYLE.ON: RegexpSinglelineJava
      ThreadLocal.withInitial(
          () -> {
            ensureBcProvider();
            try {
              return Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            } catch (Exception e) {
              throw new CryptoException("SM4/GCM not available via BouncyCastle", e);
            }
          });

  /** 确保 BC Provider 已注册（幂等） */
  private static void ensureBcProvider() {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  public Sm4GcmCryptoProvider() {
    ensureBcProvider();
  }

  /**
   * 清理当前线程的 SM4-GCM Cipher 缓存。
   *
   * <p>在线程池复用场景下，建议在请求处理完成后调用此方法，避免 ThreadLocal 内存泄漏。
   */
  public static void cleanup() {
    CIPHER_POOL.remove();
  }

  @Override
  public String algorithm() {
    return "SM4-GCM";
  }

  @Override
  public int keyLength() {
    return KEY_LENGTH;
  }

  @Override
  public int ivLength() {
    return IV_LENGTH;
  }

  @Override
  public byte[] generateKey() {
    byte[] key = new byte[KEY_LENGTH];
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
    if (key.length != KEY_LENGTH) {
      throw new IllegalArgumentException("SM4 key must be 16 bytes, got " + key.length);
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

      return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
    } catch (CryptoException e) {
      throw e;
    } catch (Exception e) {
      throw new CryptoException("SM4-GCM encryption failed", e);
    }
  }

  @Override
  public byte[] decrypt(byte[] ciphertext, byte[] key, byte[] aad) {
    Objects.requireNonNull(ciphertext, "ciphertext must not be null");
    Objects.requireNonNull(key, "key must not be null");
    if (key.length != KEY_LENGTH) {
      throw new IllegalArgumentException("SM4 key must be 16 bytes, got " + key.length);
    }
    if (ciphertext.length < IV_LENGTH + (GCM_TAG_BITS / 8)) {
      throw new IllegalArgumentException(
          "Ciphertext too short: minimum " + (IV_LENGTH + GCM_TAG_BITS / 8) + " bytes");
    }

    byte[] iv = new byte[IV_LENGTH];
    System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
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
      throw new CryptoException("SM4-GCM authentication failed: data may be tampered", e);
    } catch (Exception e) {
      throw new CryptoException("SM4-GCM decryption failed", e);
    }
  }

  @Override
  public String toString() {
    return "Sm4GcmCryptoProvider{algorithm='" + algorithm() + "'}";
  }
}
