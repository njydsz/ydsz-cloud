package com.njydsz.pmis.common.util.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AesGcmCrypto 单元测试
 *
 * <p>覆盖 AES-GCM 加密/解密、密钥校验、IV/Nonce 持久化、密文篡改认证、
 * 中文/Emoji/特殊字符、错误密钥与 null 异常等核心场景。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@DisplayName("AesGcmCrypto - AES-GCM 认证加密器测试")
class AesGcmCryptoTest {

    /** 测试用 32 字节主密钥 */
    private static final byte[] KEY_256 = new byte[32];
    /** 测试用 16 字节主密钥 */
    private static final byte[] KEY_128 = new byte[16];

    static {
        SecureRandom random = new SecureRandom();
        random.nextBytes(KEY_256);
        random.nextBytes(KEY_128);
    }

    /** 内存型 Nonce 持久化器（保存 IV 以便复用） */
    private static class InMemoryNoncePersistor implements AesGcmCrypto.NoncePersistor {
        private final Map<String, byte[]> store = new HashMap<>();

        @Override
        public byte[] load(String keyId) {
            return store.get(keyId);
        }

        @Override
        public void save(String keyId, byte[] iv) {
            store.put(keyId, iv.clone());
        }
    }

    /** 空操作 Nonce 持久化器（从不保存，每次加密都生成新 IV） */
    private static class NoopNoncePersistor implements AesGcmCrypto.NoncePersistor {
        @Override
        public byte[] load(String keyId) {
            return null;
        }

        @Override
        public void save(String keyId, byte[] iv) {
            // 不保存，强制每次加密生成随机 IV
        }
    }

    // ==================== 加密 / 解密 ====================

    @Nested
    @DisplayName("加密与解密")
    class EncryptDecryptTest {

        private AesGcmCrypto crypto;

        @BeforeEach
        void setUp() {
            crypto = new AesGcmCrypto(KEY_256, new InMemoryNoncePersistor());
        }

        @Test
        @DisplayName("加密后解密应还原原始明文")
        void shouldEncryptAndDecryptSuccessfully() {
            String plaintext = "Hello, AES-GCM!";
            String ciphertext = crypto.encrypt(plaintext, "key-1");

            assertNotNull(ciphertext);
            assertNotEquals(plaintext, ciphertext);

            String decrypted = crypto.decrypt(ciphertext, "key-1");
            assertEquals(plaintext, decrypted);
        }

        @Test
        @DisplayName("加密结果为 Base64 字符串且解码后长度大于 IV+Tag")
        void shouldReturnBase64Ciphertext() {
            String ciphertext = crypto.encrypt("payload", "key-1");

            assertNotNull(ciphertext);
            // Base64 解码后长度至少为 IV(12) + GCM Tag(16) = 28 字节
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            assertTrue(decoded.length >= AesGcmCrypto.IV_LENGTH + AesGcmCrypto.GCM_TAG_LENGTH / 8);
        }

        @Test
        @DisplayName("空字符串加解密应正常工作")
        void shouldEncryptAndDecryptEmptyString() {
            String ciphertext = crypto.encrypt("", "key-1");
            assertNotNull(ciphertext);
            assertEquals("", crypto.decrypt(ciphertext, "key-1"));
        }
    }

    // ==================== 密钥与构造 ====================

    @Nested
    @DisplayName("密钥与构造方法")
    class KeyAndConstructorTest {

        @Test
        @DisplayName("SecureRandom 生成的 16/24/32 字节密钥均可正常加解密")
        void shouldAcceptGeneratedKeysOfValidLength() {
            SecureRandom random = new SecureRandom();
            for (int len : new int[]{16, 24, 32}) {
                byte[] key = new byte[len];
                random.nextBytes(key);
                AesGcmCrypto crypto = new AesGcmCrypto(key, new InMemoryNoncePersistor());
                String ct = crypto.encrypt("hello", "key-" + len);
                assertEquals("hello", crypto.decrypt(ct, "key-" + len));
            }
        }

