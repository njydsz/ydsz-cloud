package com.njydsz.pmis.common.util.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PwdUtils 单元测试
 *
 * <p>覆盖加盐哈希、PBKDF2 加密、密码验证、密码强度校验、随机盐生成等核心能力，
 * 包含正常流程与 null/空字符串等异常场景。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@DisplayName("PwdUtils - 用户密码安全工具类测试")
class PwdUtilsTest {

    /** 测试用固定盐值（16 字节 => 32 位十六进制） */
    private static final String FIXED_SALT_HEX = "0123456789abcdef0123456789abcdef";

    // ==================== encodeWithSalt ====================

    @Nested
    @DisplayName("encodeWithSalt - 加盐哈希")
    class EncodeWithSaltTest {

        @Test
        @DisplayName("正常加密返回 salt:hash 格式")
        void shouldReturnSaltColonHashFormat() {
            String encoded = PwdUtils.encodeWithSalt("password123", FIXED_SALT_HEX);
            assertNotNull(encoded);
            // 仅包含一个冒号分隔 salt 与 hash
            long colonCount = encoded.chars().filter(c -> c == ':').count();
            assertEquals(1L, colonCount);
            assertTrue(encoded.startsWith(FIXED_SALT_HEX + ":"));
        }

        @Test
        @DisplayName("相同密码与盐值产生相同哈希（确定性）")
        void shouldProduceSameHashForSameInput() {
            String e1 = PwdUtils.encodeWithSalt("password123", FIXED_SALT_HEX);
            String e2 = PwdUtils.encodeWithSalt("password123", FIXED_SALT_HEX);
            assertEquals(e1, e2);
        }

        @Test
        @DisplayName("不同密码产生不同哈希")
        void shouldProduceDifferentHashForDifferentPassword() {
            String e1 = PwdUtils.encodeWithSalt("password123", FIXED_SALT_HEX);
            String e2 = PwdUtils.encodeWithSalt("password456", FIXED_SALT_HEX);
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("不同盐值产生不同哈希")
        void shouldProduceDifferentHashForDifferentSalt() {
            String salt2 = "fedcba9876543210fedcba9876543210";
            String e1 = PwdUtils.encodeWithSalt("password123", FIXED_SALT_HEX);
            String e2 = PwdUtils.encodeWithSalt("password123", salt2);
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("null 密码抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodeWithSalt(null, FIXED_SALT_HEX));
        }

        @Test
        @DisplayName("空字符串密码抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsEmpty() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodeWithSalt("", FIXED_SALT_HEX));
        }

        @Test
        @DisplayName("null 盐值抛出 IllegalArgumentException")
        void shouldThrowWhenSaltIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodeWithSalt("password123", null));
        }

