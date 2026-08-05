package com.remisoft.common.util.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link Rsa2Utils} 单元测试 — 覆盖密钥生成、OAEP 加解密、SHA256withRSA 签名验签。
 *
 * <p>使用 2048 位密钥平衡测试速度与安全性覆盖。
 *
 * @author remi-team
 * @since 1.3.0
 */
@DisplayName("Rsa2Utils RSA2 工具类测试")
class Rsa2UtilsTest {

    /**
     * 生成 2048 位 RSA 密钥对，用于测试。
     */
    private static Map<String, String> generateKeyPair2048() {
        return Rsa2Utils.initRSAKey(2048);
    }

    @Nested
    @DisplayName("密钥生成")
    class KeyGeneration {

        @Test
        @DisplayName("initRSAKey() 生成默认 2048 位密钥对")
        void shouldGenerateDefaultKey() {
            Map<String, String> keys = Rsa2Utils.initRSAKey();
            assertThat(keys).containsKeys("publicKey", "privateKey");
            assertThat(keys.get("publicKey")).isNotEmpty();
            assertThat(keys.get("privateKey")).isNotEmpty();
            assertThat(Base64.getDecoder().decode(keys.get("publicKey")).length).isGreaterThanOrEqualTo(290);
        }

        @Test
        @DisplayName("initRSAKey(2048) 通过校验")
        void shouldAccept2048() {
            assertThat(Rsa2Utils.initRSAKey(2048)).isNotEmpty();
        }

        @Test
        @DisplayName("initRSAKey(4096) 通过校验")
        void shouldAccept4096() {
            assertThat(Rsa2Utils.initRSAKey(4096)).isNotEmpty();
        }

        @Test
        @DisplayName("initRSAKey 拒绝 < 2048")
        void shouldRejectSmallKey() {
            assertThatThrownBy(() -> Rsa2Utils.initRSAKey(1024))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2048");
        }
    }

    @Nested
    @DisplayName("OAEP 公钥加密 / 私钥解密")
    class RsaEncryptDecrypt {

        @Test
        @DisplayName("加解密往返一致")
        void roundtrip() {
            Map<String, String> keys = generateKeyPair2048();
            String plaintext = "hello RSA-OAEP";
            String ciphertext = Rsa2Utils.encryptByPublicKey(plaintext, keys.get("publicKey"));
            assertThat(Rsa2Utils.decryptByPrivateKey(ciphertext, keys.get("privateKey")))
                    .isEqualTo(plaintext);
        }

        @Test
        @DisplayName("中文加解密往返一致")
        void chineseRoundtrip() {
            Map<String, String> keys = generateKeyPair2048();
            String plaintext = "你好，RSA αβγ 加密";
            String ciphertext = Rsa2Utils.encryptByPublicKey(plaintext, keys.get("publicKey"));
            assertThat(Rsa2Utils.decryptByPrivateKey(ciphertext, keys.get("privateKey")))
                    .isEqualTo(plaintext);
        }

        @Test
        @DisplayName("空字符串往返一致")
        void emptyStringRoundtrip() {
            Map<String, String> keys = generateKeyPair2048();
            String ciphertext = Rsa2Utils.encryptByPublicKey("", keys.get("publicKey"));
            assertThat(Rsa2Utils.decryptByPrivateKey(ciphertext, keys.get("privateKey")))
                    .isEmpty();
        }

        @Test
        @DisplayName("长文本（>190 字节）自动分段加密解密")
        void longTextRoundtrip() {
            Map<String, String> keys = generateKeyPair2048();
            String plaintext = "a".repeat(500);
            String ciphertext = Rsa2Utils.encryptByPublicKey(plaintext, keys.get("publicKey"));
            assertThat(Rsa2Utils.decryptByPrivateKey(ciphertext, keys.get("privateKey")))
                    .isEqualTo(plaintext);
        }

