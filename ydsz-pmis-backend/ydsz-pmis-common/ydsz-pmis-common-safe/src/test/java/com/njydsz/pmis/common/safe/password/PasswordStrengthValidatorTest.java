package com.njydsz.pmis.common.safe.password;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PasswordStrengthValidator} 单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
class PasswordStrengthValidatorTest {

    private final PasswordStrengthValidator validator = PasswordStrengthValidator.createDefault();

    @Test
    @DisplayName("强密码应通过校验")
    void testStrongPassword() {
        var result = validator.validate("MyP@ssw0rd2026");
        assertTrue(result.valid());
        assertTrue(result.score() >= 60);
        assertTrue(result.violations().isEmpty());
    }

    @Test
    @DisplayName("空密码应不通过校验")
    void testEmptyPassword() {
        var result = validator.validate("");
        assertFalse(result.valid());
        assertNotNull(result.getFirstViolation());
    }

    @Test
    @DisplayName("null 密码应不通过校验")
    void testNullPassword() {
        var result = validator.validate(null);
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("短密码应不通过校验")
    void testShortPassword() {
        var result = validator.validate("Ab1@");
        assertFalse(result.valid());
        assertFalse(result.violations().isEmpty());
    }

    @Test
    @DisplayName("弱密码字典中的密码应不通过校验")
    void testWeakDictionaryPassword() {
        var result = validator.validate("Password123456");
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("包含连续序列的密码应不通过校验")
    void testSequencePassword() {
        var result = validator.validate("Abcdef1@3");
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("包含过多重复字符的密码应不通过校验")
    void testRepeatPassword() {
        var result = validator.validate("AAAAaaaa1@");
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("自定义配置应正确生效")
    void testCustomConfig() {
        var customValidator = PasswordStrengthValidator.builder()
                .minLength(12)
                .requireCharTypes(4)
                .build();
        var result = customValidator.validate("Short1@");
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("禁用弱密码字典检查后弱密码可通过")
    void testDisableWeakDictionary() {
        var customValidator = PasswordStrengthValidator.builder()
                .checkWeakDictionary(false)
                .build();
        var result = customValidator.validate("Password123456");
        assertTrue(result.valid());
    }
}
