package com.njydsz.common.util.security.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KeyProvider SPI（密钥来源注册表）测试。
 *
 * <p>覆盖：注册/替换/注销语义、按 keyId 解析、未注册时的错误指引、
 * 提供方异常包装、与 CryptoUtils 密钥标识 API 的端到端往返。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class KeyProviderRegistryTest {

  /** 测试密钥标识 */
  private static final String KEY_ID = "test-key-v1";

  @BeforeEach
  @AfterEach
  void resetRegistry() {
    KeyProviderRegistry.unregister();
    CryptoUtils.resetForTesting();
    System.clearProperty(CryptoUtils.ALGORITHM_SYSTEM_PROPERTY);
  }

  @Test
  @DisplayName("注册与解析：按 keyId 取回密钥")
  void registerAndResolve() {
    byte[] expected = CryptoUtils.provider().generateKey();
    KeyProviderRegistry.register(keyId -> expected);

    assertThat(KeyProviderRegistry.isRegistered()).isTrue();
    assertThat(KeyProviderRegistry.resolve(KEY_ID)).isEqualTo(expected);
  }

  @Test
  @DisplayName("替换语义：后注册者覆盖先注册者（密钥来源切换是合法运维动作）")
  void laterRegistrationReplacesEarlier() {
    byte[] first = CryptoUtils.provider().generateKey();
    byte[] second = CryptoUtils.provider().generateKey();
    KeyProviderRegistry.register(keyId -> first);
    KeyProviderRegistry.register(keyId -> second);

    assertThat(KeyProviderRegistry.resolve(KEY_ID)).isEqualTo(second);
  }

  @Test
  @DisplayName("未注册时解析：抛 CryptoException 并给出注册指引")
  void resolveWithoutRegistrationFailsWithGuidance() {
    assertThatThrownBy(() -> KeyProviderRegistry.resolve(KEY_ID))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("KeyProvider");
  }

  @Test
  @DisplayName("提供方返回 null 密钥：包装为带 keyId 的 CryptoException")
  void nullKeyFromProviderRejected() {
    KeyProviderRegistry.register(keyId -> null);

    assertThatThrownBy(() -> KeyProviderRegistry.resolve(KEY_ID))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining(KEY_ID);
  }

  @Test
  @DisplayName("提供方抛异常：包装为 CryptoException 且不丢失原始原因")
  void providerExceptionWrapped() {
    KeyProviderRegistry.register(
        keyId -> {
          throw new IllegalStateException("kms unreachable");
        });

    assertThatThrownBy(() -> KeyProviderRegistry.resolve(KEY_ID))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining(KEY_ID)
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("register(null)：直接拒绝")
  void nullRegistrationRejected() {
    assertThatThrownBy(() -> KeyProviderRegistry.register(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("端到端：encryptWithKeyId/decryptWithKeyId 往返还原")
  void encryptWithKeyIdRoundTrip() {
    byte[] key = CryptoUtils.provider().generateKey();
    KeyProviderRegistry.register(keyId -> key);

    String plaintext = "KeyProvider SPI 端到端测试";
    String ciphertext = CryptoUtils.encryptWithKeyId(plaintext, KEY_ID);
    String decrypted = CryptoUtils.decryptWithKeyId(ciphertext, KEY_ID);

    assertThat(decrypted).isEqualTo(plaintext);
    assertThat(ciphertext).isNotEqualTo(plaintext);
  }

  @Test
  @DisplayName("端到端：encryptWithKeyIdAndAad 带 AAD 往返且 AAD 不匹配时失败")
  void encryptWithKeyIdAndAadRoundTrip() {
    byte[] key = CryptoUtils.provider().generateKey();
    KeyProviderRegistry.register(keyId -> key);
    byte[] aad = "tenant-42".getBytes();

    String plaintext = "带 AAD 的密钥标识加密";
    String ciphertext = CryptoUtils.encryptWithKeyIdAndAad(plaintext, KEY_ID, aad);

    assertThat(CryptoUtils.decryptWithKeyIdAndAad(ciphertext, KEY_ID, aad)).isEqualTo(plaintext);
    assertThatThrownBy(
            () ->
                CryptoUtils.decryptWithKeyIdAndAad(
                    ciphertext, KEY_ID, "wrong-tenant".getBytes()))
        .isInstanceOf(CryptoException.class);
  }
}
