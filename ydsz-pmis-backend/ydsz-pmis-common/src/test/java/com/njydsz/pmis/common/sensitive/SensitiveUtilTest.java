package com.njydsz.pmis.common.sensitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveUtil 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("SensitiveUtil 测试")
class SensitiveUtilTest {

    // ==================== 姓名脱敏 ====================

    @Test
    @DisplayName("姓名脱敏 - 三字名应保留首尾，中间用 * 填充")
    void maskName_shouldMaskThreeCharName() {
        assertEquals("张*丰", SensitiveUtil.maskName("张三丰"));
    }

    @Test
    @DisplayName("姓名脱敏 - 二字名应保留首字加 *")
    void maskName_shouldMaskTwoCharName() {
        assertEquals("张*", SensitiveUtil.maskName("张三"));
    }

    @Test
    @DisplayName("姓名脱敏 - 单字名应加 *")
    void maskName_shouldMaskSingleChar() {
        assertEquals("张*", SensitiveUtil.maskName("张"));
    }

    @Test
    @DisplayName("姓名脱敏 - 多字名应保留首尾")
    void maskName_shouldMaskLongName() {
        String result = SensitiveUtil.maskName("欧阳娜娜娜");
        assertEquals('欧', result.charAt(0));
        assertEquals('娜', result.charAt(result.length() - 1));
        assertTrue(result.contains("*"));
    }

    // ==================== 身份证脱敏 ====================

