package com.njydsz.common.safe.password;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 密码强度校验器
 *
 * <p>提供全面的密码强度校验能力，覆盖 OWASP 密码安全最佳实践：
 * <ul>
 *   <li>长度要求（最小 8 位，推荐 12 位以上）</li>
 *   <li>字符种类要求（大写字母、小写字母、数字、特殊字符至少 3 种）</li>
 *   <li>常见弱密码字典检测（如 123456、password、qwerty）</li>
 *   <li>连续/重复字符检测（如 abcdef、aaaaaa）</li>
 *   <li>键盘序列检测（如 qwerty、asdfgh）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * PasswordStrengthValidator validator = PasswordStrengthValidator.builder()
 *     .minLength(10)
 *     .requireCharTypes(3)
 *     .checkWeakDictionary(true)
 *     .checkSequence(true)
 *     .build();
 *
 * PasswordStrengthResult result = validator.validate("MyP@ssw0rd");
 * if (!result.isValid()) {
 *     throw new BusinessException(result.getFirstViolation());
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class PasswordStrengthValidator {

    private static final Pattern UPPER_CASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER_CASE = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR = Pattern.compile(".*[!@#$%^&*()\\-_=+\\[\\]{}|;:'\",.<>/?`~].*");

    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "123456", "123456789", "password", "qwerty", "abc123",
            "12345678", "111111", "1234567", "1234567890", "admin",
            "letmein", "welcome", "monkey", "dragon", "master",
            "password1", "123123", "iloveyou", "sunshine", "princess",
            "000000", "123abc", "654321", "superman", "1qaz2wsx",
            "qwertyuiop", "zxcvbnm", "asdfghjkl", "1q2w3e4r",
            "passw0rd", "p@ssword", "p@ssw0rd", "root", "toor",
            "administrator", "guest", "test", "temp", "default"
    );

    private static final String[] KEYBOARD_SEQUENCES = {
            "qwertyuiop", "asdfghjkl", "zxcvbnm",
            "1234567890", "0987654321",
            "abcdefghijklmnopqrstuvwxyz",
            "qwertyuiopasdfghjklzxcvbnm"
    };

    private final int minLength;
    private final int requireCharTypes;
    private final boolean checkWeakDictionary;
    private final boolean checkSequence;
    private final boolean checkRepeat;

    private PasswordStrengthValidator(Builder builder) {
        this.minLength = builder.minLength;
        this.requireCharTypes = builder.requireCharTypes;
        this.checkWeakDictionary = builder.checkWeakDictionary;
        this.checkSequence = builder.checkSequence;
        this.checkRepeat = builder.checkRepeat;
    }

    /**
     * 校验密码强度
     *
     * @param password 待校验的密码
     * @return 校验结果
     */
    public PasswordStrengthResult validate(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrengthResult.invalid("密码不能为空");
        }

        Set<String> violations = new HashSet<>();

        if (password.length() < minLength) {
            violations.add("密码长度不足，至少需要 " + minLength + " 位");
        }

        int charTypeCount = 0;
        if (UPPER_CASE.matcher(password).matches()) {
            charTypeCount++;
        } else {
            violations.add("密码需要包含大写字母");
        }
        if (LOWER_CASE.matcher(password).matches()) {
            charTypeCount++;
        } else {
            violations.add("密码需要包含小写字母");
        }
        if (DIGIT.matcher(password).matches()) {
            charTypeCount++;
        } else {
            violations.add("密码需要包含数字");
        }
        if (SPECIAL_CHAR.matcher(password).matches()) {
            charTypeCount++;
        }

        if (charTypeCount < requireCharTypes) {
            violations.add("密码字符种类不足，至少需要 " + requireCharTypes + " 种（大写字母、小写字母、数字、特殊字符）");
        }

        if (checkWeakDictionary) {
            String lowerPassword = password.toLowerCase();
            if (WEAK_PASSWORDS.stream().anyMatch(lowerPassword::contains)) {
                violations.add("密码包含常见弱密码片段，请避免使用");
            }
        }

        if (checkSequence && containsSequence(password.toLowerCase())) {
            violations.add("密码包含连续字符序列（如 abcdef、123456、qwerty），请避免使用");
        }

        if (checkRepeat && hasExcessiveRepeat(password)) {
            violations.add("密码包含过多重复字符，请避免使用");
        }

        int score = calculateScore(password, charTypeCount, violations.size());
        boolean valid = violations.isEmpty() && score >= 60;

        return new PasswordStrengthResult(valid, score, violations);
    }

    /**
     * 检测密码是否包含键盘序列或字母数字序列
     */
    private boolean containsSequence(String password) {
        for (String seq : KEYBOARD_SEQUENCES) {
            for (int i = 0; i <= seq.length() - 4; i++) {
                String sub = seq.substring(i, i + 4);
                if (password.contains(sub)) {
                    return true;
                }
            }
            String reversed = new StringBuilder(seq).reverse().toString();
            for (int i = 0; i <= reversed.length() - 4; i++) {
                String sub = reversed.substring(i, i + 4);
                if (password.contains(sub)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检测密码是否包含过多重复字符（连续 4 个或以上相同字符）
     */
    private boolean hasExcessiveRepeat(String password) {
        int maxRepeat = 1;
        int currentRepeat = 1;
        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                currentRepeat++;
                maxRepeat = Math.max(maxRepeat, currentRepeat);
            } else {
                currentRepeat = 1;
            }
        }
        return maxRepeat >= 4;
    }

    /**
     * 计算密码强度分数（0-100）
     */
    private int calculateScore(String password, int charTypeCount, int violationCount) {
        int score = 0;

        score += Math.min(password.length() * 4, 40);

        score += charTypeCount * 10;

        int uniqueChars = (int) password.chars().distinct().count();
        score += Math.min(uniqueChars * 2, 20);

        score -= violationCount * 10;

        return Math.max(0, Math.min(100, score));
    }

    /**
     * 创建默认配置的校验器
     *
     * @return 默认校验器实例
     */
    public static PasswordStrengthValidator createDefault() {
        return new Builder().build();
    }

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 密码强度校验结果
     *
     * @param valid      是否通过校验
     * @param score      强度分数（0-100）
     * @param violations 违规原因集合
     */
    public record PasswordStrengthResult(boolean valid, int score, Set<String> violations) {

        /**
         * 创建校验失败结果
         *
         * @param reason 失败原因
         * @return 校验失败结果
         */
        public static PasswordStrengthResult invalid(String reason) {
            Set<String> violations = new HashSet<>();
            violations.add(reason);
            return new PasswordStrengthResult(false, 0, violations);
        }

        /**
         * 获取第一个违规原因
         *
         * @return 第一个违规原因，无违规返回 null
         */
        public String getFirstViolation() {
            return violations.stream().findFirst().orElse(null);
        }
    }

    /**
     * 密码强度校验器构建器
     */
    public static class Builder {

        private int minLength = 8;
        private int requireCharTypes = 3;
        private boolean checkWeakDictionary = true;
        private boolean checkSequence = true;
        private boolean checkRepeat = true;

        /**
         * 设置最小密码长度
         *
         * @param minLength 最小长度
         * @return 构建器
         */
        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        /**
         * 设置要求的字符种类数（1-4）
         *
         * @param requireCharTypes 字符种类数
         * @return 构建器
         */
        public Builder requireCharTypes(int requireCharTypes) {
            this.requireCharTypes = Math.max(1, Math.min(4, requireCharTypes));
            return this;
        }

        /**
         * 设置是否检查弱密码字典
         *
         * @param checkWeakDictionary 是否检查
         * @return 构建器
         */
        public Builder checkWeakDictionary(boolean checkWeakDictionary) {
            this.checkWeakDictionary = checkWeakDictionary;
            return this;
        }

        /**
         * 设置是否检查连续序列
         *
         * @param checkSequence 是否检查
         * @return 构建器
         */
        public Builder checkSequence(boolean checkSequence) {
            this.checkSequence = checkSequence;
            return this;
        }

        /**
         * 设置是否检查重复字符
         *
         * @param checkRepeat 是否检查
         * @return 构建器
         */
        public Builder checkRepeat(boolean checkRepeat) {
            this.checkRepeat = checkRepeat;
            return this;
        }

        /**
         * 构建校验器
         *
         * @return 校验器实例
         */
        public PasswordStrengthValidator build() {
            return new PasswordStrengthValidator(this);
        }
    }
}
