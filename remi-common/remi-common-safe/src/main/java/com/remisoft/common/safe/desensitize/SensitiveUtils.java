package com.remisoft.common.safe.desensitize;

/**
 * 敏感数据脱敏工具类（字段级）。
 *
 * <p>提供各种类型的脱敏策略实现。手机号/身份证/邮箱脱敏规则与
 * 原 {@code StringUtils}（remi-common-util）保持一致，本类提供更高层次的
 * {@link SensitiveType} 枚举 API 以及姓名/地址/全掩码等场景。</p>
 *
 * <p>与 {@link ColumnDesensitizationRule}（列级正则脱敏）互补：
 * 本工具类面向字段级（编程式），列级规则面向数据库结果集。</p>
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
 * @author remi-team
 * @since 1.5.0
 * @see Sensitive
 * @see SensitiveType
 */
public final class SensitiveUtils {

    private static final char MASK_CHAR = '*';

    /** 邮箱格式简单校验正则（脱敏用） */
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private SensitiveUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 按指定脱敏类型对文本进行脱敏。
     *
     * <p>MOBILE / ID_CARD / EMAIL 由本类实现（规则与原 StringUtils 统一），
     * BANK_CARD / NAME / ADDRESS / MASK_ALL 由本类实现。
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
            case MOBILE -> maskMobile(value);
            case ID_CARD -> maskIdCard(value);
            case EMAIL -> maskEmail(value);
            case BANK_CARD -> maskFixed(value, type.getPrefixKeep(), type.getSuffixKeep());
            case NAME -> maskName(value);
            case ADDRESS -> maskAddress(value);
            case MASK_ALL -> maskAll(value);
            case CUSTOM -> value;
        };
    }

    /**
     * 手机号脱敏（保留前 3 位和后 4 位）。
     *
     * @param mobile 手机号
     * @return 脱敏后的手机号；非 11 位或 null 原样返回
     */
    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() != 11) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    /**
     * 身份证号脱敏（保留前 6 位和后 4 位）。
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号；不足 18 位或 null 原样返回
     */
    private static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 18) {
            return idCard;
        }
        return idCard.substring(0, 6) + "*".repeat(8) + idCard.substring(14);
    }

    /**
     * 邮箱脱敏（保留前 2 位和域名）。
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱；格式非法或 null 原样返回
     */
    private static String maskEmail(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            return email;
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) {
            return email;
        }
        return email.substring(0, 2) + "*".repeat(atIndex - 2) + email.substring(atIndex);
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
     */
    private static String maskFixed(String value, int prefixKeep, int suffixKeep) {
        int length = value.length();
        int maskLength = length - prefixKeep - suffixKeep;
        if (maskLength <= 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(value, 0, prefixKeep);
        sb.append(String.valueOf(MASK_CHAR).repeat(Math.max(maskLength, 1)));
        sb.append(value, length - suffixKeep, length);
        return sb.toString();
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
