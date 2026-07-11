package com.njydsz.pmis.common.sensitive;

/**
 * 脱敏策略
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum SensitiveStrategy {

    /** 不处理 */
    NONE,

    /** 姓名：张*、李*明（保留首末字） */
    NAME,

    /** 身份证：保留前 6 后 4 */
    ID_CARD,

    /** 手机号：138****8000 */
    PHONE,

    /** 邮箱：a***@example.com */
    EMAIL,

    /** 银行卡：保留前 4 后 4 */
    BANK_CARD,

    /** 地址：保留前 6 字 + *** */
    ADDRESS,

    /** 自定义：使用 SensitiveUtil.register 注册的处理函数 */
    CUSTOM;

    /**
     * 从字符串解析脱敏策略，未知值默认为 NONE
     *
     * @param s 字符串值
     * @return 解析得到的脱敏策略
     */
    public static SensitiveStrategy parse(String s) {
        if (s == null || s.isEmpty()) return NONE;
        try {
            return SensitiveStrategy.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return NONE;
        }
    }
}