        @Test
        @DisplayName("空字符串盐值抛出 IllegalArgumentException")
        void shouldThrowWhenSaltIsEmpty() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodeWithSalt("password123", ""));
        }
    }

    // ==================== encodeWithAutoSalt ====================

    @Nested
    @DisplayName("encodeWithAutoSalt - 自动生成盐值并加密")
    class EncodeWithAutoSaltTest {

        @Test
        @DisplayName("自动生成盐值并返回 salt:hash 格式")
        void shouldGenerateSaltAndReturnEncodedResult() {
            String encoded = PwdUtils.encodeWithAutoSalt("password123");
            assertNotNull(encoded);
            long colonCount = encoded.chars().filter(c -> c == ':').count();
            assertEquals(1L, colonCount);
        }

        @Test
        @DisplayName("相同密码每次加密生成不同盐值，结果不同")
        void shouldGenerateDifferentSaltEachCall() {
            String e1 = PwdUtils.encodeWithAutoSalt("password123");
            String e2 = PwdUtils.encodeWithAutoSalt("password123");
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("null 密码抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodeWithAutoSalt(null));
        }

        @Test
        @DisplayName("空字符串密码抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsEmpty() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodeWithAutoSalt(""));
        }
    }

    // ==================== isValidPasswordWithSalt ====================

    @Nested
    @DisplayName("isValidPasswordWithSalt - 验证加盐密码")
    class IsValidPasswordWithSaltTest {

        @Test
        @DisplayName("正确密码验证通过")
        void shouldReturnTrueForCorrectPassword() {
            String encoded = PwdUtils.encodeWithSalt("password123", FIXED_SALT_HEX);
            assertTrue(PwdUtils.isValidPasswordWithSalt("password123", encoded));
        }

        @Test
        @DisplayName("错误密码验证不通过")
        void shouldReturnFalseForWrongPassword() {
            String encoded = PwdUtils.encodeWithSalt("password123", FIXED_SALT_HEX);
            assertFalse(PwdUtils.isValidPasswordWithSalt("wrongPassword", encoded));
        }

        @Test
        @DisplayName("使用自动盐值加密的密码也能正确验证")
        void shouldValidateAutoSaltEncodedPassword() {
            String encoded = PwdUtils.encodeWithAutoSalt("password123");
            assertTrue(PwdUtils.isValidPasswordWithSalt("password123", encoded));
            assertFalse(PwdUtils.isValidPasswordWithSalt("password456", encoded));
        }

        @Test
        @DisplayName("null 密码返回 false")
        void shouldReturnFalseWhenPasswordIsNull() {
            assertFalse(PwdUtils.isValidPasswordWithSalt(null, FIXED_SALT_HEX + ":hash"));
        }

        @Test
        @DisplayName("空字符串密码返回 false")
        void shouldReturnFalseWhenPasswordIsEmpty() {
            assertFalse(PwdUtils.isValidPasswordWithSalt("", FIXED_SALT_HEX + ":hash"));
        }

        @Test
        @DisplayName("null 加密串返回 false")
        void shouldReturnFalseWhenEncodedIsNull() {
            assertFalse(PwdUtils.isValidPasswordWithSalt("password123", null));
        }

        @Test
        @DisplayName("空字符串加密串返回 false")
        void shouldReturnFalseWhenEncodedIsEmpty() {
            assertFalse(PwdUtils.isValidPasswordWithSalt("password123", ""));
        }

        @Test
        @DisplayName("加密串不含冒号分隔符返回 false")
        void shouldReturnFalseWhenEncodedHasNoColon() {
            assertFalse(PwdUtils.isValidPasswordWithSalt("password123", "invalidEncodedString"));
        }
    }

    // ==================== encodePBKDF2 ====================

    @Nested
    @DisplayName("encodePBKDF2 - PBKDF2 加密")
    class EncodePBKDF2Test {

        @Test
        @DisplayName("默认迭代次数加密返回 salt:iterations:hash 格式")
        void shouldReturnSaltIterationsHashFormat() {
            String encoded = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX);
            assertNotNull(encoded);
            String[] parts = encoded.split(":");
            assertEquals(3, parts.length);
            assertEquals(FIXED_SALT_HEX, parts[0]);
            assertEquals("10000", parts[1]);
        }

        @Test
        @DisplayName("指定迭代次数加密时，结果中 iterations 字段与传入值一致")
        void shouldUseSpecifiedIterations() {
            String encoded = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX, 5000);
            assertNotNull(encoded);
            String[] parts = encoded.split(":");
            assertEquals(3, parts.length);
            assertEquals("5000", parts[1]);
        }

        @Test
        @DisplayName("相同密码、盐值、迭代次数产生相同哈希（确定性）")
        void shouldProduceSameHashForSameInput() {
            String e1 = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX, 1000);
            String e2 = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX, 1000);
            assertEquals(e1, e2);
        }

        @Test
        @DisplayName("不同迭代次数产生不同哈希")
        void shouldProduceDifferentHashForDifferentIterations() {
            String e1 = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX, 1000);
            String e2 = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX, 2000);
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("null 盐值抛出 IllegalArgumentException")
        void shouldThrowWhenSaltIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodePBKDF2("password123".toCharArray(), null));
        }

        @Test
        @DisplayName("空字符串盐值抛出 IllegalArgumentException")
        void shouldThrowWhenSaltIsEmpty() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodePBKDF2("password123".toCharArray(), ""));
        }

        @Test
        @DisplayName("null 密码数组抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodePBKDF2(null, FIXED_SALT_HEX));
        }

        @Test
        @DisplayName("空字符密码数组抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsEmpty() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodePBKDF2(new char[0], FIXED_SALT_HEX));
        }
    }

    // ==================== encodePBKDF2WithAutoSalt ====================

    @Nested
    @DisplayName("encodePBKDF2WithAutoSalt - 自动生成盐值的 PBKDF2 加密")
    class EncodePBKDF2WithAutoSaltTest {

        @Test
        @DisplayName("默认迭代次数加密返回 salt:iterations:hash 格式")
        void shouldReturnSaltIterationsHashFormat() {
            String encoded = PwdUtils.encodePBKDF2WithAutoSalt("password123".toCharArray());
            assertNotNull(encoded);
            String[] parts = encoded.split(":");
            assertEquals(3, parts.length);
            assertEquals("10000", parts[1]);
        }

        @Test
        @DisplayName("指定迭代次数加密时，结果中 iterations 字段与传入值一致")
        void shouldUseSpecifiedIterations() {
            String encoded = PwdUtils.encodePBKDF2WithAutoSalt("password123".toCharArray(), 5000);
            String[] parts = encoded.split(":");
            assertEquals(3, parts.length);
            assertEquals("5000", parts[1]);
        }

        @Test
        @DisplayName("相同密码每次加密生成不同盐值，结果不同")
        void shouldGenerateDifferentSaltEachCall() {
            String e1 = PwdUtils.encodePBKDF2WithAutoSalt("password123".toCharArray());
            String e2 = PwdUtils.encodePBKDF2WithAutoSalt("password123".toCharArray());
            assertNotEquals(e1, e2);
        }

        @Test
        @DisplayName("null 密码数组抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsNull() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodePBKDF2WithAutoSalt(null));
        }

        @Test
        @DisplayName("空字符密码数组抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsEmpty() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodePBKDF2WithAutoSalt(new char[0]));
        }

        @Test
        @DisplayName("空字符密码数组（带迭代次数）抛出 IllegalArgumentException")
        void shouldThrowWhenPasswordIsEmptyWithIterations() {
            assertThrows(IllegalArgumentException.class,
                () -> PwdUtils.encodePBKDF2WithAutoSalt(new char[0], 1000));
        }
    }

    // ==================== verifyPBKDF2 ====================

    @Nested
    @DisplayName("verifyPBKDF2 - 验证 PBKDF2 加密密码")
    class VerifyPBKDF2Test {

        @Test
        @DisplayName("正确密码验证通过")
        void shouldReturnTrueForCorrectPassword() {
            String encoded = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX);
            assertTrue(PwdUtils.verifyPBKDF2("password123", encoded));
        }

        @Test
        @DisplayName("错误密码验证不通过")
        void shouldReturnFalseForWrongPassword() {
            String encoded = PwdUtils.encodePBKDF2("password123".toCharArray(), FIXED_SALT_HEX);
            assertFalse(PwdUtils.verifyPBKDF2("wrongPassword", encoded));
        }

        @Test
        @DisplayName("使用自动盐值加密的密码也能正确验证")
        void shouldValidateAutoSaltEncodedPassword() {
            String encoded = PwdUtils.encodePBKDF2WithAutoSalt("password123".toCharArray());
            assertTrue(PwdUtils.verifyPBKDF2("password123", encoded));
            assertFalse(PwdUtils.verifyPBKDF2("password456", encoded));
        }

        @Test
        @DisplayName("null 密码返回 false")
        void shouldReturnFalseWhenPasswordIsNull() {
            assertFalse(PwdUtils.verifyPBKDF2(null, "salt:10000:hash"));
        }

        @Test
        @DisplayName("空字符串密码返回 false")
        void shouldReturnFalseWhenPasswordIsEmpty() {
            assertFalse(PwdUtils.verifyPBKDF2("", "salt:10000:hash"));
        }

        @Test
        @DisplayName("null 加密串返回 false")
        void shouldReturnFalseWhenEncodedIsNull() {
            assertFalse(PwdUtils.verifyPBKDF2("password123", null));
        }

        @Test
        @DisplayName("空字符串加密串返回 false")
        void shouldReturnFalseWhenEncodedIsEmpty() {
            assertFalse(PwdUtils.verifyPBKDF2("password123", ""));
        }

        @Test
        @DisplayName("加密串不含两个冒号（格式错误）返回 false")
        void shouldReturnFalseWhenEncodedFormatIsInvalid() {
            // 仅一段
            assertFalse(PwdUtils.verifyPBKDF2("password123", "onlyOnePart"));
            // 两段
            assertFalse(PwdUtils.verifyPBKDF2("password123", "salt:10000"));
            // 四段
            assertFalse(PwdUtils.verifyPBKDF2("password123", "salt:10000:hash:extra"));
        }

        @Test
        @DisplayName("iterations 字段非数字返回 false")
        void shouldReturnFalseWhenIterationsIsNotANumber() {
            assertFalse(PwdUtils.verifyPBKDF2("password123", FIXED_SALT_HEX + ":notANumber:abcd"));
        }

        @Test
        @DisplayName("非法十六进制盐值返回 false（不抛异常）")
        void shouldReturnFalseWhenSaltHexIsInvalid() {
            // 长度为奇数，无法解码
            assertFalse(PwdUtils.verifyPBKDF2("password123", "abc:10000:abcd"));
        }
    }

    // ==================== 默认密码加密 ====================

    @Nested
    @DisplayName("默认密码加密")
    class DefaultPassEncryptionTest {

        @Test
        @DisplayName("getDefaultPassEncryption 返回 salt:hash 格式的非空字符串")
        void shouldReturnNonEmptySaltHashFormat() {
            String encoded = PwdUtils.getDefaultPassEncryption();
            assertNotNull(encoded);
            long colonCount = encoded.chars().filter(c -> c == ':').count();
            assertEquals(1L, colonCount);
        }

        @Test
        @DisplayName("getDefaultPassEncryptionWithSalt 返回 salt:hash 格式的非空字符串")
        void shouldReturnNonEmptySaltHashFormatWithSalt() {
            String encoded = PwdUtils.getDefaultPassEncryptionWithSalt();
            assertNotNull(encoded);
            long colonCount = encoded.chars().filter(c -> c == ':').count();
            assertEquals(1L, colonCount);
        }

        @Test
        @DisplayName("两次调用 getDefaultPassEncryption 因盐值随机而结果不同")
        void shouldReturnDifferentResultEachCall() {
            String e1 = PwdUtils.getDefaultPassEncryption();
            String e2 = PwdUtils.getDefaultPassEncryption();
            assertNotEquals(e1, e2);
        }
    }

    // ==================== generateSalt ====================

    @Nested
    @DisplayName("generateSalt - 随机盐值生成")
    class GenerateSaltTest {

        @Test
        @DisplayName("generateSalt(length) 返回 2 * length 长度的十六进制串")
        void shouldReturnHexSaltOfExpectedLength() {
            String salt = PwdUtils.generateSalt(16);
            assertNotNull(salt);
            assertEquals(32, salt.length());
        }

        @Test
        @DisplayName("generateSalt() 默认返回 32 位十六进制串（16 字节）")
        void shouldReturnDefaultLengthSalt() {
            String salt = PwdUtils.generateSalt();
            assertNotNull(salt);
            assertEquals(32, salt.length());
        }

        @Test
        @DisplayName("每次调用 generateSalt 返回不同的随机盐值")
        void shouldReturnDifferentSaltEachCall() {
            String s1 = PwdUtils.generateSalt();
            String s2 = PwdUtils.generateSalt();
            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("生成的盐值仅包含十六进制字符")
        void shouldContainOnlyHexChars() {
            String salt = PwdUtils.generateSalt(32);
            assertTrue(salt.matches("^[0-9a-f]+$"));
        }
    }

    // ==================== checkPasswordStrength ====================

    @Nested
    @DisplayName("checkPasswordStrength - 密码强度校验")
    class CheckPasswordStrengthTest {

        @Test
        @DisplayName("null 密码返回 WEAK")
        void shouldReturnWeakWhenPasswordIsNull() {
            assertEquals("WEAK", PwdUtils.checkPasswordStrength(null));
        }

        @Test
        @DisplayName("空字符串密码返回 WEAK")
        void shouldReturnWeakWhenPasswordIsEmpty() {
            assertEquals("WEAK", PwdUtils.checkPasswordStrength(""));
        }

        @Test
        @DisplayName("短密码仅小写字母返回 WEAK")
        void shouldReturnWeakForShortLowercasePassword() {
            // 长度 3 < 8，仅小写字母 => score 0 => WEAK
            assertEquals("WEAK", PwdUtils.checkPasswordStrength("abc"));
        }

        @Test
        @DisplayName("长度 8 但仅小写字母返回 WEAK")
        void shouldReturnWeakForEightCharLowercasePassword() {
            // 长度 8 => score 1，无大写/数字/特殊字符 => 总分 1 => WEAK
            assertEquals("WEAK", PwdUtils.checkPasswordStrength("abcdefgh"));
        }

        @Test
        @DisplayName("长度 9 含大小写+数字返回 MEDIUM")
        void shouldReturnMediumForMixedCaseWithDigit() {
            // 长度 9 => score 1，大小写 => +1，数字 => +1，无特殊字符 => 总分 3 => MEDIUM
            assertEquals("MEDIUM", PwdUtils.checkPasswordStrength("Abcdefgh1"));
        }

        @Test
        @DisplayName("长度 16 仅小写字母返回 MEDIUM")
        void shouldReturnMediumForLongLowercasePassword() {
            // 长度 16 => score 3，无其他类型 => 总分 3 => MEDIUM
            assertEquals("MEDIUM", PwdUtils.checkPasswordStrength("abcdefghijklmnop"));
        }

        @Test
        @DisplayName("长度 13 含大小写+数字+特殊字符返回 STRONG")
        void shouldReturnStrongForMixedCaseDigitSpecial() {
            // 长度 13 => score 2 (>=8, >=12)，大小写 => +1，数字 => +1，特殊 => +1 => 总分 5 => STRONG
            assertEquals("STRONG", PwdUtils.checkPasswordStrength("Abcdefgh123!"));
        }

        @Test
        @DisplayName("长度 17 含全部字符类型返回 STRONG")
        void shouldReturnStrongForLongComplexPassword() {
            // 长度 17 => score 3，大小写 => +1，数字 => +1，特殊 => +1 => 总分 6 => STRONG
            assertEquals("STRONG", PwdUtils.checkPasswordStrength("Abcdefgh12345678!"));
        }

        @Test
        @DisplayName("强度返回值仅可能为 WEAK / MEDIUM / STRONG")
        void shouldReturnValidStrengthLevel() {
            String strength = PwdUtils.checkPasswordStrength("SomePassword1!");
            assertTrue(strength.equals("WEAK") || strength.equals("MEDIUM") || strength.equals("STRONG"));
        }
    }
}
