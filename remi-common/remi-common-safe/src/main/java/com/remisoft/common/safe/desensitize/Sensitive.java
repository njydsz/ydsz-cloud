package com.remisoft.common.safe.desensitize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感数据标记注解（字段级脱敏）。
 *
 * <p>标记在 DTO/VO 字段上，指示该字段包含敏感信息，需要在 JSON 序列化、日志输出等
 * 场景中自动脱敏处理。配合 {@link SensitiveUtils} 或 Jackson 序列化器使用。</p>
 *
 * <p>与 {@link ColumnDesensitizationRule}（列级脱敏，数据库结果集脱敏）互补：
 * 本注解面向字段级（对象序列化），列级脱敏面向 SQL 查询结果。</p>
 *
 * <h3>支持的脱敏类型</h3>
 * <table>
 *   <tr><th>类型</th><th>示例（脱敏前）</th><th>示例（脱敏后）</th></tr>
 *   <tr><td>{@link SensitiveType#ID_CARD ID_CARD}</td><td>320102199001011234</td><td>320***********1234</td></tr>
 *   <tr><td>{@link SensitiveType#MOBILE MOBILE}</td><td>13812345678</td><td>138****5678</td></tr>
 *   <tr><td>{@link SensitiveType#EMAIL EMAIL}</td><td>zhangsan@example.com</td><td>z***n@example.com</td></tr>
 *   <tr><td>{@link SensitiveType#BANK_CARD BANK_CARD}</td><td>6222021234567890</td><td>6222****7890</td></tr>
 *   <tr><td>{@link SensitiveType#NAME NAME}</td><td>张三</td><td>张*</td></tr>
 *   <tr><td>{@link SensitiveType#ADDRESS ADDRESS}</td><td>北京市海淀区中关村大街1号</td><td>北京市海淀区****</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public class UserVO {
 *     @Sensitive(SensitiveType.MOBILE)
 *     private String phone;
 *
 *     @Sensitive(SensitiveType.ID_CARD)
 *     private String idCard;
 *
 *     @Sensitive  // 默认：全部替换为 ****
 *     private String secretKey;
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.5.0
 * @see SensitiveType
 * @see SensitiveUtils
 * @see ColumnDesensitizationRule
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sensitive {

    /**
     * 脱敏类型。
     *
     * @return 脱敏类型，默认 {@link SensitiveType#MASK_ALL}
     */
    SensitiveType value() default SensitiveType.MASK_ALL;

    /**
     * 头部保留字符数（针对 CUSTOM 类型生效）。
     *
     * @return 头部保留字符数，默认 0
     */
    int prefixKeep() default 0;

    /**
     * 尾部保留字符数（针对 CUSTOM 类型生效）。
     *
     * @return 尾部保留字符数，默认 0
     */
    int suffixKeep() default 0;
}
