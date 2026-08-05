package com.remisoft.common.util.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.remisoft.common.util.security.PasswordStrengthChecker.PasswordStrengthLevel;
import com.remisoft.common.util.security.PwdUtils.PasswordStrength;

/**
 * {@link PwdUtils} 单元测试 — 覆盖 BCrypt / PBKDF2 / 密码强度 SPI 等关键能力。
 *
 * @author remi-team
 * @since 1.3.0
 */
@DisplayName("PwdUtils 密码工具测试")
class PwdUtilsTest {

    @Nested
    @DisplayName("BCrypt 哈希与验证")
    class Bcrypt {

        @Test
        @DisplayName("hashPasswordBCrypt 输出以 $2a$12$ 开头")
        void bcryptHashFormat() {
            String hash = PwdUtils.hashPasswordBCrypt("mypass");
            assertThat(hash).startsWith("$2a$12$");
        }

        @Test
        @DisplayName("verifyPasswordBCrypt 正确密码返回 true")
        void correctPasswordShouldMatch() {
            String hash = PwdUtils.hashPasswordBCrypt("mypass");
            assertThat(PwdUtils.verifyPasswordBCrypt("mypass", hash)).isTrue();
        }

        @Test
        @DisplayName("verifyPasswordBCrypt 错误密码返回 false")
        void wrongPasswordShouldNotMatch() {
            String hash = PwdUtils.hashPasswordBCrypt("mypass");
            assertThat(PwdUtils.verifyPasswordBCrypt("wrongpass", hash)).isFalse();
        }

        @Test
        @DisplayName("isBCryptFormat 正确识别 BCrypt 字符串")
        void shouldRecognizeBcryptFormat() {
            String hash = PwdUtils.hashPasswordBCrypt("test");
            assertThat(PwdUtils.isBCryptFormat(hash)).isTrue();
            assertThat(PwdUtils.isBCryptFormat("not-a-hash")).isFalse();
            assertThat(PwdUtils.isBCryptFormat(null)).isFalse();
        }

        @Test
        @DisplayName("每次 hash 输出不同（随机盐值）")
        void hashesShouldDiffer() {
            String hash1 = PwdUtils.hashPasswordBCrypt("same");
            String hash2 = PwdUtils.hashPasswordBCrypt("same");
            assertThat(hash1).isNotEqualTo(hash2);
            // 但都能验证
            assertThat(PwdUtils.verifyPasswordBCrypt("same", hash1)).isTrue();
            assertThat(PwdUtils.verifyPasswordBCrypt("same", hash2)).isTrue();
        }
    }

    @Nested
    @DisplayName("PBKDF2 哈希与验证")
    class Pbkdf2 {

        @Test
        @DisplayName("encodePBKDF2 格式 salt:iterations:hash")
        void pbkdf2FormatIsCorrect() {
            String saltHex = PwdUtils.generateSalt(16);
            String encoded = PwdUtils.encodePBKDF2("password".toCharArray(), saltHex);
            String[] parts = encoded.split(":");
            assertThat(parts).hasSize(3);
            // 迭代次数默认 600000
            assertThat(Integer.parseInt(parts[1])).isEqualTo(600000);
        }

        @Test
        @DisplayName("verifyPBKDF2 正确密码通过验证")
        void correctPasswordShouldVerify() {
            String saltHex = PwdUtils.generateSalt(16);
            String encoded = PwdUtils.encodePBKDF2("mypassword".toCharArray(), saltHex);
            assertThat(PwdUtils.verifyPBKDF2("mypassword", encoded)).isTrue();
        }

        @Test
        @DisplayName("verifyPBKDF2 错误密码不通过")
        void wrongPasswordShouldNotVerify() {
            String saltHex = PwdUtils.generateSalt(16);
            String encoded = PwdUtils.encodePBKDF2("mypassword".toCharArray(), saltHex);
            assertThat(PwdUtils.verifyPBKDF2("wrong", encoded)).isFalse();
        }

        @Test
        @DisplayName("encodePBKDF2WithAutoSalt 自动生成盐值")
        void autoSaltShouldWork() {
            String encoded = PwdUtils.encodePBKDF2WithAutoSalt("test".toCharArray());
            assertThat(encoded).contains(":");
            assertThat(PwdUtils.verifyPBKDF2("test", encoded)).isTrue();
        }

