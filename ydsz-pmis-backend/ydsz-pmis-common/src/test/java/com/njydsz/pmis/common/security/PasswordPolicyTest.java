package com.njydsz.pmis.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PasswordPolicy} 密码策略校验器测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PasswordPolicy 密码策略校验测试")
class PasswordPolicyTest {

    @Nested
    @DisplayName("check() 密码校验")
    class CheckTest {

        @Test
        @DisplayName("强密码校验通过")
        void shouldPassStrongPassword() {
            var result = PasswordPolicy.check("Str0ng!Pass", "admin");
            assertTrue(result.pass());
            assertTrue(result.failures().isEmpty());
        }

        @Test
        @DisplayName("null 密码校验失败")
        void shouldFailNullPassword() {
            var result = PasswordPolicy.check(null, "admin");
            assertFalse(result.pass());
            assertEquals(1, result.failures().size());
            assertEquals("密码不能为空", result.firstError());
        }

        @Test
        @DisplayName("过短密码校验失败")
        void shouldFailShortPassword() {
            var result = PasswordPolicy.check("Ab1!", "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("长度不能少于")));
        }

        @Test
        @DisplayName("过长密码校验失败")
        void shouldFailLongPassword() {
            String longPwd = "A1!" + "a".repeat(32); // 总长 35
            var result = PasswordPolicy.check(longPwd, "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("长度不能超过")));
        }

        @Test
        @DisplayName("缺少大写字母校验失败")
        void shouldFailWithoutUppercase() {
            var result = PasswordPolicy.check("strong1!pass", "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("大写字母")));
        }

        @Test
        @DisplayName("缺少小写字母校验失败")
        void shouldFailWithoutLowercase() {
            var result = PasswordPolicy.check("STRONG1!PASS", "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("小写字母")));
        }

        @Test
        @DisplayName("缺少数字校验失败")
        void shouldFailWithoutDigit() {
            var result = PasswordPolicy.check("Strong!Pass", "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("数字")));
        }

        @Test
        @DisplayName("缺少特殊字符校验失败")
        void shouldFailWithoutSpecialChar() {
            var result = PasswordPolicy.check("Strong1Pass", "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("特殊字符")));
        }

        @Test
        @DisplayName("密码包含用户名时校验失败")
        void shouldFailWhenPasswordContainsUsername() {
            var result = PasswordPolicy.check("Str0ng!adminPass", "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("包含用户名")));
        }

        @Test
        @DisplayName("密码与用户名相同校验失败")
        void shouldFailWhenPasswordEqualsUsername() {
            var result = PasswordPolicy.check("Admin123!", "Admin123!");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("用户名")));
        }

        @Test
        @DisplayName("常见弱密码校验失败")
        void shouldFailWeakPassword() {
            var result = PasswordPolicy.check("Pmis@123", "admin");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("简单")));
        }

        @Test
        @DisplayName("包含连续递增字母校验失败")
        void shouldFailWithSequentialAscending() {
            var result = PasswordPolicy.check("Abc123!Xy", "user");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("递增")));
        }

        @Test
        @DisplayName("包含连续递减字母校验失败")
        void shouldFailWithSequentialDescending() {
            var result = PasswordPolicy.check("Cba987!Yx", "user");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("递减")));
        }

        @Test
        @DisplayName("包含连续重复字符校验失败")
        void shouldFailWithRepeatingChars() {
            var result = PasswordPolicy.check("AAA8xyz!", "user");
            assertFalse(result.pass());
            assertTrue(result.failures().stream().anyMatch(f -> f.contains("重复")));
        }

