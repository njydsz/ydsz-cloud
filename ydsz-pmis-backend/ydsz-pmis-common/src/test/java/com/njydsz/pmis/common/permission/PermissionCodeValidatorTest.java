package com.njydsz.pmis.common.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PermissionCodeValidator 单元测试
 */
@DisplayName("PermissionCodeValidator 权限码校验测试")
class PermissionCodeValidatorTest {

    @Test
    @DisplayName("三段式小写合法权限码应通过")
    void validCode() {
        assertThat(PermissionCodeValidator.isValid("auth:user:create")).isTrue();
        assertThat(PermissionCodeValidator.isValid("scheduler:job:trigger")).isTrue();
        assertThat(PermissionCodeValidator.isValid("file:storage:upload")).isTrue();
        assertThat(PermissionCodeValidator.isValid("auth:user:reset-password")).isTrue();
    }

    @Test
    @DisplayName("两段式(legacy)应被拒绝")
    void legacyCode_rejected() {
        assertThat(PermissionCodeValidator.isValid("job:add")).isFalse();
        assertThat(PermissionCodeValidator.isValid("file:upload")).isFalse();
        assertThat(PermissionCodeValidator.isValid("notif:send")).isFalse();
    }

    @Test
    @DisplayName("非法 action 词应被拒绝")
    void invalidAction() {
        assertThat(PermissionCodeValidator.isValid("auth:user:execute")).isFalse();
        assertThat(PermissionCodeValidator.isValid("auth:user:run")).isFalse();
    }

    @Test
    @DisplayName("大写字母应被拒绝")
    void uppercase_rejected() {
        assertThat(PermissionCodeValidator.isValid("AUTH:USER:CREATE")).isFalse();
        assertThat(PermissionCodeValidator.isValid("auth:user:Create")).isFalse();
    }

    @Test
    @DisplayName("空值应被拒绝")
    void nullAndEmpty() {
        assertThat(PermissionCodeValidator.isValid(null)).isFalse();
        assertThat(PermissionCodeValidator.isValid("")).isFalse();
    }

    @Test
    @DisplayName("validate 返回错误信息")
    void validate_errorMessage() {
        String msg = PermissionCodeValidator.validate("job:add");
        assertThat(msg).contains("格式不合法");
        String msg2 = PermissionCodeValidator.validate("auth:user:execute");
        assertThat(msg2).contains("action");
    }

    @Test
    @DisplayName("validate 合法时返回 null")
    void validate_ok() {
        assertThat(PermissionCodeValidator.validate("auth:user:create")).isNull();
    }

    @Test
    @DisplayName("所有 PermissionCodes 常量都应合法")
    void allConstantsValid() throws IllegalAccessException {
        java.lang.reflect.Field[] fields = PermissionCodes.class.getFields();
        int checked = 0;
        for (java.lang.reflect.Field f : fields) {
            // 跳过 LEGACY_*
            if (f.getName().startsWith("LEGACY_")) continue;
            Object v = f.get(null);
            if (v instanceof String s) {
                assertThat(PermissionCodeValidator.isValid(s))
                        .as("常量 %s = %s 应为合法权限码", f.getName(), s)
                        .isTrue();
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(20);
    }
}
