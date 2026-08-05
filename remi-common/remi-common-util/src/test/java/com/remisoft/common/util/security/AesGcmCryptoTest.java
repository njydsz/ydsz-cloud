package com.remisoft.common.util.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
/**
 * {@link AesGcmCrypto} 单元测试 — 覆盖加解密往返、密钥校验、密文篡改检测等关键路径。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("AesGcmCrypto 测试")
class AesGcmCryptoTest {

    private static byte[] genKey(int len) {
        byte[] key = new byte[len];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Nested
    @DisplayName("密钥校验")
    /**
     * 测试分组：密钥校验
     */
    class KeyValidation {

        @Test
        @DisplayName("16 字节密钥合法")
        void aes128KeyShouldBeAccepted() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(16));
            assertThat(crypto).isNotNull();
        }

        @Test
        @DisplayName("24 字节密钥合法")
        void aes192KeyShouldBeAccepted() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(24));
            assertThat(crypto).isNotNull();
        }

        @Test
        @DisplayName("32 字节密钥合法")
        void aes256KeyShouldBeAccepted() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            assertThat(crypto).isNotNull();
        }

        @Test
        @DisplayName("非法密钥长度（10）抛 IllegalArgumentException")
        void invalidKeyLengthShouldThrow() {
            assertThatThrownBy(() -> new AesGcmCrypto(genKey(10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AES key length must be 16/24/32 bytes");
        }

        @Test
        @DisplayName("null 密钥抛 IllegalArgumentException")
        void nullKeyShouldThrow() {
            assertThatThrownBy(() -> new AesGcmCrypto(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("加解密往返")
    /**
     * 测试分组：加解密往返
     */
    class EncryptDecryptRoundtrip {

        @Test
        @DisplayName("ASCII 明文加解密一致")
        void asciiRoundtrip() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            String plaintext = "hello world";
            String ciphertext = crypto.encrypt(plaintext);
            assertThat(crypto.decrypt(ciphertext)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("中文明文加解密一致")
        void chineseRoundtrip() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            String plaintext = "你好，世界！AES-GCM 测试 αβγ";
            String ciphertext = crypto.encrypt(plaintext);
            assertThat(crypto.decrypt(ciphertext)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("空字符串加解密一致")
        void emptyStringRoundtrip() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(16));
            String ciphertext = crypto.encrypt("");
            assertThat(crypto.decrypt(ciphertext)).isEmpty();
        }

        @Test
        @DisplayName("每次加密的密文都不同（IV 随机性）")
        void ciphertextShouldDifferEachTime() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            String plaintext = "deterministic-input";
            String ct1 = crypto.encrypt(plaintext);
            String ct2 = crypto.encrypt(plaintext);
            String ct3 = crypto.encrypt(plaintext);
            assertThat(ct1).isNotEqualTo(ct2);
            assertThat(ct2).isNotEqualTo(ct3);
            // 三次解密结果一致
            assertThat(crypto.decrypt(ct1)).isEqualTo(plaintext);
            assertThat(crypto.decrypt(ct2)).isEqualTo(plaintext);
            assertThat(crypto.decrypt(ct3)).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("长文本（10KB）加解密一致")
        void largePayloadRoundtrip() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            byte[] data = new byte[10 * 1024];
            new SecureRandom().nextBytes(data);
            String plaintext = new String(data, StandardCharsets.ISO_8859_1);
            String ciphertext = crypto.encrypt(plaintext);
            assertThat(crypto.decrypt(ciphertext)).isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("密文篡改检测")
    /**
     * 测试分组：密文篡改检测
     */
    class TamperDetection {

        @Test
        @DisplayName("篡改密文应解密失败")
        void tamperedCiphertextShouldFail() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            String ciphertext = crypto.encrypt("sensitive-data");
            // 翻转最后一个字符以模拟篡改
            char[] chars = ciphertext.toCharArray();
            chars[chars.length - 1] = (chars[chars.length - 1] == 'A') ? 'B' : 'A';
            String tampered = new String(chars);
            assertThatThrownBy(() -> crypto.decrypt(tampered))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES-GCM decryption failed");
        }

        @Test
        @DisplayName("密文长度过短抛 IllegalArgumentException")
        void tooShortCiphertextShouldThrow() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            // 12 byte IV + 16 byte tag = 28 byte，对应 Base64 是 40 字符
            // 27 字节（少 1 字节）即触发校验
            byte[] shortBytes = new byte[27];
            new SecureRandom().nextBytes(shortBytes);
            String shortCiphertext = Base64.getEncoder().encodeToString(shortBytes);
            assertThatThrownBy(() -> crypto.decrypt(shortCiphertext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid ciphertext length");
        }

        @Test
        @DisplayName("null 密文抛 NullPointerException")
        void nullCiphertextShouldThrow() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            assertThatThrownBy(() -> crypto.decrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null 明文抛 NullPointerException")
        void nullPlaintextShouldThrow() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            assertThatThrownBy(() -> crypto.encrypt(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("AAD 认证加密")
    /**
     * 测试分组：AAD 认证加密
     */
    class AadAuthenticatedEncryption {

        @Test
        @DisplayName("AAD 加解密往返一致")
        void aadRoundtrip() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            byte[] aad = "request-id=abc-123".getBytes(StandardCharsets.UTF_8);
            String ct = crypto.encrypt("sensitive-data", aad);
            assertThat(crypto.decrypt(ct, aad)).isEqualTo("sensitive-data");
        }

        @Test
        @DisplayName("AAD 不匹配应解密失败")
        void aadMismatchShouldFail() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            byte[] aadEncrypt = "user-id=42".getBytes(StandardCharsets.UTF_8);
            byte[] aadDecrypt = "user-id=99".getBytes(StandardCharsets.UTF_8);
            String ct = crypto.encrypt("sensitive-data", aadEncrypt);
            assertThatThrownBy(() -> crypto.decrypt(ct, aadDecrypt))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES-GCM decryption failed");
        }

        @Test
        @DisplayName("AAD 加密 -> 无 AAD 解密应失败")
        void aadEncryptedWithoutAadDecryptShouldFail() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            byte[] aad = "context".getBytes(StandardCharsets.UTF_8);
            String ct = crypto.encrypt("data", aad);
            assertThatThrownBy(() -> crypto.decrypt(ct))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES-GCM decryption failed");
        }

        @Test
        @DisplayName("无 AAD 加密 -> AAD 解密应失败")
        void noAadEncryptWithAadDecryptShouldFail() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            String ct = crypto.encrypt("data");
            assertThatThrownBy(() -> crypto.decrypt(ct, "extra".getBytes(StandardCharsets.UTF_8)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AES-GCM decryption failed");
        }

        @Test
        @DisplayName("空 AAD 等价于无 AAD")
        void emptyAadEqualsNoAad() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            String ctWithEmptyAad = crypto.encrypt("data", new byte[0]);
            String ctWithoutAad = crypto.encrypt("data");
            assertThat(crypto.decrypt(ctWithEmptyAad)).isEqualTo("data");
            assertThat(crypto.decrypt(ctWithoutAad)).isEqualTo("data");
        }

        @Test
        @DisplayName("大 AAD（1KB）加解密一致")
        void largeAadRoundtrip() {
            AesGcmCrypto crypto = new AesGcmCrypto(genKey(32));
            byte[] largeAad = new byte[1024];
            new SecureRandom().nextBytes(largeAad);
            String ct = crypto.encrypt("payload", largeAad);
            assertThat(crypto.decrypt(ct, largeAad)).isEqualTo("payload");
        }
    }
}