        @Test
        @DisplayName("每次加密结果不同（OAEP 随机填充）")
        void ciphertextShouldDiffer() {
            Map<String, String> keys = generateKeyPair2048();
            String plaintext = "deterministic-input";
            String ct1 = Rsa2Utils.encryptByPublicKey(plaintext, keys.get("publicKey"));
            String ct2 = Rsa2Utils.encryptByPublicKey(plaintext, keys.get("publicKey"));
            assertThat(ct1).isNotEqualTo(ct2);
            // 但都能正确解密
            assertThat(Rsa2Utils.decryptByPrivateKey(ct1, keys.get("privateKey"))).isEqualTo(plaintext);
            assertThat(Rsa2Utils.decryptByPrivateKey(ct2, keys.get("privateKey"))).isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("SHA256withRSA 签名 / 验签")
    class RsaSignature {

        @Test
        @DisplayName("签名后验签通过")
        void signThenVerify() {
            Map<String, String> keys = generateKeyPair2048();
            String data = "important-message";
            String signature = Rsa2Utils.sign(data, keys.get("privateKey"));
            assertThat(Rsa2Utils.verify(data, keys.get("publicKey", signature)).isTrue();
        }

        @Test
        @DisplayName("篡改数据后验签失败")
        void verifyWithTamperedDataShouldFail() {
            Map<String, String> keys = generateKeyPair2048();
            String data = "original";
            String signature = Rsa2Utils.sign(data, keys.get("privateKey"));
            assertThat(Rsa2Utils.verify("tampered", keys.get("publicKey"), signature)).isFalse();
        }

        @Test
        @DisplayName("不同密钥验签失败")
        void verifyWithDifferentKeyShouldFail() {
            Map<String, String> keys1 = generateKeyPair2048();
            Map<String, String> keys2 = generateKeyPair2048();
            String data = "test";
            String signature = Rsa2Utils.sign(data, keys1.get("privateKey"));
            assertThat(Rsa2Utils.verify(data, keys2.get("publicKey"), signature)).isFalse();
        }

        @Test
        @DisplayName("非法 Base64 签名格式返回 false（不抛异常）")
        void invalidSignatureFormatShouldReturnFalse() {
            Map<String, String> keys = generateKeyPair2048();
            assertThat(Rsa2Utils.verify("data", keys.get("publicKey"), "not-valid-base64!@#"))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("密钥格式转换")
    class PemFormatConversion {

        @Test
        @DisplayName("publicKey / privateKey to PEM 包含首尾标记")
        void pemShouldContainMarkers() {
            Map<String, String> keys = generateKeyPair2048();
            String pubPem = Rsa2Utils.publicKeyToPEM(keys.get("publicKey"));
            String priPem = Rsa2Utils.privateKeyToPEM(keys.get("privateKey"));
            assertThat(pubPem).contains("-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----");
            assertThat(priPem).contains("-----BEGIN PRIVATE KEY-----", "-----END private KEY-----");
        }

        @Test
        @DisplayName("从 PEM 加载并签名验签")
        void loadFromPEMAndUse() throws GeneralSecurityException {
            Map<String, String> keys = generateKeyPair2048();
            PublicKey publicKey = Rsa2Utils.loadPublicKeyFromPEM(
                    Rsa2Utils.publicKeyToPEM(keys.get("publicKey")));
            PrivateKey privateKey = Rsa2Utils.loadPrivateKeyFromPEM(
                    Rsa2Utils.privateKeyToPEM(keys.get("privateKey")));

            // 验证类型
            assertThat(publicKey).isNotNull();
            assertThat(privateKey).isNotNull();

            // 功能可用
            String pubBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            String priBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            String data = "pem-test";
            String signature = Rsa2Utils.sign(data, priBase64);
            assertThat(Rsa2Utils.verify(data, pubBase64, signature)).isTrue();
        }
    }

    @Nested
    @DisplayName("密钥对校验")
    class KeyPairVerification {

        @Test
        @DisplayName("合法密钥对 verifyKeyPair 返回 true")
        void validKeyPairShouldPass() {
            Map<String, String> keys = generateKeyPair2048();
            assertThat(Rsa2Utils.verifyKeyPair(keys.get("publicKey"), keys.get("privateKey"))).isTrue();
        }

        @Test
        @DisplayName("跨密钥对 verifyKeyPair 返回 false")
        void mismatchedKeyPairShouldFail() {
            Map<String, String> keys1 = generateKeyPair2048();
            Map<String, String> keys2 = generateKeyPair2048();
            assertThat(Rsa2Utils.verifyKeyPair(keys1.get("publicKey"), keys2.get("privateKey"))).isFalse();
        }
    }
}
