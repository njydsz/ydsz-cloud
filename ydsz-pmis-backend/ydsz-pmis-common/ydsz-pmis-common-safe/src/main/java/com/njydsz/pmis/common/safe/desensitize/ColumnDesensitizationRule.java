package com.njydsz.pmis.common.safe.desensitize;


/**
 * 字段脱敏规则枚举。
 *
 * <p>定义常见的字段脱敏规则，支持灵活组合使用。
 *
 * <p><b>支持规则：</b>
 * <ul>
 *   <li>PHONE：手机号脱敏，如 13812345678 → 138****5678</li>
 *   <li>ID_CARD：身份证号脱敏，如 320123199001011234 → 3201**********1234</li>
 *   <li>EMAIL：邮箱脱敏，如 john@example.com → j***@example.com</li>
 *   <li>BANK_CARD：银行卡脱敏，如 6222021234567890123 → 6222***********0123</li>
 *   <li>NAME：姓名脱敏，如 张三 → 张*，李明 → 李*</li>
 *   <li>ADDRESS：地址脱敏，如 北京市朝阳区某某街道... → 北京市***</li>
 *   <li>AMOUNT：金额脱敏，如 10000.50 → 10000.****</li>
 *   <li>PASSWORD：密码脱敏，完全隐藏，如 anypassword → ********</li>
 *   <li>CUSTOM：自定义规则，通过 pattern 和 replacement 指定</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // Redis role-col-key 中的配置
 * {
 *   "visibleColumns": {
 *     "sys_user": [
 *       {"column": "phone", "rule": "PHONE"},
 *       {"column": "id_card", "rule": "ID_CARD"},
 *       {"column": "email", "rule": "EMAIL"}
 *     ]
 *   }
 * }
 * }</pre>
 *
 * @since 1.0.0
 * 
 * @see ColumnDesensitizationExecutor
 */
public enum ColumnDesensitizationRule {

    /**
     * 手机号脱敏：保留前3位和后4位，中间用 **** 替换
     * <p>示例：13812345678 → 138****5678
     */
    PHONE("(\\d{3})\\d{4}(\\d{4})", "$1****$2", "手机号"),

    /**
     * 身份证号脱敏：保留前4位和后4位，中间用 ********** 替换
     * <p>示例：320123199001011234 → 3201**********1234
     */
    ID_CARD("(\\d{4})\\d{10}(\\w{4})", "$1**********$2", "身份证号"),

    /**
     * 邮箱脱敏：保留首字符和@后域名，隐藏中间部分
     * <p>示例：john@example.com → j***@example.com
     */
    EMAIL("(\\w)\\w*(@\\w+\\.\\w+)", "$1***$2", "邮箱"),

    /**
     * 银行卡脱敏：保留前4位和后4位，中间用 ********** 替换
     * <p>示例：6222021234567890123 → 6222***********0123
     */
    BANK_CARD("(\\d{4})\\d*(\\d{4})", "$1***********$2", "银行卡号"),

    /**
     * 姓名脱敏：只保留姓，多字姓名保留前两字
     * <p>示例：张三 → 张*，欧阳娜娜 → 欧阳*
     */
    NAME("([^\\s]{1,2})[^\\s]+", "$1*", "姓名"),

    /**
     * 地址脱敏：只保留省市或市区
     * <p>示例：北京市朝阳区某某街道123号 → 北京市
     */
    ADDRESS("^(.+?[市区])(?:.+)$", "$1", "地址"),

    /**
     * 金额脱敏：保留整数部分，小数部分用 **** 替换
     * <p>示例：10000.50 → 10000.****
     */
    AMOUNT("(\\d+\\.)\\d+", "$1****", "金额"),

    /**
     * 密码：完全隐藏
     * <p>示例：anypassword → ********
     */
    PASSWORD("\\S+", "********", "密码"),

    /**
     * 自定义规则：需要配合 pattern 和 replacement 使用
     */
    CUSTOM(null, null, "自定义");

    private final String pattern;
    private final String replacement;
    private final String description;

    ColumnDesensitizationRule(String pattern, String replacement, String description) {
        this.pattern = pattern;
        this.replacement = replacement;
        this.description = description;
    }

    public String getPattern() {
        return pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public static ColumnDesensitizationRule codeOf(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
