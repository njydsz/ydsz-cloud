package com.njydsz.common.util.regex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * RegexUtils 单元测试
 *
 * <p>覆盖核心方法：手机号、邮箱、身份证、URL、IP、日期、车牌等正则验证。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("RegexUtils 工具类测试")
class RegexUtilsTest {

    // ==================== 手机号验证 ====================

    @Nested
    @DisplayName("手机号验证")
    class MobileTest {

    @Test
    @DisplayName("简单手机号 - 合法")
    void isMobileSimple_valid() {
        assertThat(RegexUtils.isMobile("13812345678")).isTrue();
        assertThat(RegexUtils.isMobile("15912345678")).isTrue();
        assertThat(RegexUtils.isMobile("18612345678")).isTrue();
    }

    @Test
    @DisplayName("简单手机号 - 非法")
    void isMobileSimple_invalid() {
        assertThat(RegexUtils.isMobile("12345678901")).isFalse();
        assertThat(RegexUtils.isMobile("1381234567")).isFalse();
        assertThat(RegexUtils.isMobile(null)).isFalse();
    }

        @Test
        @DisplayName("精确手机号 - 合法")
        void isMobileExact_valid() {
            assertThat(RegexUtils.isMobileExact("13812345678")).isTrue();
            assertThat(RegexUtils.isMobileExact("19912345678")).isTrue();
        }

        @Test
        @DisplayName("精确手机号 - 非法")
        void isMobileExact_invalid() {
            assertThat(RegexUtils.isMobileExact("12812345678")).isFalse();
            assertThat(RegexUtils.isMobileExact(null)).isFalse();
        }
    }

    // ==================== 邮箱验证 ====================

    @Test
    @DisplayName("邮箱验证")
    void isEmail() {
        assertThat(RegexUtils.isEmail("test@example.com")).isTrue();
        assertThat(RegexUtils.isEmail("user.name@domain.org")).isTrue();
        assertThat(RegexUtils.isEmail("invalid")).isFalse();
        assertThat(RegexUtils.isEmail("@domain.com")).isFalse();
        assertThat(RegexUtils.isEmail(null)).isFalse();
    }

    // ==================== 身份证验证 ====================

    @Test
    @DisplayName("身份证验证")
    void isIdCard() {
        assertThat(RegexUtils.isIdCard("110101199001011234")).isTrue();
        assertThat(RegexUtils.isIdCard("11010119900101123X")).isTrue();
        assertThat(RegexUtils.isIdCard("12345")).isFalse();
        assertThat(RegexUtils.isIdCard(null)).isFalse();
    }

    // ==================== URL 验证 ====================

    @Test
    @DisplayName("URL 验证")
    void isUrl() {
        assertThat(RegexUtils.isUrl("https://www.example.com")).isTrue();
        assertThat(RegexUtils.isUrl("http://localhost:8080/path")).isTrue();
        assertThat(RegexUtils.isUrl("ftp://files.example.com")).isTrue();
        assertThat(RegexUtils.isUrl("not-a-url")).isFalse();
        assertThat(RegexUtils.isUrl(null)).isFalse();
    }

    // ==================== IP 验证 ====================

    @Nested
    @DisplayName("IP 验证")
    class IpTest {

        @Test
        @DisplayName("IPv4 - 合法")
        void isIp_valid() {
            assertThat(RegexUtils.isIp("192.168.1.1")).isTrue();
            assertThat(RegexUtils.isIp("10.0.0.1")).isTrue();
            assertThat(RegexUtils.isIp("255.255.255.255")).isTrue();
        }

        @Test
        @DisplayName("IPv4 - 非法")
        void isIp_invalid() {
            assertThat(RegexUtils.isIp("256.1.1.1")).isFalse();
            assertThat(RegexUtils.isIp("192.168.1")).isFalse();
            assertThat(RegexUtils.isIp(null)).isFalse();
        }

        @Test
        @DisplayName("简单 IP - 合法")
        void isIpSimple_valid() {
            assertThat(RegexUtils.isIpSimple("192.168.1.1")).isTrue();
        }

        @Test
        @DisplayName("简单 IP - 非法")
        void isIpSimple_invalid() {
            assertThat(RegexUtils.isIpSimple("999.999.999.999")).isFalse();
            assertThat(RegexUtils.isIpSimple(null)).isFalse();
        }
    }

    // ==================== 日期验证 ====================

    @Test
    @DisplayName("日期验证")
    void isDate() {
        assertThat(RegexUtils.isDate("2024-01-15")).isTrue();
        assertThat(RegexUtils.isDate("2024-1-5")).isTrue();
        assertThat(RegexUtils.isDate("2024/01/15")).isFalse();
        assertThat(RegexUtils.isDate(null)).isFalse();
    }

