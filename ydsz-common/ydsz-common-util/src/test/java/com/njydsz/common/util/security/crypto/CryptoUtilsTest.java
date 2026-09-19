package com.njydsz.common.util.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link CryptoUtils} 单元测试。
 *
 * <p>覆盖：加解密 round-trip（AES-256-GCM）、Hex 编解码 round-trip、
 * withKey 模板方法、算法列表查询、密钥安全擦除。
 *
 * <p>注意：通过 {@link CryptoUtils#resetForTesting()} 避免静态状态跨测试污染。
 *
 * @since 26.09.19
 */
@DisplayName("CryptoUtils 测试")
class CryptoUtilsTest {

  private static final String DEFAULT_ALGORITHM = "AES-256-GCM";
  private byte[] key;

  @BeforeEach
  void setUp() {
    CryptoUtils.resetForTesting();
    CryptoUtils.setDefaultAlgorithm(DEFAULT_ALGORITHM);
    // 生成 256 位 (32 字节) 随机密钥
    key = new byte[32];
    new SecureRandom().nextBytes(key);
  }

  @Nested
  @DisplayName("加解密 round-trip")
  class EncryptDecryptTest {

    @Test
    @DisplayName("encrypt/decrypt: 加解密后明文一致")
    void encryptDecrypt_roundTrip() {
      String plaintext = "Hello, YDSZ 云顶平台! 测试文本 123";
      String ciphertext = CryptoUtils.encrypt(plaintext, key);
      String decrypted = CryptoUtils.decrypt(ciphertext, key);
      assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("encrypt/decrypt: 不同明文产生不同密文")
      void encrypt_differentPlaintexts_differentCiphertexts() {
      String cipher1 = CryptoUtils.encrypt("text-one", key);
      String cipher2 = CryptoUtils.encrypt("text-two", key);
      assertThat(cipher1).isNotEqualTo(cipher2);
    }

    @Test
    @DisplayName("encrypt: null plaintext 抛出 NPE")
    void encrypt_nullPlaintext_throwsNpe() {
      assertThatThrownBy(() -> CryptoUtils.encrypt(null, key))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("decrypt: null ciphertext 抛出 NPE")
    void decrypt_nullCiphertext_throwsNpe() {
      assertThatThrownBy(() -> CryptoUtils.decrypt(null, key))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Hex 编解码 round-trip")
  class HexTest {

    @Test
    @DisplayName("encryptHex/decryptHex: 加解密后明文一致")
    void encryptDecryptHex_roundTrip() {
      // AES-256 需要 32 字节 = 64 个 hex 字符
      String validHexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

      String plaintext = "Test Hex Encoding 中文测试";
      String ciphertext = CryptoUtils.encryptHex(plaintext, validHexKey);
      String decrypted = CryptoUtils.decryptHex(ciphertext, validHexKey);

      assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("encryptHex: null plaintext 抛出 NPE")
    void encryptHex_nullPlaintext_throwsNpe() {
      String validHexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
      assertThatThrownBy(() -> CryptoUtils.encryptHex(null, validHexKey))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("withKey 模板方法")
  class WithKeyTest {

    @Test
    @DisplayName("withKey: 正常执行并返回结果")
    void withKey_normalExecution() {
      String result = CryptoUtils.withKey(key, k -> CryptoUtils.encrypt("template-test", k));
      assertThat(result).isNotBlank();

      // 验证密钥已被擦除（全零）
      for (byte b : key) {
        assertThat(b).isEqualTo((byte) 0);
      }
    }

    @Test
    @DisplayName("withKey: 异常时仍擦除密钥")
    void withKey_exceptionStillDestroysKey() {
      assertThatThrownBy(() -> CryptoUtils.withKey(key, k -> {
        throw new RuntimeException("intentional failure");
      })).isInstanceOf(RuntimeException.class);

      // 密钥仍被擦除
      for (byte b : key) {
        assertThat(b).isEqualTo((byte) 0);
      }
    }

    @Test
    @DisplayName("withKeyVoid: 无返回值正常执行")
    void withKeyVoid_normalExecution() {
      byte[] capturedKey = key.clone();
      CryptoUtils.withKeyVoid(capturedKey, k -> {
        String cipher = CryptoUtils.encrypt("void-test", k);
        assertThat(cipher).isNotBlank();
      });
      // 密钥被擦除
      for (byte b : capturedKey) {
        assertThat(b).isEqualTo((byte) 0);
      }
    }
  }

  @Nested
  @DisplayName("密钥擦除")
  class DestroyKeyTest {

    @Test
    @DisplayName("destroyKey: 安全擦除密钥，全部置零")
    void destroyKey_allBytesZeroed() {
      byte[] testKey = "super-secret-key-bytes!".getBytes();
      CryptoUtils.destroyKey(testKey);
      for (byte b : testKey) {
        assertThat(b).isEqualTo((byte) 0);
      }
    }

    @Test
    @DisplayName("destroyKey: null 时无操作不抛异常")
    void destroyKey_nullNoOp() {
      CryptoUtils.destroyKey(null); // 不应抛异常
    }
  }

  @Nested
  @DisplayName("算法配置")
  class AlgorithmTest {

    @Test
    @DisplayName("availableAlgorithms: 包含 AES-256-GCM")
    void availableAlgorithms_containsAesGcm() {
      Set<String> algorithms = CryptoUtils.availableAlgorithms();
      assertThat(algorithms).contains("AES-256-GCM");
    }

    @Test
    @DisplayName("provider: 返回非空提供者")
    void provider_notNull() {
      assertThat(CryptoUtils.provider()).isNotNull();
      assertThat(CryptoUtils.provider("AES-256-GCM")).isNotNull();
    }
  }
}
