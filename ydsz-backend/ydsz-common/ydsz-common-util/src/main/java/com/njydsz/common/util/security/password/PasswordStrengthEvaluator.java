package com.njydsz.common.util.security.password;

/**
 * 密码强度评估器
 *
 * <p>遵循 NIST SP 800-63B 与大厂安全规范：
 * <ul>
 *   <li>最小长度 ≥ 8</li>
 *   <li>建议长度 ≥ 12</li>
 *   <li>字符集复杂度：大小写字母 + 数字 + 特殊字符</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class PasswordStrengthEvaluator {

    public static final int MIN_LENGTH = 8;
    public static final int RECOMMENDED_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private PasswordStrengthEvaluator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public enum Level {
        WEAK, MEDIUM, STRONG, VERY_STRONG
    }

    /**
     * 评估密码强度
     *
     * <p>根据密码长度和字符集复杂度（小写字母、大写字母、数字、特殊字符）进行评估。
     *
     * @param password 待评估的密码
     * @return 密码强度等级（WEAK / MEDIUM / STRONG / VERY_STRONG）
     */
    public static Level evaluate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return Level.WEAK;
        }
        if (password.length() > MAX_LENGTH) {
            return Level.WEAK;
        }
        int classes = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) classes++;
        if (password.chars().anyMatch(Character::isUpperCase)) classes++;
        if (password.chars().anyMatch(Character::isDigit)) classes++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;

        if (password.length() >= 16 && classes == 4) {
            return Level.VERY_STRONG;
        }
        if (password.length() >= 12 && classes >= 3) {
            return Level.STRONG;
        }
        if (classes >= 2) {
            return Level.MEDIUM;
        }
        return Level.WEAK;
    }

    /**
     * 判断密码是否符合安全合规要求（强度为 STRONG 或 VERY_STRONG）
     *
     * @param password 待检查的密码
     * @return true 表示符合合规要求
     */
    public static boolean isCompliant(String password) {
        Level level = evaluate(password);
        return level == Level.STRONG || level == Level.VERY_STRONG;
    }
}
