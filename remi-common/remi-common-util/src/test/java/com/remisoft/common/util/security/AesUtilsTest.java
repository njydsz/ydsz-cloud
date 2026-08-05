package com.remisoft.common.util.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link AesUtils} 单元测试 — 覆盖配置密钥管理、工具函数等近期新增/修复行为。
 *
 * @author remi-team
 * @since 1.3.0
 */
@DisplayName("AesUtils 工具类测试")
class AesUtilsTest {

    @AfterEach
    @BeforeEach
    void resetConfiguredKey() {
        // 清空配置密钥，避免测试间相互影响（反射清除 AtomicReference）
        try {
            java.lang.reflect.Field keyField = AesUtils.class.getDeclaredField("configuredKey");
            keyField.setAccessible(true);
            keyField.set(null, null);

            java.lang.reflect.Field cryptoField = AesUtils.class.getDeclaredField("configuredCryptoRef");
            cryptoField.setAccessible(true);
            ((java.util.concurrent.atomic.AtomicReference<?>) cryptoField.get(null)).set(null);
        } catch (Exception ignored) {
            // 反射失败时忽略，测试本身会覆盖配置
        }
    }

    @Nested
    @DisplayName("配置密钥管理")
    class ConfiguredKey {

        @Test
        @DisplayName("未配置密钥时 getConfiguredKey 抛 IllegalStateException")
        void shouldThrowWhenNotConfigured() {
            assertThatThrownBy(AesUtils::getConfiguredKey)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES 密钥未配置");
        }

        @Test
        @DisplayName("setConfiguredKey 后 getConfiguredKey 返回一致")
        void shouldReturnSameKeyAfterSet() {
            String key = AesUtils.initHexKey();
            AesUtils.setConfiguredKey(key);
            assertThat(AesUtils.getConfiguredKey()).isEqualTo(key);
        }

        @Test
        @DisplayName("setConfiguredKey 拒绝 null")
        void shouldRejectNullKey() {
            assertThatThrownBy(() -> AesUtils.setConfiguredKey(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("setConfiguredKey 拒绝非法长度")
        void shouldRejectInvalidKeyLength() {
            assertThatThrownBy(() -> AesUtils.setConfiguredKey("AABBCCDDEE"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("配置密钥后可正常使用 encrypt/decrypt")
        void shouldEncryptDecryptWithConfiguredKey() throws GeneralSecurityException {
            AesUtils.setConfiguredKey(AesUtils.initHexKey());
            String plaintext = "configured-key-test";
            String ciphertext = AesUtils.encrypt(plaintext, AesUtils.getConfiguredKey());
            assertThat(AesUtils.decrypt(ciphertext, AesUtils.getConfiguredKey())).isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("Hex/Base64 工具方法")
    class EncodingUtils {

        @Test
        @DisplayName("bytesToHex / hexToBytes 往返一致")
        void hexRoundtrip() {
            byte[] original = new byte[]{0x00, 0x0F, (byte) 0xFF, 0x10, 0x7A};
            String hex = AesUtils.bytesToHex(original);
            byte[] restored = AesUtils.hexToBytes(hex);
            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("hexToBytes 拒绝 null")
        void hexToBytesShouldRejectNull() {
            assertThatThrownBy(() -> AesUtils.hexToBytes(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("hexToBytes 拒绝奇数长度")
        void hexToBytesShouldRejectOddLength() {
            assertThatThrownBy(() -> AesUtils.hexToBytes("ABC"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("base64Encode / base64Decode 往返一致")
        void base64Roundtrip() {
            byte[] original = "hello".getBytes();
            String encoded = AesUtils.base64Encode(original);
            byte[] decoded = AesUtils.base64Decode(encoded);
            assertThat(decoded).isEqualTo(original);
        }

        @Test
        @DisplayName("base64Encode(null) 返回 null")
        void base64EncodeNullShouldReturnNull() {
            assertThat(AesUtils.base64Encode(null)).isNull();
        }

        @Test
        @DisplayName("base64Decode(null) 返回 null")
        void base64DecodeNullShouldReturnNull() {
            assertThat(AesUtils.base64Decode(null)).isNull();
        }
    }

    @Nested
    @DisplayName("密钥生成")
    class KeyGeneration {

        @Test
        @DisplayName("initHexKey() 返回 64 字符 Hex")
        void defaultHexKeyLength() {
            String key = AesUtils.initHexKey();
            assertThat(key).hasSize(64); // 256 bit = 32 byte = 64 hex chars
        }

        @Test
        @DisplayName("initHexKey(128) 返回 32 字符 Hex")
        void aes128HexKeyLength() {
            String key = AesUtils.initHexKey(128);
            assertThat(key).hasSize(32);
        }

        @Test
        @DisplayName("initHexKey(192) 返回 48 字符 Hex")
        void aes192HexKeyLength() {
            String key = AesUtils.initHexKey(192);
            assertThat(key).hasSize(48);
        }

        @Test
        @DisplayName("initKey 非法长度抛 IllegalArgumentException")
        void initKeyInvalidSizeShouldThrow() {
            assertThatThrownBy(() -> AesUtils.initKey(64))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be 128, 192, or 256");
        }

        @Test
        @DisplayName("每次 initHexKey() 生成不同密钥")
        void keysShouldDiffer() {
            assertThat(AesUtils.initHexKey()).isNotEqualTo(AesUtils.initHexKey());
        }
    }

    @Nested
    @DisplayName("GCM 加解密（使用非配置密钥）")
    class GcmEncryptDecrypt {

        @Test
        @DisplayName("GCM 加解密往返一致")
        void roundtripWithHexKey() throws GeneralSecurityException {
            String key = AesUtils.initHexKey();
            String plaintext = "你好，Remi";
            String ciphertext = AesUtils.encrypt(plaintext, key);
            assertThat(AesUtils.decrypt(ciphertext, key)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("decrypt 篡改密文后抛 IllegalStateException")
        void tamperedCiphertextShouldFail() throws GeneralSecurityException {
            String key = AesUtils.initHexKey();
            String ciphertext = AesUtils.encrypt("test", key);

            // 篡改 Base64 末尾
            char[] chars = ciphertext.toCharArray();
            chars[chars.length - 1] = (chars[chars.length - 1] == 'A') ? 'B' : 'A';
            String tampered = new String(chars);

            assertThatThrownBy(() -> AesUtils.decrypt(tampered, key))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES-GCM decryption failed");
        }

        @Test
        @DisplayName("encrypt 非法密钥长度抛异常")
        void encryptWithInvalidKeyShouldThrow() {
            assertThatThrownBy(() -> AesUtils.encrypt("test", "00FF"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