        @Test
        @DisplayName("null 用户名时不检查用户名相关规则")
        void shouldNotCheckUsernameWhenNull() {
            var result = PasswordPolicy.check("Str0ng!Pass", null);
            assertTrue(result.pass());
        }
    }

    @Nested
    @DisplayName("strength() 密码强度等级")
    class StrengthTest {

        @Test
        @DisplayName("null 或空密码返回 0")
        void shouldReturn0ForNullOrEmpty() {
            assertEquals(0, PasswordPolicy.strength(null));
            assertEquals(0, PasswordPolicy.strength(""));
        }

        @Test
        @DisplayName("仅小写字母 8 位返回 1")
        void shouldReturn1ForLowercaseOnly() {
            assertEquals(1, PasswordPolicy.strength("abcdefgh"));
        }

        @Test
        @DisplayName("大小写字母 + 12 位返回 3")
        void shouldReturn3ForMixedCase12() {
            assertEquals(3, PasswordPolicy.strength("Abcdefghijkl"));
        }

        @Test
        @DisplayName("大小写 + 数字 + 特殊字符 + 12 位返回 4")
        void shouldReturn4ForFullStrength() {
            assertEquals(4, PasswordPolicy.strength("Abcdefghij1!"));
        }

        @Test
        @DisplayName("强度最大为 4")
        void shouldCapAt4() {
            // 即使更长更复杂也不会超过 4
            assertEquals(4, PasswordPolicy.strength("Abcdefghijklmnop123!@#"));
        }
    }

    @Nested
    @DisplayName("isExpired() 密码过期检查")
    class IsExpiredTest {

        @Test
        @DisplayName("lastChange 为 null 返回 true")
        void shouldReturnTrueForNullLastChange() {
            assertTrue(PasswordPolicy.isExpired(null, 90));
        }

        @Test
        @DisplayName("未过期返回 false")
        void shouldReturnFalseForNotExpired() {
            LocalDateTime recent = LocalDateTime.now().minusDays(10);
            assertFalse(PasswordPolicy.isExpired(recent, 90));
        }

        @Test
        @DisplayName("已过期返回 true")
        void shouldReturnTrueForExpired() {
            LocalDateTime old = LocalDateTime.now().minusDays(100);
            assertTrue(PasswordPolicy.isExpired(old, 90));
        }

        @Test
        @DisplayName("刚好到过期边界返回 true")
        void shouldReturnTrueAtBoundary() {
            LocalDateTime boundary = LocalDateTime.now().minusDays(91);
            assertTrue(PasswordPolicy.isExpired(boundary, 90));
        }
    }

    @Nested
    @DisplayName("daysUntilExpiry() 密码过期预警")
    class DaysUntilExpiryTest {

        @Test
        @DisplayName("lastChange 为 null 返回 0")
        void shouldReturn0ForNullLastChange() {
            assertEquals(0, PasswordPolicy.daysUntilExpiry(null, 90));
        }

        @Test
        @DisplayName("未过期返回剩余天数")
        void shouldReturnRemainingDays() {
            LocalDateTime recent = LocalDateTime.now().minusDays(10);
            long remaining = PasswordPolicy.daysUntilExpiry(recent, 90);
            assertTrue(remaining > 70 && remaining <= 80);
        }

        @Test
        @DisplayName("已过期返回 0")
        void shouldReturn0ForExpired() {
            LocalDateTime old = LocalDateTime.now().minusDays(100);
            assertEquals(0, PasswordPolicy.daysUntilExpiry(old, 90));
        }
    }

    @Nested
    @DisplayName("PasswordCheckResult")
    class PasswordCheckResultTest {

        @Test
        @DisplayName("firstError() 空列表返回空字符串")
        void shouldReturnEmptyStringForNoErrors() {
            var result = new PasswordPolicy.PasswordCheckResult(true, java.util.List.of());
            assertEquals("", result.firstError());
        }

        @Test
        @DisplayName("firstError() 返回第一条错误")
        void shouldReturnFirstError() {
            var result = new PasswordPolicy.PasswordCheckResult(false,
                    java.util.List.of("错误1", "错误2"));
            assertEquals("错误1", result.firstError());
        }
    }
}
