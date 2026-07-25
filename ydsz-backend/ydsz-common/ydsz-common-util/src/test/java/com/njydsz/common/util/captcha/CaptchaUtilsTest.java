package com.njydsz.common.util.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CaptchaUtils} 单元测试 — 覆盖 5 种验证码生成的关键路径。
 *
 * <p>由于验证码具有随机性，测试只校验：
 * <ul>
 *   <li>返回结果非 null</li>
 *   <li>code 非空且长度符合预期</li>
 *   <li>image 为非空 BufferedImage</li>
 *   <li>验证码校验 API 行为正确</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("CaptchaUtils 验证码工具测试")
class CaptchaUtilsTest {

    @Test
    @DisplayName("数字验证码 — 长度符合预期且全部为数字")
    void generateNumeric() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateNumeric(6);
        assertThat(result).isNotNull();
        assertThat(result.getCode()).hasSize(6);
        assertThat(result.getCode()).matches("\\d{6}");
        assertThat(result.getImage()).isInstanceOf(BufferedImage.class);
    }

    @Test
    @DisplayName("字母验证码 — 长度符合预期")
    void generateAlphabetic() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateAlphabetic(5);
        assertThat(result.getCode()).hasSize(5);
        assertThat(result.getCode()).matches("[A-Za-z]{5}");
    }

    @Test
    @DisplayName("混合验证码 — 长度符合预期")
    void generateAlphanumeric() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateAlphanumeric(8);
        assertThat(result.getCode()).hasSize(8);
        assertThat(result.getCode()).matches("[A-Za-z0-9]{8}");
    }

    @Test
    @DisplayName("算术验证码 — 文本格式符合 'a + b ='")
    void generateArithmetic() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateArithmetic();
        assertThat(result.getText()).containsAnyOf("+", "-", "×");
        assertThat(result.getCode()).matches("\\d+");
    }

    @Test
    @DisplayName("中文验证码 — 长度符合预期")
    void generateChinese() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateChinese(4);
        assertThat(result.getCode()).hasSize(4);
    }

    @Test
    @DisplayName("自定义尺寸 — 图像宽高符合配置")
    void generateCustomSize() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateNumeric(4, 200, 80);
        BufferedImage img = result.getImage();
        assertThat(img.getWidth()).isEqualTo(200);
        assertThat(img.getHeight()).isEqualTo(80);
    }

    @Test
    @DisplayName("matches — 大小写不敏感匹配")
    void matchesCaseInsensitive() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateNumeric(4);
        String code = result.getCode();
        assertThat(result.matches(code)).isTrue();
        assertThat(result.matches(code.toLowerCase())).isTrue();
        assertThat(result.matches(code.toUpperCase())).isTrue();
    }

    @Test
    @DisplayName("matches — 错误验证码返回 false")
    void matchesWrongCode() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateNumeric(4);
        // 找一个肯定不等于 code 的字符串
        String wrongCode = result.getCode().equals("0000") ? "1111" : "0000";
        assertThat(result.matches(wrongCode)).isFalse();
    }

    @Test
    @DisplayName("matches — null / 空字符串返回 false")
    void matchesNullAndEmpty() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateNumeric(4);
        assertThat(result.matches(null)).isFalse();
        assertThat(result.matches("")).isFalse();
        assertThat(result.matches("   ")).isFalse();
    }

    @Test
    @DisplayName("toString 包含 code 字段")
    void toStringContainsCode() {
        CaptchaUtils.CaptchaResult result = CaptchaUtils.generateNumeric(4);
        assertThat(result.toString()).contains("code='").contains(result.getCode());
    }
}
