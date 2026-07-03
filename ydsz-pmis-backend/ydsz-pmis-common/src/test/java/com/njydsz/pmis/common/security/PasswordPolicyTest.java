package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PasswordPolicy 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("PasswordPolicy 测试")
class PasswordPolicyTest {

    // ==================== check 方法 ====================

    @Test
    @DisplayName("密码强度校验 - 合法密码应通过")
    void check_shouldPassForValidPassword() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("Abc@1234", "testuser");
        assertTrue(result.pass());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    @DisplayName("密码强度校验 - null 密码应失败")
    void check_shouldFailForNullPassword() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check(null, "testuser");
        assertFalse(result.pass());
        assertFalse(result.failures().isEmpty());
    }

    @Test
    @DisplayName("密码强度校验 - 空字符串密码应失败（缺少各类字符）")
    void check_shouldFailForEmptyPassword() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("", "testuser");
        assertFalse(result.pass());
        assertFalse(result.failures().isEmpty());
    }

    @Test
    @DisplayName("密码强度校验 - 太短密码应失败")
    void check_shouldFailForTooShortPassword() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("Ab@1", "testuser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("不能少于")));
    }

    @Test
    @DisplayName("密码强度校验 - 太长密码应失败")
    void check_shouldFailForTooLongPassword() {
        String longPassword = "Abc@1234" + "x".repeat(30);
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check(longPassword, "testuser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("不能超过")));
    }

    @Test
    @DisplayName("密码强度校验 - 缺少大写字母应失败")
    void check_shouldFailForMissingUppercase() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("abc@1234", "testuser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("大写字母")));
    }

    @Test
    @DisplayName("密码强度校验 - 缺少小写字母应失败")
    void check_shouldFailForMissingLowercase() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("ABC@1234", "testuser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("小写字母")));
    }

    @Test
    @DisplayName("密码强度校验 - 缺少数字应失败")
    void check_shouldFailForMissingDigit() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("Abc@defg", "testuser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("数字")));
    }

    @Test
    @DisplayName("密码强度校验 - 缺少特殊字符应失败")
    void check_shouldFailForMissingSpecial() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("Abc12345", "testuser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("特殊字符")));
    }

    @Test
    @DisplayName("密码强度校验 - 密码与用户名相同应失败")
    void check_shouldFailForSameAsUsername() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("TestUser", "testuser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("不能与用户名相同")));
    }

    @Test
    @DisplayName("密码强度校验 - 弱密码应失败")
    void check_shouldFailForWeakPassword() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("12345678", "otheruser");
        assertFalse(result.pass());
        assertTrue(result.failures().stream().anyMatch(f -> f.contains("过于简单")));
    }

    @Test
    @DisplayName("密码强度校验 - username 为 null 也不应报错")
    void check_shouldHandleNullUsername() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("Abc@1234", null);
        assertTrue(result.pass());
    }

    @Test
    @DisplayName("密码强度校验 - firstError 应返回第一条失败原因")
    void firstError_shouldReturnFirstFailure() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("", "testuser");
        assertEquals("密码长度不能少于 8 位", result.firstError());
    }

    @Test
    @DisplayName("密码强度校验 - 通过时 firstError 应返回空字符串")
    void firstError_shouldReturnEmptyWhenPassed() {
        PasswordPolicy.PasswordCheckResult result = PasswordPolicy.check("Abc@1234", "testuser");
        assertEquals("", result.firstError());
    }

    // ==================== strength 方法 ====================

    @Test
    @DisplayName("密码强度 - null 密码应返回 0")
    void strength_shouldReturn0ForNull() {
        assertEquals(0, PasswordPolicy.strength(null));
    }

    @Test
    @DisplayName("密码强度 - 空密码应返回 0")
    void strength_shouldReturn0ForEmpty() {
        assertEquals(0, PasswordPolicy.strength(""));
    }

    @Test
    @DisplayName("密码强度 - 弱密码应返回低分")
    void strength_shouldReturnLowScoreForWeakPassword() {
        assertTrue(PasswordPolicy.strength("12345678") <= 2);
    }

    @Test
    @DisplayName("密码强度 - 强密码应返回 4")
    void strength_shouldReturn4ForStrongPassword() {
        assertEquals(4, PasswordPolicy.strength("MyStr0ng!P@ssw0rd"));
    }

    @Test
    @DisplayName("密码强度 - 中等密码应返回中等分数")
    void strength_shouldReturnMidScore() {
        int score = PasswordPolicy.strength("Abc12345");
        assertTrue(score >= 1 && score <= 3);
    }

    // ==================== isExpired 方法 ====================

    @Test
    @DisplayName("密码过期 - null 修改时间应返回 true")
    void isExpired_shouldReturnTrueForNullLastChange() {
        assertTrue(PasswordPolicy.isExpired(null, 90));
    }

    @Test
    @DisplayName("密码过期 - 很久以前修改的密码应过期")
    void isExpired_shouldReturnTrueForOldPassword() {
        LocalDateTime oldDate = LocalDateTime.now().minusDays(100);
        assertTrue(PasswordPolicy.isExpired(oldDate, 90));
    }

    @Test
    @DisplayName("密码过期 - 最近修改的密码不应过期")
    void isExpired_shouldReturnFalseForRecentPassword() {
        LocalDateTime recentDate = LocalDateTime.now().minusDays(10);
        assertFalse(PasswordPolicy.isExpired(recentDate, 90));
    }

    // ==================== PasswordCheckResult record ====================

    @Test
    @DisplayName("PasswordCheckResult - 通过时 pass 为 true，failures 为空")
    void passwordCheckResult_shouldHaveCorrectFieldsWhenPassed() {
        PasswordPolicy.PasswordCheckResult result = new PasswordPolicy.PasswordCheckResult(true, List.of());
        assertTrue(result.pass());
        assertTrue(result.failures().isEmpty());
        assertEquals("", result.firstError());
    }

    @Test
    @DisplayName("PasswordCheckResult - 失败时 pass 为 false，failures 非空")
    void passwordCheckResult_shouldHaveCorrectFieldsWhenFailed() {
        List<String> failures = List.of("密码太短", "缺少大写字母");
        PasswordPolicy.PasswordCheckResult result = new PasswordPolicy.PasswordCheckResult(false, failures);
        assertFalse(result.pass());
        assertEquals(2, result.failures().size());
        assertEquals("密码太短", result.firstError());
    }
}