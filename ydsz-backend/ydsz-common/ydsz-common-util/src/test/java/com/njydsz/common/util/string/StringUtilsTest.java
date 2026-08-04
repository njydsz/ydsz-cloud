package com.njydsz.common.util.string;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * StringUtils 单元测试
 *
 * <p>覆盖核心方法：判空、命名转换、格式化、脱敏、格式校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("StringUtils 工具类测试")
class StringUtilsTest {

    // ==================== 判空方法 ====================

    @Nested
    @DisplayName("判空方法")
    class IsBlankTest {

        @Test
        @DisplayName("null 字符串为 blank")
        void isBlank_null() {
            assertThat(StringUtils.isBlank(null)).isTrue();
        }

        @Test
        @DisplayName("空字符串为 blank")
        void isBlank_empty() {
            assertThat(StringUtils.isBlank("")).isTrue();
        }

        @Test
        @DisplayName("纯空白字符为 blank")
        void isBlank_whitespace() {
            assertThat(StringUtils.isBlank("   ")).isTrue();
            assertThat(StringUtils.isBlank("\t\n\r")).isTrue();
        }

        @Test
        @DisplayName("有内容的字符串不为 blank")
        void isBlank_content() {
            assertThat(StringUtils.isBlank("hello")).isFalse();
            assertThat(StringUtils.isBlank(" a ")).isFalse();
        }

        @Test
        @DisplayName("hasText 等价于 isNotBlank")
        void hasText() {
            assertThat(StringUtils.hasText("hello")).isTrue();
            assertThat(StringUtils.hasText("")).isFalse();
            assertThat(StringUtils.hasText(null)).isFalse();
        }
    }

    @Test
    @DisplayName("isEmpty 支持多种类型")
    void isEmpty_multiType() {
        assertThat(StringUtils.isEmpty((Object) null)).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isEmpty(Collections.emptyList())).isTrue();
        assertThat(StringUtils.isEmpty(Collections.emptyMap())).isTrue();
        assertThat(StringUtils.isEmpty(new Object[]{})).isTrue();
        assertThat(StringUtils.isEmpty("hello")).isFalse();
        assertThat(StringUtils.isEmpty(Arrays.asList(1, 2))).isFalse();
    }

    // ==================== 命名转换 ====================

    @Nested
    @DisplayName("命名转换方法")
    class CaseConversionTest {

        @Test
        @DisplayName("下划线转驼峰 - 全小写")
        void toCamelCase_lower() {
            assertThat(StringUtils.toCamelCase("user_name")).isEqualTo("userName");
        }

        @Test
        @DisplayName("下划线转驼峰 - 全大写转首字母大写")
        void toCamelCase_upper() {
            assertThat(StringUtils.toCamelCase("USER_NAME")).isEqualTo("userName");
        }

        @Test
        @DisplayName("下划线转驼峰 - 混合大小写保留")
        void toCamelCase_mixed() {
            assertThat(StringUtils.toCamelCase("User_Name")).isEqualTo("userName");
        }

        @Test
        @DisplayName("下划线转驼峰 - null 返回 null")
        void toCamelCase_null() {
            assertThat(StringUtils.toCamelCase(null)).isNull();
        }

        @Test
        @DisplayName("驼峰转下划线")
        void toUnderScoreCase() {
            assertThat(StringUtils.toUnderScoreCase("userName")).isEqualTo("user_name");
            assertThat(StringUtils.toUnderScoreCase("UserName")).isEqualTo("user_name");
        }

        @Test
        @DisplayName("驼峰转下划线 - null 返回 null")
        void toUnderScoreCase_null() {
            assertThat(StringUtils.toUnderScoreCase(null)).isNull();
        }
    }

    // ==================== 格式化 ====================

    @Test
    @DisplayName("format - {} 占位符替换")
    void format() {
        assertThat(StringUtils.format("Hello, {}!", "World")).isEqualTo("Hello, World!");
        assertThat(StringUtils.format("{}, {}!", "Hello", "World")).isEqualTo("Hello, World!");
        assertThat(StringUtils.format("No placeholders")).isEqualTo("No placeholders");
        assertThat(StringUtils.format(null, "arg")).isNull();
        assertThat(StringUtils.format("Extra {} placeholder", "one")).isEqualTo("Extra one placeholder");
    }

    // ==================== 脱敏 ====================

    @Nested
    @DisplayName("脱敏方法")
    class MaskTest {

        @Test
        @DisplayName("手机号脱敏")
        void maskMobile() {
            assertThat(StringUtils.maskMobile("13812345678")).isEqualTo("138****5678");
            assertThat(StringUtils.maskMobile("123")).isEqualTo("123");
            assertThat(StringUtils.maskMobile(null)).isNull();
        }

        @Test
        @DisplayName("身份证号脱敏")
        void maskIdCard() {
            String idCard = "110101199001011234";
            String masked = StringUtils.maskIdCard(idCard);
            assertThat(masked).startsWith("110101");
            assertThat(masked).endsWith("1234");
            assertThat(masked).contains("*");
        }

        @Test
        @DisplayName("邮箱脱敏")
        void maskEmail() {
            assertThat(StringUtils.maskEmail("test@example.com")).contains("*");
            assertThat(StringUtils.maskEmail("ab@example.com")).isEqualTo("ab@example.com");
        }
    }

    // ==================== 验证方法 ====================

    @Nested
    @DisplayName("验证方法")
    class ValidateTest {

        @Test
        @DisplayName("邮箱验证")
        void isEmail() {
            assertThat(StringUtils.isEmail("test@example.com")).isTrue();
            assertThat(StringUtils.isEmail("invalid")).isFalse();
            assertThat(StringUtils.isEmail(null)).isFalse();
        }

        @Test
        @DisplayName("手机号验证")
        void isMobile() {
            assertThat(StringUtils.isMobile("13812345678")).isTrue();
            assertThat(StringUtils.isMobile("12345678901")).isFalse();
            assertThat(StringUtils.isMobile(null)).isFalse();
        }

        @Test
        @DisplayName("IPv4 验证")
        void isIpv4() {
            assertThat(StringUtils.isIpv4("192.168.1.1")).isTrue();
            assertThat(StringUtils.isIpv4("256.1.1.1")).isFalse();
            assertThat(StringUtils.isIpv4(null)).isFalse();
        }
    }

    // ==================== defaultIfBlank ====================

    @Test
    @DisplayName("defaultIfBlank")
    void defaultIfBlank() {
        assertThat(StringUtils.defaultIfBlank("  ", "default")).isEqualTo("default");
        assertThat(StringUtils.defaultIfBlank("hello", "default")).isEqualTo("hello");
        assertThat(StringUtils.defaultIfBlank(null, "default")).isEqualTo("default");
    }
}