    @Test
    @DisplayName("日期时间验证")
    void isDateTime() {
        assertThat(RegexUtils.isDateTime("2024-01-15 10:30:00")).isTrue();
        assertThat(RegexUtils.isDateTime("2024-1-5 1:30:00")).isTrue();
        assertThat(RegexUtils.isDateTime("2024-01-15")).isFalse();
        assertThat(RegexUtils.isDateTime(null)).isFalse();
    }

    // ==================== 车牌验证 ====================

    @Test
    @DisplayName("车牌号验证")
    void isLicensePlate() {
        assertThat(RegexUtils.isLicensePlate("京A12345")).isTrue();
        assertThat(RegexUtils.isLicensePlate("沪B99999")).isTrue();
        assertThat(RegexUtils.isLicensePlate("粤Z12345D")).isTrue();
        assertThat(RegexUtils.isLicensePlate("invalid")).isFalse();
        assertThat(RegexUtils.isLicensePlate(null)).isFalse();
    }

    // ==================== MAC 地址验证 ====================

    @Test
    @DisplayName("MAC 地址验证")
    void isMac() {
        assertThat(RegexUtils.isMac("00:1A:2B:3C:4D:5E")).isTrue();
        assertThat(RegexUtils.isMac("00-1A-2B-3C-4D-5E")).isTrue();
        assertThat(RegexUtils.isMac("001A.2B3C.4D5E")).isTrue();
        assertThat(RegexUtils.isMac("invalid")).isFalse();
        assertThat(RegexUtils.isMac(null)).isFalse();
    }

    // ==================== 数字验证 ====================

    @Nested
    @DisplayName("数字验证")
    class NumberTest {

        @Test
        @DisplayName("整数验证")
        void isInteger() {
            assertThat(RegexUtils.isInteger("123")).isTrue();
            assertThat(RegexUtils.isInteger("-123")).isTrue();
            assertThat(RegexUtils.isInteger("+123")).isTrue();
            assertThat(RegexUtils.isInteger("12.3")).isFalse();
            assertThat(RegexUtils.isInteger(null)).isFalse();
        }

        @Test
        @DisplayName("正整数验证")
        void isPositiveInteger() {
            assertThat(RegexUtils.isPositiveInteger("123")).isTrue();
            assertThat(RegexUtils.isPositiveInteger("0")).isTrue();
            assertThat(RegexUtils.isPositiveInteger("-123")).isFalse();
            assertThat(RegexUtils.isPositiveInteger(null)).isFalse();
        }

        @Test
        @DisplayName("小数验证")
        void isDecimal() {
            assertThat(RegexUtils.isDecimal("123.45")).isTrue();
            assertThat(RegexUtils.isDecimal("-123.45")).isTrue();
            assertThat(RegexUtils.isDecimal("123")).isTrue();
            assertThat(RegexUtils.isDecimal("abc")).isFalse();
            assertThat(RegexUtils.isDecimal(null)).isFalse();
        }
    }

    // ==================== getPattern ====================

    @Nested
    @DisplayName("getPattern 方法")
    class GetPatternTest {

        @Test
        @DisplayName("获取已注册 Pattern")
        void getPattern_registered() {
            Pattern pattern = RegexUtils.getPattern(RegexUtils.EMAIL);
            assertThat(pattern).isNotNull();
            assertThat(pattern.matcher("test@example.com").matches()).isTrue();
        }

        @Test
        @DisplayName("获取未注册 Pattern - 返回 null")
        void getPattern_unregistered() {
            assertThat(RegexUtils.getPattern("NON_EXISTENT_TYPE")).isNull();
        }

        @Test
        @DisplayName("null type - 返回 null")
        void getPattern_null() {
            assertThat(RegexUtils.getPattern(null)).isNull();
        }
    }

    // ==================== extractGroup ====================

    @Test
    @DisplayName("提取分组")
    void extractGroup() {
        assertThat(RegexUtils.extractGroup("\\d+", "abc123def", 1)).isEqualTo("123");
        assertThat(RegexUtils.extractGroup("\\d+", "no numbers", 1)).isNull();
        assertThat(RegexUtils.extractGroup("\\d+", null, 1)).isNull();
    }

    // ==================== isLetterOrDigit 字符串检查 ====================

    @Test
    @DisplayName("字母数字字符串验证")
    void isAlphaNumeric() {
        assertThat(RegexUtils.isAlphaNumeric("abc123")).isTrue();
        assertThat(RegexUtils.isAlphaNumeric("ABC")).isTrue();
        assertThat(RegexUtils.isAlphaNumeric("123")).isTrue();
        assertThat(RegexUtils.isAlphaNumeric("abc_123")).isFalse();
        assertThat(RegexUtils.isAlphaNumeric("abc 123")).isFalse();
    }
}
