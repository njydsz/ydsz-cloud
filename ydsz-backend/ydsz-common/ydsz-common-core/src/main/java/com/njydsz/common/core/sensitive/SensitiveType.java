package com.njydsz.common.core.sensitive;

/**
 * 敏感数据类型枚举
 *
 * <p>定义常见的敏感数据类别，配合 {@link Sensitive} 注解标注字段类型，
 * 由 {@link SensitiveDataMasker} 执行对应的脱敏策略。</p>
 *
 * <p><b>脱敏策略说明：</b>
 * <ul>
 *   <li>{@link #MOBILE} 手机号：保留前 3 后 4，如 {@code 138****5678}</li>
 *   <li>{@link #ID_CARD} 身份证：保留前 4 后 4，如 {@code 3201**********1234}</li>
 *   <li>{@link #BANK_CARD} 银行卡：保留前 4 后 4，如 {@code 6222 **** **** 1234}</li>
 *   <li>{@link #EMAIL} 邮箱：保留首字符与 @ 后域名，如 {@code z***@example.com}</li>
 *   <li>{@link #NAME} 姓名：保留姓氏，如 {@code 张*}</li>
 *   <li>{@link #ADDRESS} 地址：保留省市区，如 {@code 江苏省南京市********}</li>
 *   <li>{@link #PASSWORD} 密码：全部替换为 {@code ******}</li>
 *   <li>{@link #CUSTOM} 自定义：由 {@link Sensitive#masker()} 指定的实现处理</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see Sensitive
 * @see SensitiveDataMasker
 */
public enum SensitiveType {

    /** 手机号 */
    MOBILE,
    /** 身份证号 */
    ID_CARD,
    /** 银行卡号 */
    BANK_CARD,
    /** 邮箱地址 */
    EMAIL,
    /** 姓名 */
    NAME,
    /** 地址 */
    ADDRESS,
    /** 密码 */
    PASSWORD,
    /** 自定义脱敏（需通过 {@link Sensitive#masker()} 指定实现） */
    CUSTOM
}
