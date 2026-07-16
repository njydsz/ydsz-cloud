package com.njydsz.userinfo.server.security;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * 密码强度策略（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-common-security 包，因 common 重构后该策略类已迁移到各业务模块本地化。
 * 提供：
 * <ul>
 *   <li>{@link #check(String, String)} — 注册 / 改密场景的强度校验，返回多错误列表</li>
 *   <li>{@link #isExpired(LocalDateTime, int)} — 密码过期判定</li>
 * </ul>
 *
 * <p>校验规则（基线策略，可按需调整）：
 * <ol>
 *   <li>长度 ≥ 8</li>
 *   <li>同时包含字母与数字</li>
 *   <li>不能与 username 相同（大小写不敏感）</li>
 *   <li>不能为常见弱密码（123456 / qwerty / password 等）</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class PasswordPolicy {

    /** 密码最小长度 */
    public static final int MIN_LENGTH = 8;
    /** 常见弱密码黑名单（演示用，生产应接入 NIST/HaveIBeenPwned API） */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "12345678", "123456789", "1234567890", "qwerty", "qwerty123",
            "password", "password1", "abc12345", "iloveyou", "admin123",
            "11111111", "00000000", "66666666", "88888888", "99999999"
    );

    private PasswordPolicy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 校验密码强度
     *
     * @param rawPassword 明文密码
     * @param username    当前用户名（用于排除 "用户名=密码" 的弱口令）
     * @return 校验结果
     */
    public static PasswordCheckResult check(String rawPassword, String username) {
        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasText(rawPassword)) {
            errors.add("密码不能为空");
            return PasswordCheckResult.fail(errors);
        }

        if (rawPassword.length() < MIN_LENGTH) {
            errors.add("密码长度至少 " + MIN_LENGTH + " 位");
        }
        if (!containsLetter(rawPassword) || !containsDigit(rawPassword)) {
            errors.add("密码必须同时包含字母与数字");
        }
        if (StringUtils.hasText(username) && rawPassword.equalsIgnoreCase(username)) {
            errors.add("密码不能与用户名相同");
        }
        if (WEAK_PASSWORDS.contains(rawPassword.toLowerCase())) {
            errors.add("密码过于简单，请更换");
        }

        return errors.isEmpty() ? PasswordCheckResult.pass() : PasswordCheckResult.fail(errors);
    }

    /**
     * 判断密码是否过期
     *
     * @param lastChangedAt 上次修改时间
     * @param expireDays    有效天数
     * @return true 表示已过期（lastChangedAt 为 null 时视为首次登录不强制改密）
     */
    public static boolean isExpired(LocalDateTime lastChangedAt, int expireDays) {
        if (lastChangedAt == null) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(lastChangedAt, LocalDateTime.now());
        return days >= expireDays;
    }

    private static boolean containsLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 密码校验结果
     */
    public static class PasswordCheckResult {

        private final boolean pass;
        private final List<String> errors;

        private PasswordCheckResult(boolean pass, List<String> errors) {
            this.pass = pass;
            this.errors = errors;
        }

        public static PasswordCheckResult pass() {
            return new PasswordCheckResult(true, List.of());
        }

        public static PasswordCheckResult fail(List<String> errors) {
            return new PasswordCheckResult(false, List.copyOf(errors));
        }

        /**
         * 是否通过（boolean 属性形式，兼容 {@code !r.isPass()} 调用）
         */
        public boolean isPass() {
            return pass;
        }

        public List<String> errors() {
            return errors;
        }

        public String firstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }
    }
}
