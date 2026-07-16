package com.njydsz.pmis.common.safe.sensitive;

/**
 * 敏感数据类型枚举
 * <p>
 * 定义常见的敏感数据类型及其脱敏策略。覆盖中国个人信息保护法（PIPL）和欧盟 GDPR
 * 要求保护的关键个人信息（PII）。
 * </p>
 *
 * <p><b>脱敏示例：</b></p>
 * <pre>{@code
 * CHINESE_NAME: "张三" → "张*"
 * ID_CARD:      "110101199001011234" → "110101********1234"
 * PHONE:        "13800138000" → "138****8000"
 * EMAIL:        "test@example.com" → "t**t@example.com"
 * BANK_CARD:    "6222021234567890123" → "622202********0123"
 * ADDRESS:      "北京市朝阳区建国路88号" → "北京市朝阳区****"
 * PASSWORD:     "abc123" → "******"（不返回前端）
 * }</pre>
 *
 * @since 1.0.0
 */
public enum SensitiveType {

    /** 默认脱敏：保留前 2 后 2 位，中间用 **** 替换 */
    DEFAULT,

    /** 中文姓名：保留姓氏，隐藏名字（2 字"张*"，3 字"张*三"） */
    CHINESE_NAME,

    /** 身份证号：显示前 3 位和后 4 位，中间隐藏（110101********1234） */
    ID_CARD,

    /** 手机号：显示前 3 位和后 4 位，中间隐藏（138****8000） */
    PHONE,

    /** 电子邮箱：保留首尾字符，中间隐藏（t**t@example.com） */
    EMAIL,

    /** 银行卡号：显示前 6 位和后 4 位，中间隐藏（622202********0123） */
    BANK_CARD,

    /** 家庭住址：保留省市区，隐藏详细地址（北京市朝阳区****） */
    ADDRESS,

    /** 密码：不返回或返回固定占位符（******） */
    PASSWORD,

    /** 固定电话：显示区号和后 4 位，中间隐藏（010-****1234） */
    FIXED_PHONE,

    /** 信用卡安全码：不返回（***） */
    CVV,

    /** 军官证：显示前几位和后几位 */
    MILITARY_ID,

    /** 护照：显示前几位和后几位 */
    PASSPORT,

    /** 企业工商注册号：显示前几位和后几位 */
    BUSINESS_LICENSE,

    /** 车牌号：保留首字符和最后一位（京A***5） */
    CAR_LICENSE,

    /** 社保卡号：显示前几位和后几位 */
    SOCIAL_SECURITY,

    /** 出生日期：只显示年月（1990-**-**） */
    BIRTH_DATE,

    /** 姓名：仅保留首字（2 字"张*"，3 字"张**"，4 字及以上"张**三"） */
    NAME,

    /** 自定义规则：由使用者自定义脱敏策略，配合 {@code @Sensitive(prefixKeep, suffixKeep, replacement)} 使用 */
    CUSTOM
}
