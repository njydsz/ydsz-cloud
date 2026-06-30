package com.njydsz.pmis.common.sensitive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensitiveUtil 脱敏工具测试")
class SensitiveUtilTest {

    @Test
    @DisplayName("姓名 - 三字保留首末")
    void maskName() {
        assertThat(SensitiveUtil.maskName("张三丰")).isEqualTo("张*丰");
        assertThat(SensitiveUtil.maskName("李四")).isEqualTo("李*");
        assertThat(SensitiveUtil.maskName("王")).isEqualTo("王*");
    }

    @Test
    @DisplayName("身份证保留前6后4")
    void maskIdCard() {
        assertThat(SensitiveUtil.maskIdCard("11010119900101001X"))
                .isEqualTo("110101********001X");
    }

    @Test
    @DisplayName("手机号保留前3后4")
    void maskPhone() {
        assertThat(SensitiveUtil.maskPhone("13800001234")).isEqualTo("138****1234");
    }

    @Test
    @DisplayName("邮箱前3后@")
    void maskEmail() {
        assertThat(SensitiveUtil.maskEmail("alice@example.com"))
                .isEqualTo("ali***@example.com");
        assertThat(SensitiveUtil.maskEmail("ab@example.com"))
                .isEqualTo("a***@example.com");
    }

    @Test
    @DisplayName("银行卡保留前4后4")
    void maskBankCard() {
        assertThat(SensitiveUtil.maskBankCard("6222021234567890123"))
                .isEqualTo("6222***********0123");
    }

    @Test
    @DisplayName("地址保留前缀")
    void maskAddress() {
        assertThat(SensitiveUtil.maskAddress("江苏省南京市雨花台区软件大道101号", 3, 0))
                .isEqualTo("江苏省***");
    }

    @Test
    @DisplayName("null/空 不处理")
    void nullOrEmpty() {
        assertThat(SensitiveUtil.desensitize(null, SensitiveStrategy.PHONE)).isNull();
        assertThat(SensitiveUtil.desensitize("", SensitiveStrategy.PHONE)).isEmpty();
    }

    @Test
    @DisplayName("NONE 策略原样返回")
    void none() {
        assertThat(SensitiveUtil.desensitize("abc", SensitiveStrategy.NONE)).isEqualTo("abc");
    }

    @Test
    @DisplayName("CUSTOM 策略调用注册函数")
    void custom() {
        SensitiveUtil.register("reverse", s -> new StringBuilder(s).reverse().toString());
        assertThat(SensitiveUtil.maskCustom("hello", "reverse")).isEqualTo("olleh");
    }
}
