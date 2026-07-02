package com.njydsz.pmis.common.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import java.time.LocalDateTime;

/**
 * 密码策略校验器
 *
 * <p>默认策略（等保 2.0 三级要求）：
 * <ul>
 *   <li>长度 8-32 位</li>
 *   <li>至少包含 1 个大写字母</li>
 *   <li>至少包含 1 个小写字母</li>
 *   <li>至少包含 1 个数字</li>
 *   <li>至少包含 1 个特殊字符（!@#$%^&*()_+-=）</li>
 *   <li>不能与用户名相同（不区分大小写）</li>
 *   <li>不能是常见弱密码</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class PasswordPolicy {

    /** 密码最小长度 */
    private static final int MIN_LENGTH = 8;
    /** 密码最大长度 */
    private static final int MAX_LENGTH = 32;

    /** 大写字母正则 */
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    /** 小写字母正则 */
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    /** 数字正则 */
    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** 特殊字符正则 */
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=]");

    /** 常见弱密码列表 */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "12345678", "123456789", "1234567890", "password", "qwerty",
            "abc123", "11111111", "00000000", "iloveyou", "admin123",
            "pmis1234", "Pmis@123"
    );

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度
     *
     * @param password 密码
     * @param username 用户名
     * @return 校验结果
     */
    public static PasswordCheckResult check(String password, String username) {
        List<String> failures = new ArrayList<>();
        if (password == null) {
            failures.add("密码不能为空");
            return new PasswordCheckResult(false, failures);
        }
        if (password.length() < MIN_LENGTH) {
            failures.add("密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            failures.add("密码长度不能超过 " + MAX_LENGTH + " 位");
        }
        if (!UPPER.matcher(password).find()) {
            failures.add("密码必须包含大写字母");
        }
        if (!LOWER.matcher(password).find()) {
            failures.add("密码必须包含小写字母");
        }
        if (!DIGIT.matcher(password).find()) {
            failures.add("密码必须包含数字");
        }
        if (!SPECIAL.matcher(password).find()) {
            failures.add("密码必须包含特殊字符 (!@#$%^&*()_+-=)");
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            failures.add("密码不能与用户名相同");
        }
        if (WEAK_PASSWORDS.contains(password)) {
            failures.add("密码过于简单，请使用强密码");
        }
        return new PasswordCheckResult(failures.isEmpty(), failures);
    }

    /**
     * 计算密码强度等级 0-4（0=弱 / 1=差 / 2=中 / 3=良 / 4=强）
     *
     * @param password 密码
     * @return 强度等级
     */
    public static int strength(String password) {
        if (password == null || password.isEmpty()) return 0;
        int score = 0;
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (UPPER.matcher(password).find() && LOWER.matcher(password).find()) score++;
        if (DIGIT.matcher(password).find()) score++;
        if (SPECIAL.matcher(password).find()) score++;
        return Math.min(score, 4);
    }

    /**
     * 是否需要强制修改（maxDays 天未改）
     *
     * @param lastChange 上次修改时间，为 null 时返回 true
     * @param maxDays    最大有效天数
     * @return true 表示密码已过期
     */
    public static boolean isExpired(LocalDateTime lastChange, int maxDays) {
        if (lastChange == null) return true;
        return lastChange.plusDays(maxDays).isBefore(LocalDateTime.now());
    }

    /**
     * 密码校验结果
     *
     * @param pass     是否通过
     * @param failures 失败原因列表
     */
    public record PasswordCheckResult(boolean pass, List<String> failures) {
        /**
         * 获取第一条失败原因
         *
         * @return 失败原因；无失败时返回空字符串
         */
        public String firstError() {
            return failures.isEmpty() ? "" : failures.get(0);
        }
    }
}
