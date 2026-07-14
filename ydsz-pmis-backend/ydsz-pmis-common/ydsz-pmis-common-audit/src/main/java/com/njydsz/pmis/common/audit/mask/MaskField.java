package com.njydsz.pmis.common.audit.mask;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级脱敏注解
 * <p>
 * 标记在实体/DTO 字段上，审计切面在记录请求参数时会对该字段值进行脱敏处理，
 * 避免敏感数据写入审计日志。该注解对字段名（基于配置敏感词列表）方式形成补充，
 * 用于更精确的字段级控制。
 * </p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public class UserDTO {
 *     {@literal @}MaskField
 *     private String password;
 *
 *     {@literal @}MaskField(pattern = "card")
 *     private String creditCardNo;
 * }
 * }</pre>
 *
 * <p><b>脱敏策略：</b></p>
 * <ul>
 *   <li>字符串：保留前 2 位和后 2 位，中间替换为 {@code ****}</li>
 *   <li>集合/Map：递归处理每个元素</li>
 *   <li>其他类型：序列化为 {@code ***MASKED***}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 * @see SensitiveFieldMask
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface MaskField {

    /**
     * 匹配模式名称（可选），用于业务方扩展自定义脱敏规则。
     * <p>当前框架内置 {@code card}（银行卡）、{@code mobile}（手机号）、
     * {@code idcard}（身份证号）模式，业务方可注册自定义模式。
     *
     * @return 匹配模式名称，默认为空（使用默认字符串脱敏策略）
     */
    String pattern() default "";
}
