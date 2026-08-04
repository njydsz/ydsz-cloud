package com.remisoft.common.safe.desensitize;

/**
 * 列脱敏规则枚举。
 *
 * <p>定义内置脱敏规则及其正则表达式与替换模板，供
 * {@link ColumnDesensitizationExecutor} 使用。
 *
 * <p><b>规则示例：</b>
 * <ul>
 *   <li>{@link #PHONE}：{@code 13812345678} → {@code 138****5678}</li>
 *   <li>{@link #EMAIL}：{@code user@example.com} → {@code u***@example.com}</li>
 *   <li>{@link #ID_CARD}：{@code 110101199001011234} → {@code 110***********1234}</li>
 *   <li>{@link #BANK_CARD}：{@code 6222021234567890123} → {@code 622202***********0123}</li>
 *   <li>{@link #CUSTOM}：使用调用方提供的正则与替换模板</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see ColumnDesensitizationExecutor
 */
public enum ColumnDesensitizationRule {

    /**
     * 手机号脱敏：保留前 3 位和后 4 位，中间用 **** 替换。
     *
     * <p>示例：{@code 13812345678} → {@code 138****5678}
     */
    PHONE("(\\d{3})\\d+(\\d{4})", "$1****$2"),

    /**
     * 邮箱脱敏：保留首字符和 @ 域名部分，中间用 *** 替换。
     *
     * <p>示例：{@code user@example.com} → {@code u***@example.com}
     */
    EMAIL("(\\w)\\w+(@\\w+\\.\\w+)", "$1***$2"),

    /**
     * 身份证号脱敏：保留前 3 位和后 4 位，中间用 * 替换。
     *
     * <p>示例：{@code 110101199001011234} → {@code 110***********1234}
     */
    ID_CARD("(\\d{3})\\d+(\\d{4})", "$1***********$2"),

    /**
     * 银行卡号脱敏：保留前 6 位和后 4 位，中间用 * 替换。
     *
     * <p>示例：{@code 6222021234567890123} → {@code 622202*********0123}
     */
    BANK_CARD("(\\d{6})\\d+(\\d{4})", "$1*********$2"),

    /**
     * 自定义脱敏规则：正则和替换模板由调用方提供。
     */
    CUSTOM("", "");

    /**
     * 正则表达式（CUSTOM 规则为空字符串，使用调用方提供的正则）
     */
    private final String pattern;

    /**
     * 替换模板（CUSTOM 规则为空字符串，使用调用方提供的模板）
     */
    private final String replacement;

    ColumnDesensitizationRule(String pattern, String replacement) {
        this.pattern = pattern;
        this.replacement = replacement;
    }

    /**
     * 获取正则表达式。
     *
     * @return 正则表达式，CUSTOM 规则返回空字符串
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * 获取替换模板。
     *
     * @return 替换模板，CUSTOM 规则返回空字符串
     */
    public String getReplacement() {
        return replacement;
    }

    /**
     * 根据规则编码（枚举名）解析对应的脱敏规则。
     *
     * <p>编码大小写不敏感，未匹配时返回 {@code null}。
     *
     * @param code 规则编码（如 "PHONE" / "phone"）
     * @return 对应的脱敏规则，未匹配时返回 null
     */
    public static ColumnDesensitizationRule codeOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return ColumnDesensitizationRule.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
