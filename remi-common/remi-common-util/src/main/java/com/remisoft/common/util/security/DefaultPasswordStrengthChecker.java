package com.remisoft.common.util.security;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * 默认密码强度校验器实现。
 *
 * <p>提供基于长度、字符多样性、常见模式的密码强度评分逻辑，
 * 并支持中英文描述消息国际化。作为 SPI 的默认实现，可被业务方自定义实现覆盖。
 *
 * <p><b>评分规则（总分 10 分）：</b>
 * <ul>
 *   <li>长度 ≥ 8: +1，≥ 12: +1，≥ 16: +2</li>
 *   <li>包含小写字母: +1</li>
 *   <li>包含大写字母: +1</li>
 *   <li>包含数字: +1</li>
 *   <li>包含特殊字符: +1</li>
 *   <li>包含连续字符（如 abc、123）: -1</li>
 *   <li>包含重复字符（如 aaa、111）: -1</li>
 * </ul>
 *
 * <p>等级映射：
 * <ul>
 *   <li>0-2: VERY_WEAK</li>
 *   <li>3-4: WEAK</li>
 *   <li>5-6: MEDIUM</li>
 *   <li>7-8: STRONG</li>
 *   <li>9-10: VERY_STRONG</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.3.0
 * @see PasswordStrengthChecker
 */
public class DefaultPasswordStrengthChecker implements PasswordStrengthChecker {

    /**
     * 默认单例（无状态、线程安全，可复用）。
     *
     * <p>通过 {@code INSTANCE} 避免重复创建；由于 Score 规则是纯函数（无共享可变字段），
     * 所有调用方可安全共享同一实例。
     */
    public static final DefaultPasswordStrengthChecker INSTANCE = new DefaultPasswordStrengthChecker();

    /** Bundle 基础名，用于国际化消息查找 */
    private static final String BUNDLE_BASE = "com.remisoft.common.util.security.messages";

    /** 已知常见弱密码集合（前 100 常见密码子集）。 */
    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
            "123456", "password", "12345678", "qwerty", "123456789",
            "letmein", "1234567", "football", "iloveyou", "admin",
            "welcome", "monkey", "login", "abc123", "111111",
            "123123", "password123", "1234", "baseball", "qwerty123"
    );

    @Override
    public PasswordStrengthLevel check(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrengthLevel.VERY_WEAK;
        }

        int score = calculateScore(password);

        if (score <= 2) {
            return PasswordStrengthLevel.VERY_WEAK;
        } else if (score <= 4) {
            return PasswordStrengthLevel.WEAK;
        } else if (score <= 6) {
            return PasswordStrengthLevel.MEDIUM;
        } else if (score <= 8) {
            return PasswordStrengthLevel.STRONG;
        } else {
            return PasswordStrengthLevel.VERY_STRONG;
        }
    }

    /**
     * 计算密码强度评分。
     *
     * @param password 明文密码
     * @return 评分（可能为负数表示极弱）
     */
    private int calculateScore(String password) {
        int score = 0;
        int length = password.length();

        // 长度得分
        if (length >= 8) score++;
        if (length >= 12) score++;
        if (length >= 16) score += 2;

        // 字符多样性
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (hasLower) score++;
        if (hasUpper) score++;
        if (hasDigit) score++;
        if (hasSpecial) score++;

        // 常见弱密码惩罚
        if (COMMON_WEAK_PASSWORDS.contains(password.toLowerCase())) {
            score -= 5;
        }

        // 连续字符惩罚（如 abc、123）
        if (hasConsecutiveChars(password)) {
            score--;
        }

        // 重复字符惩罚（如 aaa、111）
        if (hasRepeatedChars(password)) {
            score--;
        }

        return Math.max(score, 0);
    }

    /**
     * 检测连续字符（长度 >= 3，如 abc、123、xyz）。
     */
    private boolean hasConsecutiveChars(String password) {
        if (password == null || password.length() < 3) {
            return false;
        }
        for (int i = 0; i <= password.length() - 3; i++) {
            char c1 = password.charAt(i);
            char c2 = password.charAt(i + 1);
            char c3 = password.charAt(i + 2);
            if (c2 == c1 + 1 && c3 == c2 + 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测重复字符（长度 >= 3，如 aaa、111）。
     */
    private boolean hasRepeatedChars(String password) {
        if (password == null || password.length() < 3) {
            return false;
        }
        for (int i = 0; i <= password.length() - 3; i++) {
            char c1 = password.charAt(i);
            if (c1 == password.charAt(i + 1) && c1 == password.charAt(i + 2)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe(PasswordStrengthLevel level, Locale locale) {
        if (level == null) {
            return "";
        }
        Locale targetLocale = locale != null ? locale : Locale.getDefault();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, targetLocale);
            return bundle.getString("password.strength." + level.name().toLowerCase());
        } catch (Exception e) {
            // 回退默认描述
            return defaultDescribe(level, targetLocale);
        }
    }

    @Override
    public String suggest(String password, Locale locale) {
        if (password == null) {
            return getMessage("password.suggest.null", locale);
        }
        StringBuilder suggestion = new StringBuilder();
        if (password.length() < 8) {
            suggestion.append(getMessage("password.suggest.length", locale)).append(" ");
        }
        boolean hasLower = false, hasUpper = false, hasDigit = false, hasSpecial = false;
        for (char c : password.toCharArray()) {
            if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        if (!hasLower || !hasUpper) {
            suggestion.append(getMessage("password.suggest.case", locale)).append(" ");
        }
        if (!hasDigit) {
            suggestion.append(getMessage("password.suggest.digit", locale)).append(" ");
        }
        if (!hasSpecial) {
            suggestion.append(getMessage("password.suggest.special", locale)).append(" ");
        }
        if (COMMON_WEAK_PASSWORDS.contains(password.toLowerCase())) {
            suggestion.append(getMessage("password.suggest.common", locale)).append(" ");
        }
        return suggestion.toString().trim();
    }

    /**
     * 默认描述（ResourceBundle 缺失时的回退）。
     */
    private String defaultDescribe(PasswordStrengthLevel level, Locale locale) {
        boolean isChinese = locale != null && Locale.CHINESE.getLanguage().equals(locale.getLanguage());
        switch (level) {
            case VERY_WEAK: return isChinese ? "极弱" : "Very Weak";
            case WEAK: return isChinese ? "弱" : "Weak";
            case MEDIUM: return isChinese ? "中等" : "Medium";
            case STRONG: return isChinese ? "强" : "Strong";
            case VERY_STRONG: return isChinese ? "极强" : "Very Strong";
            default: return "";
        }
    }

    /**
     * 获取国际化消息，找不到时返回键名。
     */
    private String getMessage(String key, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE, locale != null ? locale : Locale.getDefault());
            return bundle.getString(key);
        } catch (Exception e) {
            // 默认英文
            switch (key) {
                case "password.suggest.length": return "Password should be at least 8 characters";
                case "password.suggest.case": return "Add both uppercase and lowercase letters";
                case "password.suggest.digit": return "Include at least one digit";
                case "password.suggest.special": return "Include at least one special character";
                case "password.suggest.common": return "Avoid common passwords";
                default: return key;
            }
        }
    }
}
