package com.njydsz.pmis.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
