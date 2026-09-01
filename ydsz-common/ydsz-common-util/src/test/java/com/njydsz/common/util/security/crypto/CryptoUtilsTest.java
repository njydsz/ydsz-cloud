package com.njydsz.common.util.security.crypto;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CryptoUtils / AesGcmCryptoProvider 加解密测试。
 *
 * <p>覆盖安全不变量：
 *
 * <ul>
 *   <li>AES-GCM 往返：加密后可解密还原
 *   <li>AAD 完整性：解密时 AAD 不匹配必须失败（认证加密核心语义）
 *   <li>IV 随机性：同一明文两次密文不同（语义安全）
 *   <li>密钥长度校验与密文长度校验
 *   <li>算法路由优先级：系统属性 &gt; Spring 注入 &gt; 默认 AES-256-GCM
 *   <li>setDefaultAlgorithm 仅允许注入一次（E-4 语义）
 * </ul>
 *
 * <p>静态状态通过 {@code resetForTesting()} 复位，用例间互不污染。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class CryptoUtilsTest {

  /** 测试明文 */
  private static final String PLAINTEXT = "ydsz-common-util 加解密测试明文 — 中文/ASCII 混排 123";

  /** 测试 AAD（附加认证数据） */
  private static final byte[] AAD = "request-context-aad".getBytes(StandardCharsets.UTF_8);

  /** 篡改后的 AAD（与加密时不一致） */
  private static final byte[] TAMPERED_AAD =
      "tampered-context-aad".getBytes(StandardCharsets.UTF_8);

  @BeforeEach
  void resetState() {
    CryptoUtils.resetForTesting();
    System.clearProperty(CryptoUtils.ALGORITHM_SYSTEM_PROPERTY);
  }

  @AfterEach
  void cleanUp() {
    CryptoUtils.resetForTesting();
    System.clearProperty(CryptoUtils.ALGORITHM_SYSTEM_PROPERTY);
  }

  @Test
  @DisplayName("AES-256-GCM 字符串往返：加密后可解密还原（Base64 编解码）")
  void stringRoundTrip() {
    byte[] key = CryptoUtils.provider().generateKey();

    String ciphertext = CryptoUtils.encrypt(PLAINTEXT, key);
    String decrypted = CryptoUtils.decrypt(ciphertext, key);

    assertThat(decrypted).isEqualTo(PLAINTEXT);
  }

  @Test
  @DisplayName("字节级往返：带 AAD 加密后以相同 AAD 解密还原")
  void bytesRoundTripWithAad() {
    CryptoProvider provider = CryptoUtils.provider();
    byte[] key = provider.generateKey();
    byte[] plaintext = PLAINTEXT.getBytes(StandardCharsets.UTF_8);

    byte[] ciphertext = CryptoUtils.encryptBytes(plaintext, key, AAD);
    byte[] decrypted = CryptoUtils.decryptBytes(ciphertext, key, AAD);

    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  @DisplayName("AAD 篡改检测：解密时 AAD 不一致必须抛 CryptoException")
  void aadTamperDetection() {
    CryptoProvider provider = CryptoUtils.provider();
    byte[] key = provider.generateKey();
    byte[] ciphertext =
        CryptoUtils.encryptBytes(PLAINTEXT.getBytes(StandardCharsets.UTF_8), key, AAD);

    assertThatThrownBy(() -> CryptoUtils.decryptBytes(ciphertext, key, TAMPERED_AAD))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("tampered");
  }

  @Test
  @DisplayName("密文篡改检测：翻转密文字节后解密必须失败")
  void ciphertextTamperDetection() {
    CryptoProvider provider = CryptoUtils.provider();
    byte[] key = provider.generateKey();
    byte[] ciphertext =
        CryptoUtils.encryptBytes(PLAINTEXT.getBytes(StandardCharsets.UTF_8), key, null);

    ciphertext[ciphertext.length - 1] ^= 0x01;

    assertThatThrownBy(() -> CryptoUtils.decryptBytes(ciphertext, key, null))
        .isInstanceOf(CryptoException.class);
  }

  @Test
  @DisplayName("IV 随机性：同一明文两次加密产生不同密文（语义安全）")
  void randomIvProducesDistinctCiphertexts() {
    CryptoProvider provider = CryptoUtils.provider();
    byte[] key = provider.generateKey();
    byte[] plaintext = PLAINTEXT.getBytes(StandardCharsets.UTF_8);

    byte[] first = CryptoUtils.encryptBytes(plaintext, key, null);
    byte[] second = CryptoUtils.encryptBytes(plaintext, key, null);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName("密钥长度校验：错误长度密钥直接拒绝")
  void keyLengthValidation() {
    CryptoProvider provider = CryptoUtils.provider();
    byte[] wrongLengthKey = new byte[15];

    assertThatThrownBy(
            () -> CryptoUtils.encryptBytes(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                wrongLengthKey, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Key length");
    assertThatThrownBy(() -> provider.decrypt(new byte[32], wrongLengthKey, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("密文长度校验：短于 IV+Tag 最小长度直接拒绝")
  void ciphertextLengthValidation() {
    CryptoProvider provider = CryptoUtils.provider();
    byte[] key = provider.generateKey();
    byte[] tooShort = new byte[10];

    assertThatThrownBy(() -> provider.decrypt(tooShort, key, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("too short");
  }

  @Test
  @DisplayName("算法路由：Spring 注入优先级生效（无系统属性时）")
  void algorithmRoutingUsesInjectedValue() {
    CryptoUtils.setDefaultAlgorithm("AES-128-GCM");

    assertThat(CryptoUtils.provider().algorithm()).isEqualTo("AES-128-GCM");
  }

  @Test
  @DisplayName("算法路由：系统属性优先级高于 Spring 注入")
  void systemPropertyOverridesInjection() {
    CryptoUtils.setDefaultAlgorithm("AES-128-GCM");
    System.setProperty(CryptoUtils.ALGORITHM_SYSTEM_PROPERTY, "AES-192-GCM");

    assertThat(CryptoUtils.provider().algorithm()).isEqualTo("AES-192-GCM");
  }

  @Test
  @DisplayName("默认算法：未注入未配置时回退 AES-256-GCM")
  void defaultAlgorithmFallback() {
    assertThat(CryptoUtils.provider().algorithm()).isEqualTo("AES-256-GCM");
  }

  @Test
  @DisplayName("setDefaultAlgorithm 仅允许注入一次：重复注入被忽略并保持原值")
  void repeatInjectionIsIgnored() {
    CryptoUtils.setDefaultAlgorithm("AES-128-GCM");
    CryptoUtils.setDefaultAlgorithm("AES-256-GCM");

    assertThat(CryptoUtils.provider().algorithm())
        .as("第二次注入应被忽略，保持首次值")
        .isEqualTo("AES-128-GCM");
  }

  @Test
  @DisplayName("resetForTesting 后允许重新注入（Spring TestContext 缓存复用场景）")
  void resetForTestingAllowsReinjection() {
    CryptoUtils.setDefaultAlgorithm("AES-128-GCM");
    CryptoUtils.resetForTesting();
    CryptoUtils.setDefaultAlgorithm("AES-256-GCM");

    assertThat(CryptoUtils.provider().algorithm()).isEqualTo("AES-256-GCM");
  }

  @Test
  @DisplayName("注册表：AES-128/192/256-GCM 始终可用")
  void aesAlgorithmsAlwaysRegistered() {
    assertThat(CryptoUtils.availableAlgorithms())
        .contains("AES-128-GCM", "AES-192-GCM", "AES-256-GCM");
  }

  @Test
  @DisplayName("注册表：未知算法抛出异常并列出可用算法")
  void unknownAlgorithmRejected() {
    assertThatThrownBy(() -> CryptoUtils.provider("RSA-4096"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported crypto algorithm");
  }

  @Test
  @DisplayName("AES 构造器：非法 keyBits 直接拒绝")
  void aesConstructorValidation() {
    assertThatThrownBy(() -> new AesGcmCryptoProvider(100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("keyBits");
  }
}
