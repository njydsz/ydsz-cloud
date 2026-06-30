package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CryptoUtil 加密工具测试")
class CryptoUtilTest {

    @Test
    @DisplayName("md5 应计算标准值")
    void md5_standardValue() {
        assertThat(CryptoUtil.md5("hello")).isEqualTo("5d41402abc4b2a76b9719d911017c592");
        assertThat(CryptoUtil.md5("")).isNull();
        assertThat(CryptoUtil.md5(null)).isNull();
    }

    @Test
    @DisplayName("sha256 应计算标准值")
    void sha256_standardValue() {
        assertThat(CryptoUtil.sha256("hello")).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
        assertThat(CryptoUtil.sha256(null)).isNull();
    }

    @Test
    @DisplayName("toHex 与 base64 互转")
    void hexAndBase64() {
        byte[] data = {1, 2, 3, 4, (byte) 0xff};
        String hex = CryptoUtil.toHex(data);
        assertThat(hex).isEqualTo("01020304ff");
        String b64 = CryptoUtil.base64Encode(data);
        assertThat(CryptoUtil.base64Decode(b64)).containsExactly(data);
    }

    @Test
    @DisplayName("encryptPassword 应返回加密串与盐")
    void encryptPassword_returnsPair() {
        String[] result = CryptoUtil.encryptPassword("admin123");
        assertThat(result).hasSize(2);
        assertThat(result[0]).hasSize(32);  // MD5 长度
        assertThat(result[1]).hasSize(8);   // 盐长度
    }

    @Test
    @DisplayName("verifyPassword 正确密码应通过")
    void verifyPassword_success() {
        String[] pair = CryptoUtil.encryptPassword("admin123");
        assertThat(CryptoUtil.verifyPassword("admin123", pair[0], pair[1])).isTrue();
    }

    @Test
    @DisplayName("verifyPassword 错误密码应失败")
    void verifyPassword_fail() {
        String[] pair = CryptoUtil.encryptPassword("admin123");
        assertThat(CryptoUtil.verifyPassword("admin", pair[0], pair[1])).isFalse();
    }

    @Test
    @DisplayName("verifyPassword 空参数应返回 false")
    void verifyPassword_emptyArgs() {
        assertThat(CryptoUtil.verifyPassword(null, "abc", "salt")).isFalse();
        assertThat(CryptoUtil.verifyPassword("admin", null, "salt")).isFalse();
        assertThat(CryptoUtil.verifyPassword("admin", "abc", null)).isFalse();
    }

    @Test
    @DisplayName("randomSalt 长度应正确")
    void randomSalt_length() {
        String salt = CryptoUtil.randomSalt(10);
        assertThat(salt).hasSize(10);
    }

    @Test
    @DisplayName("randomBytes 长度应正确")
    void randomBytes_length() {
        byte[] b = CryptoUtil.randomBytes(32);
        assertThat(b).hasSize(32);
    }

    // ==================== AES-256-GCM ====================

    @Test
    @DisplayName("AES-GCM 加解密往返应一致")
    void aesGcm_roundTrip() {
        byte[] key = CryptoUtil.randomBytes(32);
        String plain = "Hello, AES-256-GCM! 中文";
        String cipher = CryptoUtil.aesGcmEncrypt(plain, key);
        assertThat(cipher).isNotEqualTo(plain);
        String back = CryptoUtil.aesGcmDecrypt(cipher, key);
        assertThat(back).isEqualTo(plain);
    }

    @Test
    @DisplayName("AES-GCM 相同明文每次 IV 不同 → 密文不同")
    void aesGcm_ivRandom() {
        byte[] key = CryptoUtil.randomBytes(32);
        String c1 = CryptoUtil.aesGcmEncrypt("same", key);
        String c2 = CryptoUtil.aesGcmEncrypt("same", key);
        assertThat(c1).isNotEqualTo(c2);
        assertThat(CryptoUtil.aesGcmDecrypt(c1, key)).isEqualTo("same");
        assertThat(CryptoUtil.aesGcmDecrypt(c2, key)).isEqualTo("same");
    }

