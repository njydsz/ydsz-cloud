package com.remisoft.common.util.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Rsa2Utils 单元测试
 *
 * <p>覆盖：密钥生成、公钥加密/私钥解密、私钥签名/公钥验签、PEM 格式转换、密钥对验证、超长文本分段加解密。
 */
class Rsa2UtilsTest {

    private static String publicKeyBase64;
    private static String privateKeyBase64;

    @BeforeAll
    static void setUp() {
        Map<String, String> keyPair = Rsa2Utils.initRSAKey(2048);
        publicKeyBase64 = keyPair.get("publicKey");
        privateKeyBase64 = keyPair.get("privateKey");
    }

    @Nested
    @DisplayName("密钥生成")
    class KeyGeneration {

        @Test
        @DisplayName("initRSAKey() 应生成默认长度的密钥对")
        void initRSAKeyDefault_shouldReturnValidKeyPair() {
            Map<String, String> keyPair = Rsa2Utils.initRSAKey();
            assertNotNull(keyPair);
            assertNotNull(keyPair.get("publicKey"));
            assertNotNull(keyPair.get("privateKey"));
            assertFalse(keyPair.get("publicKey").isBlank());
            assertFalse(keyPair.get("privateKey").isBlank());
        }

        @Test
        @DisplayName("initRSAKey(2048) 应生成 2048 位密钥对")
        void initRSAKey2048_shouldReturnValidKeyPair() {
            Map<String, String> keyPair = Rsa2Utils.initRSAKey(2048);
            assertNotNull(keyPair.get("publicKey"));
            assertNotNull(keyPair.get("privateKey"));
        }

