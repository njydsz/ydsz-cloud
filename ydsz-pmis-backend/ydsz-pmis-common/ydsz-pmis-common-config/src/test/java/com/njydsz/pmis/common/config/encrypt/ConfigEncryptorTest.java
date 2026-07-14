package com.njydsz.pmis.common.config.encrypt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ConfigEncryptor 单元测试
 *
 * @author Marvin Lee
 * @since 1.0.0
 */
@DisplayName("ConfigEncryptor 加解密测试")
class ConfigEncryptorTest {

    private final ConfigEncryptor encryptor = new ConfigEncryptor("test-secret-key-12345");

    @Test
    @DisplayName("加密后再解密应得到原文")
    void encryptThenDecrypt_shouldReturnOriginal() {
        String plaintext = "my-super-secret-password";
        String encrypted = encryptor.encrypt(plaintext);

        assertNotEquals(plaintext, encrypted);
        assertTrue(encryptor.isEncrypted(encrypted));

        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("加密结果应包含 ENC() 前后缀")
    void encrypt_shouldHaveEncWrapper() {
        String encrypted = encryptor.encrypt("test");
        assertTrue(encrypted.startsWith("ENC("));
        assertTrue(encrypted.endsWith(")"));
    }

    @Test
    @DisplayName("isEncrypted 应正确识别加密格式")
    void isEncrypted_shouldDetectEncFormat() {
        assertTrue(encryptor.isEncrypted("ENC(abc123)"));
        assertFalse(encryptor.isEncrypted("plaintext"));
        assertFalse(encryptor.isEncrypted(null));
        assertFalse(encryptor.isEncrypted(""));
    }

    @Test
    @DisplayName("相同明文每次加密结果不同（随机 IV）")
    void encrypt_samePlaintextDifferentResult() {
        String plaintext = "same-password";
        String enc1 = encryptor.encrypt(plaintext);
        String enc2 = encryptor.encrypt(plaintext);

        assertNotEquals(enc1, enc2);
        assertEquals(plaintext, encryptor.decrypt(enc1));
        assertEquals(plaintext, encryptor.decrypt(enc2));
    }

    @Test
    @DisplayName("支持中文内容加解密")
    void encryptDecrypt_chineseContent() {
        String plaintext = "中文密码测试123!@#";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("解密非 ENC 格式的纯 Base64 应正常工作")
    void decrypt_plainBase64_shouldWork() {
        String plaintext = "direct-base64-value";
        String encrypted = encryptor.encrypt(plaintext);
        String base64Only = encrypted.substring(4, encrypted.length() - 1);
        String decrypted = encryptor.decrypt(base64Only);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("错误密钥解密应抛出异常")
    void decrypt_wrongKey_shouldThrow() {
        ConfigEncryptor encryptor1 = new ConfigEncryptor("key-1");
        ConfigEncryptor encryptor2 = new ConfigEncryptor("key-2");

        String encrypted = encryptor1.encrypt("secret");
        assertThrows(IllegalStateException.class, () -> encryptor2.decrypt(encrypted));
    }
}
