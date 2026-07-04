package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CryptoUtil 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CryptoUtil 测试")
class CryptoUtilTest {

    private static final byte[] AES_KEY_32 = CryptoUtil.randomBytes(32);
    private static final byte[] SM4_KEY_16 = CryptoUtil.randomBytes(16);

    // ==================== AES-256-GCM ====================

    @Test
    @DisplayName("AES-GCM 加密解密 - 正常加解密应还原原文")
    void aesGcmEncryptDecrypt_shouldRoundtrip() {
        String plaintext = "Hello, PMIS! 你好世界";
        String ciphertext = CryptoUtil.aesGcmEncrypt(plaintext, AES_KEY_32);
        assertNotNull(ciphertext);
        assertNotEquals(plaintext, ciphertext);

        String decrypted = CryptoUtil.aesGcmDecrypt(ciphertext, AES_KEY_32);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("AES-GCM 加密 - null 明文应返回 null")
    void aesGcmEncrypt_shouldReturnNullForNullPlaintext() {
        assertNull(CryptoUtil.aesGcmEncrypt(null, AES_KEY_32));
    }

    @Test
    @DisplayName("AES-GCM 解密 - 空密文应返回 null")
    void aesGcmDecrypt_shouldReturnNullForBlankCiphertext() {
        assertNull(CryptoUtil.aesGcmDecrypt(null, AES_KEY_32));
        assertNull(CryptoUtil.aesGcmDecrypt("", AES_KEY_32));
        assertNull(CryptoUtil.aesGcmDecrypt("   ", AES_KEY_32));
    }

    @Test
    @DisplayName("AES-GCM - 无效密钥长度应抛出异常")
    void aesGcm_shouldThrowForInvalidKey() {
        String plaintext = "test";
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.aesGcmEncrypt(plaintext, null));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.aesGcmEncrypt(plaintext, new byte[16]));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.aesGcmDecrypt("dGVzdA==", null));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.aesGcmDecrypt("dGVzdA==", new byte[31]));
    }

    @Test
    @DisplayName("AES-GCM - 相同密钥不同明文加密结果应不同（IV 随机）")
    void aesGcmEncrypt_shouldProduceDifferentCiphertext() {
        String ciphertext1 = CryptoUtil.aesGcmEncrypt("hello", AES_KEY_32);
        String ciphertext2 = CryptoUtil.aesGcmEncrypt("hello", AES_KEY_32);
        assertNotEquals(ciphertext1, ciphertext2, "相同明文多次加密结果应不同（随机 IV）");
    }

    @Test
    @DisplayName("AES-GCM - 空字符串加解密")
    void aesGcmEncryptDecrypt_shouldHandleEmptyString() {
        String ciphertext = CryptoUtil.aesGcmEncrypt("", AES_KEY_32);
        assertNotNull(ciphertext);
        assertEquals("", CryptoUtil.aesGcmDecrypt(ciphertext, AES_KEY_32));
    }

    @Test
    @DisplayName("AES-GCM - 中文加解密")
    void aesGcmEncryptDecrypt_shouldHandleChinese() {
        String plaintext = "中文测试数据 🎉 测试";
        String ciphertext = CryptoUtil.aesGcmEncrypt(plaintext, AES_KEY_32);
        assertEquals(plaintext, CryptoUtil.aesGcmDecrypt(ciphertext, AES_KEY_32));
    }

    // ==================== SM4-GCM ====================

    @Test
    @DisplayName("SM4-GCM 加密解密 - 正常加解密应还原原文")
    void sm4GcmEncryptDecrypt_shouldRoundtrip() {
        String plaintext = "Hello, SM4! 国密测试";
        String ciphertext = CryptoUtil.sm4GcmEncrypt(plaintext, SM4_KEY_16);
        assertNotNull(ciphertext);
        assertNotEquals(plaintext, ciphertext);

        String decrypted = CryptoUtil.sm4GcmDecrypt(ciphertext, SM4_KEY_16);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("SM4-GCM 加密 - null 明文应返回 null")
    void sm4GcmEncrypt_shouldReturnNullForNullPlaintext() {
        assertNull(CryptoUtil.sm4GcmEncrypt(null, SM4_KEY_16));
    }

    @Test
    @DisplayName("SM4-GCM 解密 - 空密文应返回 null")
    void sm4GcmDecrypt_shouldReturnNullForBlankCiphertext() {
        assertNull(CryptoUtil.sm4GcmDecrypt(null, SM4_KEY_16));
        assertNull(CryptoUtil.sm4GcmDecrypt("", SM4_KEY_16));
    }

    @Test
    @DisplayName("SM4-GCM - 无效密钥长度应抛出异常")
    void sm4Gcm_shouldThrowForInvalidKey() {
        String plaintext = "test";
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.sm4GcmEncrypt(plaintext, null));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.sm4GcmEncrypt(plaintext, new byte[32]));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.sm4GcmDecrypt("dGVzdA==", null));
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.sm4GcmDecrypt("dGVzdA==", new byte[8]));
    }

    // ==================== MD5 ====================

    @Test
    @DisplayName("MD5 - 正常输入应返回 32 位十六进制")
    void md5_shouldReturn32HexChars() {
        String result = CryptoUtil.md5("hello");
        assertNotNull(result);
        assertEquals(32, result.length());
        assertTrue(result.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("MD5 - 空输入应返回 null")
    void md5_shouldReturnNullForBlank() {
        assertNull(CryptoUtil.md5(null));
        assertNull(CryptoUtil.md5(""));
        assertNull(CryptoUtil.md5("   "));
    }

    @Test
    @DisplayName("MD5 - 相同输入应产生相同摘要")
    void md5_shouldBeDeterministic() {
        assertEquals(CryptoUtil.md5("hello"), CryptoUtil.md5("hello"));
    }

    // ==================== SHA-256 ====================

    @Test
    @DisplayName("SHA-256 - 正常输入应返回 64 位十六进制")
    void sha256_shouldReturn64HexChars() {
        String result = CryptoUtil.sha256("hello");
        assertNotNull(result);
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]+"));
    }

    @Test
    @DisplayName("SHA-256 - 空输入应返回 null")
    void sha256_shouldReturnNullForBlank() {
        assertNull(CryptoUtil.sha256(null));
        assertNull(CryptoUtil.sha256(""));
    }

    // ==================== 密码哈希 ====================

    @Test
    @DisplayName("加盐密码 - 加密后应能校验通过")
    void encryptPassword_shouldBeVerifiable() {
        String[] result = CryptoUtil.encryptPassword("myPassword123");
        assertEquals(2, result.length);
        assertNotNull(result[0]);
        assertNotNull(result[1]);
        assertTrue(CryptoUtil.verifyPassword("myPassword123", result[0], result[1]));
    }

    @Test
    @DisplayName("加盐密码 - 错误密码校验应失败")
    void verifyPassword_shouldFailForWrongPassword() {
        String[] result = CryptoUtil.encryptPassword("myPassword123");
        assertFalse(CryptoUtil.verifyPassword("wrongPassword", result[0], result[1]));
    }

    @Test
    @DisplayName("加盐密码 - 空参数校验应返回 false")
    void verifyPassword_shouldReturnFalseForBlankParams() {
        assertFalse(CryptoUtil.verifyPassword(null, "enc", "salt"));
        assertFalse(CryptoUtil.verifyPassword("raw", null, "salt"));
        assertFalse(CryptoUtil.verifyPassword("raw", "enc", null));
    }

    // ==================== BCrypt 密码哈希 ====================

    @Test
    @DisplayName("BCrypt - 哈希后应能校验通过")
    void hashPasswordBCrypt_shouldBeVerifiable() {
        String hash = CryptoUtil.hashPasswordBCrypt("myStrongPwd#2026");
        assertNotNull(hash);
        assertTrue(CryptoUtil.isBCryptFormat(hash));
        assertTrue(CryptoUtil.verifyPasswordBCrypt("myStrongPwd#2026", hash));
    }

    @Test
    @DisplayName("BCrypt - 相同明文多次哈希结果应不同（自带随机盐）")
    void hashPasswordBCrypt_shouldProduceDifferentHashes() {
        String h1 = CryptoUtil.hashPasswordBCrypt("samePassword123");
        String h2 = CryptoUtil.hashPasswordBCrypt("samePassword123");
        assertNotEquals(h1, h2, "BCrypt 自带随机盐，相同明文哈希结果应不同");
        assertTrue(CryptoUtil.verifyPasswordBCrypt("samePassword123", h1));
        assertTrue(CryptoUtil.verifyPasswordBCrypt("samePassword123", h2));
    }

    @Test
    @DisplayName("BCrypt - 错误密码校验应失败")
    void verifyPasswordBCrypt_shouldFailForWrongPassword() {
        String hash = CryptoUtil.hashPasswordBCrypt("correctPwd#2026");
        assertFalse(CryptoUtil.verifyPasswordBCrypt("wrongPwd", hash));
    }

    @Test
    @DisplayName("BCrypt - 空参数应返回 false / 抛出异常")
    void verifyPasswordBCrypt_shouldHandleBlank() {
        String hash = CryptoUtil.hashPasswordBCrypt("correctPwd#2026");
        assertFalse(CryptoUtil.verifyPasswordBCrypt(null, hash));
        assertFalse(CryptoUtil.verifyPasswordBCrypt("raw", null));
        assertFalse(CryptoUtil.verifyPasswordBCrypt("", hash));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.hashPasswordBCrypt(null));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.hashPasswordBCrypt(""));
    }

    @Test
    @DisplayName("isBCryptFormat - 应正确识别 BCrypt 格式")
    void isBCryptFormat_shouldRecognizeBCryptHashes() {
        assertTrue(CryptoUtil.isBCryptFormat("$2a$12$abcdefghijklmnopqrstuv123456789012345678901234567890123456"));
        assertTrue(CryptoUtil.isBCryptFormat("$2b$12$abcdefghijklmnopqrstuv123456789012345678901234567890123456"));
        assertTrue(CryptoUtil.isBCryptFormat("$2y$10$abcdefghijklmnopqrstuv123456789012345678901234567890123456"));
        assertFalse(CryptoUtil.isBCryptFormat("abcdef1234567890"));
        assertFalse(CryptoUtil.isBCryptFormat(""));
        assertFalse(CryptoUtil.isBCryptFormat(null));
    }

    @Test
    @DisplayName("BCrypt - 哈希字符串应以 $2a$12$ 开头（cost=12）")
    void hashPasswordBCrypt_shouldUseCostFactor12() {
        String hash = CryptoUtil.hashPasswordBCrypt("testPwd#2026");
        assertTrue(hash.startsWith("$2a$12$") || hash.startsWith("$2b$12$"),
                "BCrypt 哈希应使用 cost=12，实际: " + hash);
    }

    @Test
    @DisplayName("PBKDF2 密码哈希 - 正常哈希应能校验通过")
    void hashPasswordPBKDF2_shouldBeVerifiable() {
        byte[] salt = CryptoUtil.randomBytes(16);
        String hash = CryptoUtil.hashPasswordPBKDF2("securePass!", salt, 10000);
        assertNotNull(hash);
        assertTrue(CryptoUtil.verifyPasswordPBKDF2("securePass!", salt, 10000, hash));
    }

    @Test
    @DisplayName("PBKDF2 密码哈希 - 空密码应返回 null")
    void hashPasswordPBKDF2_shouldReturnNullForBlankPassword() {
        byte[] salt = CryptoUtil.randomBytes(16);
        assertNull(CryptoUtil.hashPasswordPBKDF2(null, salt, 10000));
        assertNull(CryptoUtil.hashPasswordPBKDF2("", salt, 10000));
    }

    // ==================== 随机 ====================

    @Test
    @DisplayName("randomSalt - 应返回指定长度字符串")
    void randomSalt_shouldReturnCorrectLength() {
        assertEquals(8, CryptoUtil.randomSalt(8).length());
        assertEquals(16, CryptoUtil.randomSalt(16).length());
    }

    @Test
    @DisplayName("randomBytes - 应返回指定长度字节数组")
    void randomBytes_shouldReturnCorrectLength() {
        assertEquals(16, CryptoUtil.randomBytes(16).length);
        assertEquals(32, CryptoUtil.randomBytes(32).length);
    }

    // ==================== Base64 ====================

    @Test
    @DisplayName("Base64 编解码 - 编码后解码应还原")
    void base64EncodeDecode_shouldRoundtrip() {
        byte[] original = new byte[]{1, 2, 3, 4, 5};
        String encoded = CryptoUtil.base64Encode(original);
        byte[] decoded = CryptoUtil.base64Decode(encoded);
        assertArrayEquals(original, decoded);
    }

    @Test
    @DisplayName("Base64Url 编解码 - 编码后解码应还原")
    void base64UrlEncodeDecode_shouldRoundtrip() {
        byte[] original = new byte[]{1, 2, 3, 4, 5};
        String encoded = CryptoUtil.base64UrlEncode(original);
        byte[] decoded = CryptoUtil.base64UrlDecode(encoded);
        assertArrayEquals(original, decoded);
    }

    // ==================== HMAC ====================

    @Test
    @DisplayName("HMAC-SHA256 - 正常签名应返回非空")
    void hmacSha256_shouldReturnNonEmpty() {
        byte[] key = CryptoUtil.randomBytes(32);
        String result = CryptoUtil.hmacSha256("data", key);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @DisplayName("HMAC-SHA256 - 空输入应返回 null")
    void hmacSha256_shouldReturnNullForBlank() {
        byte[] key = CryptoUtil.randomBytes(32);
        assertNull(CryptoUtil.hmacSha256(null, key));
        assertNull(CryptoUtil.hmacSha256("", key));
        assertNull(CryptoUtil.hmacSha256("data", null));
    }

    // ==================== 常量时间比较 ====================

    @Test
    @DisplayName("constantTimeEquals - 相同字符串应返回 true")
    void constantTimeEquals_shouldReturnTrueForEqual() {
        assertTrue(CryptoUtil.constantTimeEquals("abc", "abc"));
    }

    @Test
    @DisplayName("constantTimeEquals - 不同字符串应返回 false")
    void constantTimeEquals_shouldReturnFalseForDifferent() {
        assertFalse(CryptoUtil.constantTimeEquals("abc", "def"));
    }

    @Test
    @DisplayName("constantTimeEquals - null 输入应返回 false")
    void constantTimeEquals_shouldReturnFalseForNull() {
        assertFalse(CryptoUtil.constantTimeEquals(null, "abc"));
        assertFalse(CryptoUtil.constantTimeEquals("abc", null));
        assertFalse(CryptoUtil.constantTimeEquals(null, null));
    }

    // ==================== toHex ====================

    @Test
    @DisplayName("toHex - 字节数组转十六进制")
    void toHex_shouldReturnHexString() {
        byte[] data = new byte[]{(byte) 0xab, (byte) 0xcd, (byte) 0xef};
        assertEquals("abcdef", CryptoUtil.toHex(data));
    }
}