        @Test
        @DisplayName("initRSAKey(1024) 应抛出异常（密钥长度不足）")
        void initRSAKey1024_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, () -> Rsa2Utils.initRSAKey(1024));
        }

        @Test
        @DisplayName("多次生成的密钥对应不同")
        void multipleKeyPairs_shouldBeDifferent() {
            Map<String, String> pair1 = Rsa2Utils.initRSAKey(2048);
            Map<String, String> pair2 = Rsa2Utils.initRSAKey(2048);
            assertNotEquals(pair1.get("publicKey"), pair2.get("publicKey"));
            assertNotEquals(pair1.get("privateKey"), pair2.get("privateKey"));
        }
    }

    @Nested
    @DisplayName("公钥加密/私钥解密")
    class EncryptDecrypt {

        @Test
        @DisplayName("基本加密解密应还原原文")
        void encryptDecryptRoundtrip_shouldReturnOriginal() {
            String plaintext = "Hello, RSA2!";
            String encrypted = Rsa2Utils.encryptByPublicKey(plaintext, publicKeyBase64);
            String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("空字符串加密解密应正常")
        void encryptDecryptEmpty_shouldWork() {
            String plaintext = "";
            String encrypted = Rsa2Utils.encryptByPublicKey(plaintext, publicKeyBase64);
            String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("中长文本（接近单块上限）应加密解密成功")
        void encryptDecryptMediumText_shouldWork() {
            // OAEP SHA-256: 2048 位密钥最大加密 190 字节
            String plaintext = "a".repeat(190);
            String encrypted = Rsa2Utils.encryptByPublicKey(plaintext, publicKeyBase64);
            String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("超长文本（需分段）应加密解密成功")
        void encryptDecryptLongText_shouldWork() {
            // 分段边界：190 字节/块，500 字节需 3 块
            String plaintext = "a".repeat(500);
            String encrypted = Rsa2Utils.encryptByPublicKey(plaintext, publicKeyBase64);
            String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("超长文本（边界值：380 字节 = 2 块）应加密解密成功")
        void encryptDecryptBoundaryText_shouldWork() {
            String plaintext = "a".repeat(380);
            String encrypted = Rsa2Utils.encryptByPublicKey(plaintext, publicKeyBase64);
            String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, privateKeyBase64);
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("错误私钥解密应抛出异常")
        void decryptWithWrongKey_shouldThrowException() {
            Map<String, String> otherKeyPair = Rsa2Utils.initRSAKey(2048);
            String plaintext = "Hello";
            String encrypted = Rsa2Utils.encryptByPublicKey(plaintext, publicKeyBase64);
            assertThrows(RuntimeException.class, () ->
                Rsa2Utils.decryptByPrivateKey(encrypted, otherKeyPair.get("privateKey")));
        }

        @Test
        @DisplayName("非法 Base64 密文解密应抛出异常")
        void decryptInvalidBase64_shouldThrowException() {
            assertThrows(RuntimeException.class, () ->
                Rsa2Utils.decryptByPrivateKey("not-valid-base64!!!", privateKeyBase64));
        }
    }

    @Nested
    @DisplayName("私钥签名/公钥验签")
    class SignVerify {

        @Test
        @DisplayName("签名并验签应返回 true")
        void signThenVerify_shouldReturnTrue() {
            String data = "test data for signing";
            String signature = Rsa2Utils.sign(data, privateKeyBase64);
            assertTrue(Rsa2Utils.verify(data, publicKeyBase64, signature));
        }

        @Test
        @DisplayName("验签被篡改的数据应返回 false")
        void verifyTamperedData_shouldReturnFalse() {
            String data = "original data";
            String signature = Rsa2Utils.sign(data, privateKeyBase64);
            assertFalse(Rsa2Utils.verify("tampered data", publicKeyBase64, signature));
        }

        @Test
        @DisplayName("错误公钥验签应返回 false")
        void verifyWithWrongKey_shouldReturnFalse() {
            String data = "test data";
            String signature = Rsa2Utils.sign(data, privateKeyBase64);
            Map<String, String> otherKeyPair = Rsa2Utils.initRSAKey(2048);
            assertFalse(Rsa2Utils.verify(data, otherKeyPair.get("publicKey"), signature));
        }

        @Test
        @DisplayName("签名应非空且格式正确")
        void signature_shouldBeValidBase64() {
            String signature = Rsa2Utils.sign("test", privateKeyBase64);
            assertNotNull(signature);
            assertFalse(signature.isBlank());
            assertDoesNotThrow(() -> Base64.getDecoder().decode(signature));
        }
    }

    @Nested
    @DisplayName("PEM 格式转换")
    class PemConversion {

        @Test
        @DisplayName("公钥 Base64 ↔ PEM 应可逆")
        void publicKeyPemRoundtrip_shouldWork() {
            String pem = Rsa2Utils.publicKeyToPEM(publicKeyBase64);
            assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"));
            assertTrue(pem.endsWith("-----END PUBLIC KEY-----"));
            PublicKey publicKey = assertDoesNotThrow(() -> Rsa2Utils.loadPublicKeyFromPEM(pem));
            assertNotNull(publicKey);
        }

        @Test
        @DisplayName("私钥 Base64 ↔ PEM 应可逆")
        void privateKeyPemRoundtrip_shouldWork() {
            String pem = Rsa2Utils.privateKeyToPEM(privateKeyBase64);
            assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"));
            assertTrue(pem.endsWith("-----END PRIVATE KEY-----"));
            PrivateKey privateKey = assertDoesNotThrow(() -> Rsa2Utils.loadPrivateKeyFromPEM(pem));
            assertNotNull(privateKey);
        }

        @Test
        @DisplayName("PEM 加载后应能正确加解密")
        void loadedFromPem_shouldEncryptDecryptCorrectly() {
            String publicPem = Rsa2Utils.publicKeyToPEM(publicKeyBase64);
            String privatePem = Rsa2Utils.privateKeyToPEM(privateKeyBase64);
            PublicKey pubKey = Rsa2Utils.loadPublicKeyFromPEM(publicPem);
            PrivateKey priKey = Rsa2Utils.loadPrivateKeyFromPEM(privatePem);

            String pubKeyB64 = Base64.getEncoder().encodeToString(pubKey.getEncoded());
            String priKeyB64 = Base64.getEncoder().encodeToString(priKey.getEncoded());

            String plaintext = "test after PEM roundtrip";
            String encrypted = Rsa2Utils.encryptByPublicKey(plaintext, pubKeyB64);
            String decrypted = Rsa2Utils.decryptByPrivateKey(encrypted, priKeyB64);
            assertEquals(plaintext, decrypted);
        }
    }

    @Nested
    @DisplayName("密钥对验证")
    class KeyPairVerification {

        @Test
        @DisplayName("匹配的密钥对验证应返回 true")
        void verifyMatchingKeyPair_shouldReturnTrue() {
            assertTrue(Rsa2Utils.verifyKeyPair(publicKeyBase64, privateKeyBase64));
        }

        @Test
        @DisplayName("不匹配的密钥对验证应返回 false")
        void verifyMismatchedKeyPair_shouldReturnFalse() {
            Map<String, String> otherKeyPair = Rsa2Utils.initRSAKey(2048);
            assertFalse(Rsa2Utils.verifyKeyPair(publicKeyBase64, otherKeyPair.get("privateKey")));
        }
    }

    @Nested
    @DisplayName("密钥缓存")
    class KeyCache {

        @Test
        @DisplayName("重复加载同一公钥应返回等价的 PublicKey 实例")
        void loadPublicKeyTwice_shouldReturnSameInstance() {
            PublicKey key1 = assertDoesNotThrow(() -> Rsa2Utils.loadPublicKey(publicKeyBase64));
            PublicKey key2 = assertDoesNotThrow(() -> Rsa2Utils.loadPublicKey(publicKeyBase64));
            // LRU 缓存命中应返回同一实例
            assertTrue(key1 == key2);
        }

        @Test
        @DisplayName("重复加载同一私钥应返回等价的 PrivateKey 实例")
        void loadPrivateKeyTwice_shouldReturnSameInstance() {
            PrivateKey key1 = assertDoesNotThrow(() -> Rsa2Utils.loadPrivateKey(privateKeyBase64));
            PrivateKey key2 = assertDoesNotThrow(() -> Rsa2Utils.loadPrivateKey(privateKeyBase64));
            assertTrue(key1 == key2);
        }

        @Test
        @DisplayName("加载空密钥应抛出异常")
        void loadEmptyKey_shouldThrowException() {
            assertThrows(Exception.class, () -> Rsa2Utils.loadPublicKey(""));
            assertThrows(Exception.class, () -> Rsa2Utils.loadPrivateKey(""));
        }

        @Test
        @DisplayName("加载非法 Base64 密钥应抛出异常")
        void loadInvalidBase64Key_shouldThrowException() {
            assertThrows(Exception.class, () -> Rsa2Utils.loadPublicKey("!!!invalid!!!"));
            assertThrows(Exception.class, () -> Rsa2Utils.loadPrivateKey("!!!invalid!!!"));
        }
    }
}
