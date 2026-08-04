package com.njydsz.common.core.annotation;

/**
 * 敏感数据脱敏工具类。
 *
 * <p>提供各种类型的脱敏策略实现，纯 JDK 实现，无任何外部依赖。
 * 既可用于 Jackson 序列化拦截，也可在日志输出、审计记录等场景中直接调用。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 直接调用
 * String masked = SensitiveUtils.mask("13812345678", SensitiveType.MOBILE);
 * // → "138****5678"
 *
 * // 自定义规则
 * String custom = SensitiveUtils.mask("4008820888", 2, 4);
 * // → "40****0888"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.5.0
 * @see Sensitive
 * @see SensitiveType
 */
public final class SensitiveUtils {

    private static final char MASK_CHAR = '*';
    private static final int MIN_MASK_LENGTH = 4;

    private SensitiveUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按指定脱敏类型对文本进行脱敏。
     *
     * @param value 原始文本（可为 null 或空字符串）
     * @param type  脱敏类型
     * @return 脱敏后的文本；输入为空时返回 null
     */
    public static String mask(String value, SensitiveType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (type) {
            case ID_CARD, MOBILE, BANK_CARD -> maskFixed(value, type.getPrefixKeep(), type.getSuffixKeep());
            case EMAIL -> maskEmail(value);
            case NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case MASK_ALL -> maskAll(value);
            case CUSTOM -> value; // CUSTOM 由调用方通过 mask(value, prefixKeep, suffixKeep) 调用
        };
    }

    /**
     * 按自定义规则脱敏。
     *
     * @param value      原始文本（可为 null）
     * @param prefixKeep 头部保留字符数
     * @param suffixKeep 尾部保留字符数
     * @return 脱敏后的文本
     */
    public static String mask(String value, int prefixKeep, int suffixKeep) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int keep = prefixKeep + suffixKeep;
        if (value.length() <= keep) {
            return value;
        }
        return maskFixed(value, prefixKeep, suffixKeep);
    }

    /**
     * 固定前后保留位数脱敏。
     *
     * @param value      原始文本
     * @param prefixKeep 头部保留字符数
     * @param suffixKeep 尾部保留字符数
     * @return 脱敏后的文本
     */
    private static String maskFixed(String value, int prefixKeep, int suffixKeep) {
        int length = value.length();
        int maskLength = length - prefixKeep - suffixKeep;
        if (maskLength <= 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(value, 0, prefixKeep);
        sb.append(String.valueOf(MASK_CHAR).repeat(Math.max(maskLength, MIN_MASK_LENGTH)));
        sb.append(value, length - suffixKeep, length);
        return sb.toString();
    }

    /**
     * 邮箱脱敏：保留首字母和末字母（@前）+ 完整域名。
     */
    private static String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            // 用户名太短，直接掩码
            return MASK_CHAR + email.substring(atIndex);
        }
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String masked = username.charAt(0) + "***" + username.charAt(username.length() - 1);
        return masked + domain;
    }

    /**
     * 姓名脱敏：保留首字，其余替换。
     */
    private static String maskName(String name) {
        if (name.length() <= 1) {
            return name;
        }
        return name.charAt(0) + String.valueOf(MASK_CHAR).repeat(name.length() - 1);
    }

    /**
     * 地址脱敏：保留前 6 个字符。
     */
    private static String maskAddress(String address) {
        if (address.length() <= 6) {
            return address;
        }
        return address.substring(0, 6) + "****";
    }

    /**
     * 全掩码：仅保留首字符。
     */
    private static String maskAll(String value) {
        if (value.length() <= 1) {
            return "*";
        }
        return value.charAt(0) + String.valueOf(MASK_CHAR).repeat(value.length() - 1);
    }
}