    @Test
    @DisplayName("AES-GCM 密钥错误应抛异常 (GCM tag 校验)")
    void aesGcm_wrongKey() {
        byte[] key1 = CryptoUtil.randomBytes(32);
        byte[] key2 = CryptoUtil.randomBytes(32);
        String cipher = CryptoUtil.aesGcmEncrypt("secret", key1);
        assertThatThrownBy(() -> CryptoUtil.aesGcmDecrypt(cipher, key2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("AES-GCM 密钥长度错误应抛 IllegalArgumentException")
    void aesGcm_badKeyLength() {
        assertThatThrownBy(() -> CryptoUtil.aesGcmEncrypt("x", new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    @DisplayName("AES-GCM null 明文应返回 null")
    void aesGcm_nullPlain() {
        assertThat(CryptoUtil.aesGcmEncrypt(null, CryptoUtil.randomBytes(32))).isNull();
    }

    // ==================== SM4-GCM ====================

    @Test
    @DisplayName("SM4-GCM 加解密往返应一致")
    void sm4Gcm_roundTrip() {
        byte[] key = CryptoUtil.randomBytes(16);
        String plain = "国密 SM4 测试 🚀";
        String cipher = CryptoUtil.sm4GcmEncrypt(plain, key);
        assertThat(cipher).isNotEqualTo(plain);
        String back = CryptoUtil.sm4GcmDecrypt(cipher, key);
        assertThat(back).isEqualTo(plain);
    }

    @Test
    @DisplayName("SM4-GCM 密钥长度错误应抛 IllegalArgumentException")
    void sm4Gcm_badKeyLength() {
        assertThatThrownBy(() -> CryptoUtil.sm4GcmEncrypt("x", new byte[8]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 字节");
    }

    @Test
    @DisplayName("SM4-GCM 密钥错误应抛异常")
    void sm4Gcm_wrongKey() {
        String cipher = CryptoUtil.sm4GcmEncrypt("x", CryptoUtil.randomBytes(16));
        assertThatThrownBy(() -> CryptoUtil.sm4GcmDecrypt(cipher, CryptoUtil.randomBytes(16)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==================== PBKDF2 ====================

    @Test
    @DisplayName("PBKDF2 哈希 + 校验往返")
    void pbkdf2_roundTrip() {
        byte[] salt = CryptoUtil.randomBytes(16);
        String hash = CryptoUtil.hashPasswordPBKDF2("password", salt, 1000);
        assertThat(hash).isNotBlank();
        assertThat(CryptoUtil.verifyPasswordPBKDF2("password", salt, 1000, hash)).isTrue();
        assertThat(CryptoUtil.verifyPasswordPBKDF2("wrong", salt, 1000, hash)).isFalse();
    }

    @Test
    @DisplayName("PBKDF2 盐太短应抛 IllegalArgumentException")
    void pbkdf2_shortSalt() {
        assertThatThrownBy(() -> CryptoUtil.hashPasswordPBKDF2("p", new byte[4], 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PBKDF2 空密码应返回 null")
    void pbkdf2_nullPassword() {
        assertThat(CryptoUtil.hashPasswordPBKDF2(null, CryptoUtil.randomBytes(16), 1000)).isNull();
        assertThat(CryptoUtil.hashPasswordPBKDF2("", CryptoUtil.randomBytes(16), 1000)).isNull();
    }

    // ==================== HMAC / constantTime ====================

    @Test
    @DisplayName("HMAC-SHA256 应返回确定值")
    void hmacSha256() {
        byte[] key = CryptoUtil.randomBytes(32);
        String sig1 = CryptoUtil.hmacSha256("data", key);
        String sig2 = CryptoUtil.hmacSha256("data", key);
        assertThat(sig1).isEqualTo(sig2);
        String sig3 = CryptoUtil.hmacSha256("DATA", key);
        assertThat(sig3).isNotEqualTo(sig1);
    }

    @Test
    @DisplayName("constantTimeEquals 行为正确")
    void constantTimeEquals_behavior() {
        assertThat(CryptoUtil.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(CryptoUtil.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(CryptoUtil.constantTimeEquals("abc", "abcd")).isFalse();
        assertThat(CryptoUtil.constantTimeEquals(null, "abc")).isFalse();
        assertThat(CryptoUtil.constantTimeEquals("abc", null)).isFalse();
    }
}