    @Test
    @DisplayName("身份证脱敏 - 18 位身份证应保留前 6 后 4")
    void maskIdCard_shouldMask18Digit() {
        String idCard = "320102199001011234";
        String result = SensitiveUtil.maskIdCard(idCard);
        assertEquals("320102", result.substring(0, 6));
        assertEquals("1234", result.substring(result.length() - 4));
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("身份证脱敏 - 短字符串（<=10 位）应原样返回")
    void maskIdCard_shouldReturnOriginalForShort() {
        assertEquals("1234567890", SensitiveUtil.maskIdCard("1234567890"));
        assertEquals("12345", SensitiveUtil.maskIdCard("12345"));
    }

    // ==================== 手机号脱敏 ====================

    @Test
    @DisplayName("手机号脱敏 - 11 位手机号应保留前 3 后 4")
    void maskPhone_shouldMask11Digit() {
        assertEquals("138****8000", SensitiveUtil.maskPhone("13812348000"));
    }

    @Test
    @DisplayName("手机号脱敏 - 短于 7 位应返回 ****")
    void maskPhone_shouldReturnAsterisksForShort() {
        assertEquals("****", SensitiveUtil.maskPhone("123456"));
        assertEquals("****", SensitiveUtil.maskPhone("1"));
    }

    // ==================== 邮箱脱敏 ====================

    @Test
    @DisplayName("邮箱脱敏 - 正常邮箱应保留前 3 字符和 @ 后部分")
    void maskEmail_shouldMaskNormalEmail() {
        assertEquals("abc***@example.com", SensitiveUtil.maskEmail("abcde@example.com"));
    }

    @Test
    @DisplayName("邮箱脱敏 - 短前缀邮箱应保留首字符")
    void maskEmail_shouldMaskShortPrefixEmail() {
        assertEquals("a***@x.com", SensitiveUtil.maskEmail("ab@x.com"));
    }

    @Test
    @DisplayName("邮箱脱敏 - 无 @ 符号应前加 ***")
    void maskEmail_shouldHandleNoAt() {
        assertEquals("***notanemail", SensitiveUtil.maskEmail("notanemail"));
    }

    // ==================== 银行卡脱敏 ====================

    @Test
    @DisplayName("银行卡脱敏 - 16 位银行卡应保留前 4 后 4")
    void maskBankCard_shouldMask16Digit() {
        String result = SensitiveUtil.maskBankCard("6222021234567890");
        assertEquals("6222", result.substring(0, 4));
        assertEquals("7890", result.substring(result.length() - 4));
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("银行卡脱敏 - 短于等于 8 位应原样返回")
    void maskBankCard_shouldReturnOriginalForShort() {
        assertEquals("12345678", SensitiveUtil.maskBankCard("12345678"));
        assertEquals("1234", SensitiveUtil.maskBankCard("1234"));
    }

    // ==================== 地址脱敏 ====================

    @Test
    @DisplayName("地址脱敏 - 长地址应保留前后缀")
    void maskAddress_shouldMaskLongAddress() {
        String result = SensitiveUtil.maskAddress("江苏省南京市秦淮区某某路100号", 6, 3);
        assertEquals("江苏省南京市***00号", result);
    }

    @Test
    @DisplayName("地址脱敏 - 短地址（prefixKeep 不超长度）应保留前缀加 ***")
    void maskAddress_shouldMaskShortAddress() {
        String result = SensitiveUtil.maskAddress("南京路1号", 2, 1);
        assertEquals("南京***", result);
    }

    // ==================== 自定义脱敏 ====================

    @Test
    @DisplayName("自定义脱敏 - 已注册 handler 应正确脱敏")
    void maskCustom_shouldUseRegisteredHandler() {
        SensitiveUtil.register("test-upper", String::toUpperCase);
        assertEquals("HELLO", SensitiveUtil.maskCustom("hello", "test-upper"));
    }

    @Test
    @DisplayName("自定义脱敏 - 未注册 handler 应返回原值")
    void maskCustom_shouldReturnOriginalForUnregistered() {
        assertEquals("hello", SensitiveUtil.maskCustom("hello", "no-such-handler"));
    }

    @Test
    @DisplayName("自定义脱敏 - handler 抛出异常应返回原值")
    void maskCustom_shouldReturnOriginalOnException() {
        SensitiveUtil.register("broken", s -> {
            throw new RuntimeException("boom");
        });
        assertEquals("hello", SensitiveUtil.maskCustom("hello", "broken"));
    }

    // ==================== desensitize 方法 ====================

    @Test
    @DisplayName("desensitize - null 或空字符串应返回原值")
    void desensitize_shouldReturnOriginalForNullOrEmpty() {
        assertNull(SensitiveUtil.desensitize(null, SensitiveStrategy.NAME));
        assertEquals("", SensitiveUtil.desensitize("", SensitiveStrategy.NAME));
    }

    @Test
    @DisplayName("desensitize - NONE 策略应返回原值")
    void desensitize_shouldReturnOriginalForNoneStrategy() {
        assertEquals("hello", SensitiveUtil.desensitize("hello", SensitiveStrategy.NONE));
    }

    @Test
    @DisplayName("desensitize - null 策略应返回原值")
    void desensitize_shouldReturnOriginalForNullStrategy() {
        assertEquals("hello", SensitiveUtil.desensitize("hello", null));
    }

    @Test
    @DisplayName("desensitize - NAME 策略")
    void desensitize_shouldUseNameStrategy() {
        assertEquals("张*", SensitiveUtil.desensitize("张三", SensitiveStrategy.NAME));
    }

    @Test
    @DisplayName("desensitize - ID_CARD 策略")
    void desensitize_shouldUseIdCardStrategy() {
        String result = SensitiveUtil.desensitize("320102199001011234", SensitiveStrategy.ID_CARD);
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("desensitize - PHONE 策略")
    void desensitize_shouldUsePhoneStrategy() {
        assertEquals("138****8000", SensitiveUtil.desensitize("13812348000", SensitiveStrategy.PHONE));
    }

    @Test
    @DisplayName("desensitize - EMAIL 策略")
    void desensitize_shouldUseEmailStrategy() {
        assertEquals("abc***@example.com", SensitiveUtil.desensitize("abcde@example.com", SensitiveStrategy.EMAIL));
    }

    @Test
    @DisplayName("desensitize - BANK_CARD 策略")
    void desensitize_shouldUseBankCardStrategy() {
        String result = SensitiveUtil.desensitize("6222021234567890", SensitiveStrategy.BANK_CARD);
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("desensitize - ADDRESS 策略（默认前 1 后 1 保留）")
    void desensitize_shouldUseAddressStrategyWithDefaults() {
        String result = SensitiveUtil.desensitize("江苏省南京市秦淮区某某路100号", SensitiveStrategy.ADDRESS);
        assertTrue(result.contains("*"));
    }

    @Test
    @DisplayName("desensitize - CUSTOM 策略，未注册应返回原值")
    void desensitize_shouldUseCustomStrategy() {
        assertEquals("hello", SensitiveUtil.desensitize("hello", SensitiveStrategy.CUSTOM));
    }
}