        @Test
        @DisplayName("encodePBKDF2 拒绝 null 密码")
        void shouldRejectNullPassword() {
            assertThatThrownBy(() -> PwdUtils.encodePBKDF2(null, "AABB"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("encodePBKDF2 拒绝空盐值")
        void shouldRejectNullSalt() {
            assertThatThrownBy(() -> PwdUtils.encodePBKDF2("test".toCharArray(), ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("verifyPBKDF2 格式异常返回 false（不抛异常）")
        void shouldReturnFalseForInvalidFormat() {
            assertThat(PwdUtils.verifyPBKDF2("test", "invalid")).isFalse();
            assertThat(PwdUtils.verifyPBKDF2("test", null)).isFalse();
            assertThat(PwdUtils.verifyPBKDF2(null, "abc:def:123")).isFalse();
        }

        @Test
        @DisplayName("verifyPBKDF2 恶意迭代次数被拒绝（> 10000000）")
        void maliciousIterationShouldFail() {
            String saltHex = PwdUtils.generateSalt(16);
            // 构造恶意高迭代次数
            String malicious = saltHex + ":99999999:hash";
            assertThat(PwdUtils.verifyPBKDF2("test", malicious)).isFalse();
        }

        @Test
        @DisplayName("generateSalt 返回 Hex 字符串且长度正确")
        void saltShouldBeHex() {
            String salt = PwdUtils.generateSalt(16);
            assertThat(salt).hasSize(32); // 16 byte = 32 hex chars
            // 验证合法 Hex
            assertThat(salt).matches("[0-9a-f]+");
        }
    }

    @Nested
    @DisplayName("checkPasswordStrength（兼容 1.x 三档）")
    class CompatStrengthCheck {

        @Test
        @DisplayName("null / 空串返回 WEAK")
        void nullOrEmptyShouldReturnWeak() {
            assertThat(PwdUtils.checkPasswordStrength(null)).isEqualTo(PasswordStrength.WEAK);
            assertThat(PwdUtils.checkPasswordStrength("")).isEqualTo(PasswordStrength.WEAK);
        }

        @Test
        @DisplayName("短密码返回 WEAK")
        void shortPasswordShouldBeWeak() {
            assertThat(PwdUtils.checkPasswordStrength("abc")).isEqualTo(PasswordStrength.WEAK);
            assertThat(PwdUtils.checkPasswordStrength("abcdefgh")).isNotEqualTo(PasswordStrength.STRONG);
        }

        @Test
        @DisplayName("长复合密码返回 STRONG")
        void complexLongPasswordShouldBeStrong() {
            assertThat(PwdUtils.checkPasswordStrength("MyP@ssw0rd!2024")).isEqualTo(PasswordStrength.STRONG);
        }

        @Test
        @DisplayName("通用弱密码（如 123456）返回 WEAK")
        void commonWeakPasswordShouldBeWeak() {
            assertThat(PwdUtils.checkPasswordStrength("123456")).isEqualTo(PasswordStrength.WEAK);
        }
    }

    @Nested
    @DisplayName("checkPasswordStrengthLevel（五档精细评分）")
    class LevelStrengthCheck {

        @Test
        @DisplayName("null / 空串返回 VERY_WEAK")
        void nullOrEmptyShouldReturnVeryWeak() {
            assertThat(PwdUtils.checkPasswordStrengthLevel(null)).isEqualTo(PasswordStrengthLevel.VERY_WEAK);
            assertThat(PwdUtils.checkPasswordStrengthLevel("")).isEqualTo(PasswordStrengthLevel.VERY_WEAK);
        }

        @Test
        @DisplayName("纯数字短密码返回 VERY_WEAK")
        void pureDigitShortShouldBeVeryWeak() {
            assertThat(PwdUtils.checkPasswordStrengthLevel("123")).isEqualTo(PasswordStrengthLevel.VERY_WEAK);
        }

        @Test
        @DisplayName("强复合密码至少是 MEDIUM")
        void strongCompositeShouldBeAtLeastMedium() {
            PasswordStrengthLevel level = PwdUtils.checkPasswordStrengthLevel("AbCd1234!@#$");
            assertThat(level).isGreaterThanOrEqualTo(PasswordStrengthLevel.MEDIUM);
        }

        @Test
        @DisplayName("极强密码（长且复合）返回 VERY_STRONG")
        void extremelyStrongPassword() {
            assertThat(PwdUtils.checkPasswordStrengthLevel("Kx!9z@Pw#2Lm&8Qv$5Rt")).isEqualTo(PasswordStrengthLevel.VERY_STRONG);
        }
    }

    @Nested
    @DisplayName("describePasswordStrength / suggestPasswordImprovement（国际化）")
    class I18nSuggestions {

        @Test
        @DisplayName("describe 中文描述非空")
        void chineseDescribeShouldBeNonEmpty() {
            String desc = PwdUtils.describePasswordStrength("abc", Locale.CHINESE);
            assertThat(desc).isNotEmpty();
        }

        @Test
        @DisplayName("describe 英文描述非空")
        void englishDescribeShouldBeNonEmpty() {
            String desc = PwdUtils.describePasswordStrength("abc", Locale.ENGLISH);
            assertThat(desc).isNotEmpty();
        }

        @Test
        @DisplayName("suggest 弱密码返回至少一条建议")
        void shouldSuggestForWeakPassword() {
            String suggestion = PwdUtils.suggestPasswordImprovement("abc", Locale.CHINESE);
            assertThat(suggestion).isNotEmpty();
        }

        @Test
        @DisplayName("suggest 强密码返回空字符串")
        void shouldNotSuggestForStrongPassword() {
            String suggestion = PwdUtils.suggestPasswordImprovement("Kx!9z@Pw#2Lm&8Qv$5Rt", Locale.CHINESE);
            assertThat(suggestion).isEmpty();
        }

        @Test
        @DisplayName("suggest null 密码返回输入提示")
        void shouldSuggestForNullPassword() {
            String suggestion = PwdUtils.suggestPasswordImprovement(null, Locale.CHINESE);
            assertThat(suggestion).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("PasswordStrengthChecker SPI")
    class SpiChecker {

        @Test
        @DisplayName("getPasswordStrengthChecker 返回不 null 实例")
        void checkerShouldNotBeNull() {
            assertThat(PwdUtils.getPasswordStrengthChecker()).isNotNull();
        }

        @Test
        @DisplayName("默认实现 DefaultPasswordStrengthChecker.check(null) 返回 VERY_WEAK")
        void defaultCheckerShouldHandleNull() {
            PasswordStrengthChecker checker = new DefaultPasswordStrengthChecker();
            assertThat(checker.check(null)).isEqualTo(PasswordStrengthLevel.VERY_WEAK);
        }

        @Test
        @DisplayName("DefaultPasswordStrengthChecker.INSTANCE 可用")
        void singletonInstanceShouldBeUsable() {
            PasswordStrengthChecker checker = DefaultPasswordStrengthChecker.INSTANCE;
            assertThat(checker.check("test")).isNotNull();
        }
    }
}
