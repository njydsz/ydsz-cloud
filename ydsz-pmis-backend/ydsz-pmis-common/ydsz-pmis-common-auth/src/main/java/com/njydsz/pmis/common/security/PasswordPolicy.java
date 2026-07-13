package com.njydsz.pmis.common.security;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码策略校验器
 *
 * <p>默认策略（等保 2.0 三级要求 + 互联网大厂增强）：
 * <ul>
 *   <li>长度 8-32 位</li>
 *   <li>至少包含 1 个大写字母</li>
 *   <li>至少包含 1 个小写字母</li>
 *   <li>至少包含 1 个数字</li>
 *   <li>至少包含 1 个特殊字符（!@#$%^&*()_+-=）</li>
 *   <li>不能与用户名相同（不区分大小写）</li>
 *   <li>不能包含用户名（不区分大小写）</li>
 *   <li>不能是常见弱密码</li>
 *   <li>不能包含连续 3 位以上递增/递减字符（如 abc / cba / 123 / 321）</li>
 *   <li>不能包含连续 3 位以上重复字符（如 aaa / 111）</li>
 *   <li>不能与最近使用的历史密码相同（需调用 {@link #check(String, String, Collection)} 传入历史密码）</li>
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
    /** 历史密码检查数量（最近 N 次不能重复） */
    private static final int HISTORY_CHECK_COUNT = 5;

    /** 大写字母正则 */
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    /** 小写字母正则 */
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    /** 数字正则 */
    private static final Pattern DIGIT = Pattern.compile("\\d");
    /** 特殊字符正则 */
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=]");

    /** 常见弱密码列表（扩展版：覆盖 OWASP Top 弱密码 + 中文常用弱密码） */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "12345678", "123456789", "1234567890", "password", "qwerty",
            "abc123", "11111111", "00000000", "iloveyou", "admin123",
            "pmis1234", "Pmis@123", "password1", "Password1", "Admin@123",
            "admin@123", "root1234", "Root@123", "test1234", "Test@123",
            "welcome1", "Welcome1", "monkey123", "dragon123", "master123",
            "Qwerty123", "Abc@1234", "abc@1234", "P@ssw0rd", "P@ssword1",
            "pass@123", "Pass@123", "1qaz@WSX", "qaz@123", "Aa123456",
            "aA123456", "Admin123!", "admin123!", "123qwe!@#", "QWEasd123"
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
        return check(password, username, null);
    }

    /**
     * 校验密码强度（含历史密码检查）
     *
     * @param password        密码
     * @param username        用户名
     * @param passwordHistory 历史密码哈希列表（最近 N 次的密码哈希），可为 null
     * @return 校验结果
     */
    public static PasswordCheckResult check(String password, String username, Collection<String> passwordHistory) {
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
        if (username != null && !username.isBlank()) {
            if (password.equalsIgnoreCase(username)) {
                failures.add("密码不能与用户名相同");
            }
            // 密码不能包含用户名（不区分大小写）
            if (password.length() > username.length()
                    && password.toLowerCase().contains(username.toLowerCase())) {
                failures.add("密码不能包含用户名");
            }
        }
        if (WEAK_PASSWORDS.contains(password)) {
            failures.add("密码过于简单，请使用强密码");
        }
        // 检查连续递增/递减字符（如 abc / 321 / cba）
        if (hasSequentialChars(password, 3)) {
            failures.add("密码不能包含连续 3 位以上递增或递减字符（如 abc / 123 / cba）");
        }
        // 检查连续重复字符（如 aaa / 111）
        if (hasRepeatingChars(password, 3)) {
            failures.add("密码不能包含连续 3 位以上重复字符（如 aaa / 111）");
        }
        // 检查历史密码
        if (passwordHistory != null && !passwordHistory.isEmpty()) {
            for (String historicHash : passwordHistory) {
                if (historicHash != null && matchesHistory(password, historicHash)) {
                    failures.add("不能使用最近 " + HISTORY_CHECK_COUNT + " 次使用过的密码");
                    break;
                }
            }
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
     * 计算密码过期预警天数
     *
     * @param lastChange 上次修改时间
     * @param maxDays    最大有效天数
     * @return 剩余有效天数；已过期返回 0；lastChange 为 null 返回 0
     */
    public static long daysUntilExpiry(LocalDateTime lastChange, int maxDays) {
        if (lastChange == null) return 0;
        LocalDateTime expiry = lastChange.plusDays(maxDays);
        long remaining = Duration.between(LocalDateTime.now(), expiry).toDays();
        return Math.max(0, remaining);
    }

    /**
     * 检查密码是否包含连续递增或递减字符
     *
     * @param password 密码
     * @param minLen   最小连续长度
     * @return true 表示包含连续递增或递减字符
     */
    private static boolean hasSequentialChars(String password, int minLen) {
        if (password == null || password.length() < minLen) return false;
        String lower = password.toLowerCase();
        int ascending = 1;
        int descending = 1;
        for (int i = 1; i < lower.length(); i++) {
            char prev = lower.charAt(i - 1);
            char curr = lower.charAt(i);
            if (curr == prev + 1) {
                ascending++;
                descending = 1;
                if (ascending >= minLen) return true;
            } else if (curr == prev - 1) {
                descending++;
                ascending = 1;
                if (descending >= minLen) return true;
            } else {
                ascending = 1;
                descending = 1;
            }
        }
        return false;
    }

    /**
     * 检查密码是否包含连续重复字符
     *
     * @param password 密码
     * @param minLen   最小重复长度
     * @return true 表示包含连续重复字符
     */
    private static boolean hasRepeatingChars(String password, int minLen) {
        if (password == null || password.length() < minLen) return false;
        int repeat = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                repeat++;
                if (repeat >= minLen) return true;
            } else {
                repeat = 1;
            }
        }
        return false;
    }

    /**
     * 检查密码是否与历史密码哈希匹配
     *
     * <p>使用 BCrypt 校验，如果哈希格式非 BCrypt 则直接比较字符串。
     *
     * @param password     明文密码
     * @param historicHash 历史密码哈希
     * @return true 表示匹配
     */
    private static boolean matchesHistory(String password, String historicHash) {
        if (historicHash == null || historicHash.isBlank()) return false;
        // 如果是 BCrypt 格式，使用 BCrypt 校验
        if (isBCryptFormat(historicHash)) {
            try {
                BCryptPasswordEncoder encoder =
                        new BCryptPasswordEncoder();
                return encoder.matches(password, historicHash);
            } catch (Exception e) {
                return false;
            }
        }
        // 非 BCrypt 格式：直接字符串比较（兼容历史 MD5 哈希）
        return password.equals(historicHash);
    }

    /**
     * 判断哈希是否为 BCrypt 格式
     *
     * @param hash 密码哈希
     * @return true 表示是 BCrypt 格式
     */
    private static boolean isBCryptFormat(String hash) {
        return hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
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