        @Test
        @DisplayName("非法密钥长度（15 字节）抛出 IllegalArgumentException")
        void shouldThrowWhenKeyLengthInvalid() {
            byte[] badKey = new byte[15];
            assertThrows(IllegalArgumentException.class,
                () -> new AesGcmCrypto(badKey, new InMemoryNoncePersistor()));
        }

        @Test
        @DisplayName("null 密钥抛出 IllegalArgumentException")
        void shouldThrowWhenKeyIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> new AesGcmCrypto(null, new InMemoryNoncePersistor()));
        }

        @Test
        @DisplayName("null 持久化器抛出 NullPointerException")
        void shouldThrowWhenPersistorIsNull() {
            assertThrows(NullPointerException.class,
                () -> new AesGcmCrypto(KEY_256, null));
        }
    }

    // ==================== IV / Nonce ====================

    @Nested
    @DisplayName("IV / Nonce 生成与持久化")
    class IvAndNonceTest {

        @Test
        @DisplayName("未持久化 IV 时，每次加密产生不同密文")
        void shouldProduceDifferentCiphertextWithRandomIv() {
            AesGcmCrypto crypto = new AesGcmCrypto(KEY_256, new NoopNoncePersistor());
            String plaintext = "same-plaintext";

            String ct1 = crypto.encrypt(plaintext, "key-1");
            String ct2 = crypto.encrypt(plaintext, "key-1");

            assertNotEquals(ct1, ct2, "不同 IV 应产生不同密文");

            // 两者都应能正确解密
            assertEquals(plaintext, crypto.decrypt(ct1, "key-1"));
            assertEquals(plaintext, crypto.decrypt(ct2, "key-1"));
        }

        @Test
        @DisplayName("持久化 IV 时，相同密钥+IV 加密相同明文得到相同密文")
        void shouldProduceSameCiphertextForSameIvAndPlaintext() {
            AesGcmCrypto crypto = new AesGcmCrypto(KEY_256, new InMemoryNoncePersistor());
            String plaintext = "same-plaintext";

            // 第一次加密生成 IV 并持久化
            String ct1 = crypto.encrypt(plaintext, "key-1");
            // 第二次加密复用已持久化的 IV
            String ct2 = crypto.encrypt(plaintext, "key-1");

            assertEquals(ct1, ct2, "相同密钥+IV+明文应产生相同密文");
        }

        @Test
        @DisplayName("不同 keyId 持久化独立的 IV")
        void shouldPersistIndependentIvForEachKeyId() {
            InMemoryNoncePersistor persistor = new InMemoryNoncePersistor();
            AesGcmCrypto crypto = new AesGcmCrypto(KEY_256, persistor);
            String plaintext = "payload";

            String ct1 = crypto.encrypt(plaintext, "key-A");
            String ct2 = crypto.encrypt(plaintext, "key-B");

            // 两个 keyId 应有不同的 IV，密文不同
            assertNotEquals(ct1, ct2);
            // 各自能正确解密
            assertEquals(plaintext, crypto.decrypt(ct1, "key-A"));
            assertEquals(plaintext, crypto.decrypt(ct2, "key-B"));
        }
    }

    // ==================== 字符集 ====================

    @Nested
    @DisplayName("多字符集加解密")
    class CharsetTest {

        private AesGcmCrypto crypto;

        @BeforeEach
        void setUp() {
            crypto = new AesGcmCrypto(KEY_256, new InMemoryNoncePersistor());
        }

        @Test
        @DisplayName("中文字符加解密")
        void shouldEncryptAndDecryptChinese() {
            String plaintext = "瑞米软件·加密测试——你好，世界！";
            String ciphertext = crypto.encrypt(plaintext, "cn");
            assertEquals(plaintext, crypto.decrypt(ciphertext, "cn"));
        }

        @Test
        @DisplayName("Emoji 与特殊字符加解密")
        void shouldEncryptAndDecryptEmojiAndSpecialChars() {
            String plaintext = "Emoji: 😀🎉🔒 \t换行\n特殊符号 <>&\"' \\ / @#$%^&*()";
            String ciphertext = crypto.encrypt(plaintext, "emoji");
            assertEquals(plaintext, crypto.decrypt(ciphertext, "emoji"));
        }

        @Test
        @DisplayName("长文本加解密（10KB）")
        void shouldEncryptAndDecryptLongText() {
            StringBuilder sb = new StringBuilder(10240);
            for (int i = 0; i < 10240; i++) {
                sb.append('A');
            }
            String plaintext = sb.toString();
            String ciphertext = crypto.encrypt(plaintext, "long");
            assertEquals(plaintext, crypto.decrypt(ciphertext, "long"));
        }
    }

    // ==================== 篡改与认证 ====================

    @Nested
    @DisplayName("密文篡改与认证标签校验")
    class TamperAndAuthTest {

        private AesGcmCrypto crypto;

        @BeforeEach
        void setUp() {
            crypto = new AesGcmCrypto(KEY_256, new NoopNoncePersistor());
        }

        @Test
        @DisplayName("篡改密文内容导致解密失败（GCM 认证标签校验）")
        void shouldFailWhenCiphertextTampered() {
            String ciphertext = crypto.encrypt("secret", "key-1");
            // 翻转密文部分（IV 之后）某字节的最低位
            byte[] tampered = tamper(ciphertext, AesGcmCrypto.IV_LENGTH + 2);

            assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(Base64.getEncoder().encodeToString(tampered), "key-1"));
        }

        @Test
        @DisplayName("篡改 IV 导致解密失败")
        void shouldFailWhenIvTampered() {
            String ciphertext = crypto.encrypt("secret", "key-1");
            // 翻转 IV 首字节的最低位
            byte[] tampered = tamper(ciphertext, 0);

            assertThrows(IllegalStateException.class,
                () -> crypto.decrypt(Base64.getEncoder().encodeToString(tampered), "key-1"));
        }

        @Test
        @DisplayName("使用错误密钥解密失败")
        void shouldFailWithWrongKey() {
            AesGcmCrypto cryptoA = new AesGcmCrypto(KEY_256, new NoopNoncePersistor());
            AesGcmCrypto cryptoB = new AesGcmCrypto(KEY_128, new NoopNoncePersistor());

            String ciphertext = cryptoA.encrypt("secret", "key-1");

            assertThrows(IllegalStateException.class,
                () -> cryptoB.decrypt(ciphertext, "key-1"));
        }

        /** 解码 Base64 密文，翻转指定位置字节的最低位后返回新数组 */
        private byte[] tamper(String base64Ciphertext, int byteIndex) {
            byte[] bytes = Base64.getDecoder().decode(base64Ciphertext);
            bytes[byteIndex] ^= 0x01;
            return bytes;
        }
    }

    // ==================== 异常场景 ====================

    @Nested
    @DisplayName("异常输入与边界")
    class ExceptionTest {

        private AesGcmCrypto crypto;

        @BeforeEach
        void setUp() {
            crypto = new AesGcmCrypto(KEY_256, new InMemoryNoncePersistor());
        }

        @Test
        @DisplayName("encrypt(null, keyId) 抛出 NullPointerException")
        void shouldThrowWhenEncryptNullPlaintext() {
            assertThrows(NullPointerException.class,
                () -> crypto.encrypt(null, "key-1"));
        }

        @Test
        @DisplayName("decrypt(null, keyId) 抛出 NullPointerException")
        void shouldThrowWhenDecryptNullCiphertext() {
            assertThrows(NullPointerException.class,
                () -> crypto.decrypt(null, "key-1"));
        }

        @Test
        @DisplayName("密文长度不足（小于 IV+Tag）抛出 IllegalArgumentException")
        void shouldThrowWhenCiphertextTooShort() {
            // 27 字节 < IV(12) + Tag(16) = 28
            byte[] tooShort = new byte[27];
            String shortBase64 = Base64.getEncoder().encodeToString(tooShort);

            assertThrows(IllegalArgumentException.class,
                () -> crypto.decrypt(shortBase64, "key-1"));
        }

        @Test
        @DisplayName("非法 Base64 密文抛出 IllegalArgumentException")
        void shouldThrowWhenCiphertextNotBase64() {
            // '!' 不属于 Base64 字母表
            assertThrows(IllegalArgumentException.class,
                () -> crypto.decrypt("!!!not-base64!!!", "key-1"));
        }
    }
}
