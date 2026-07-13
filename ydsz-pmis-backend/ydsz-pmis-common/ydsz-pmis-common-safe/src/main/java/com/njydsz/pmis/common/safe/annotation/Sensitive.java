package com.njydsz.pmis.common.safe.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.pmis.common.safe.sensitive.SensitiveType;

/**
 * 敏感数据字段标记注解
 * <p>
 * 用于标记实体类、DTO 或 VO 中的敏感数据字段（如手机号、身份证、银行卡号、密码等），
 * 配合序列化器或拦截器实现自动脱敏。脱敏在序列化、API 响应、日志记录等多个环节自动生效。
 * </p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public class UserVO {
 *     @Sensitive(type = SensitiveType.PHONE)
 *     private String phone;
 *
 *     @Sensitive(type = SensitiveType.ID_CARD)
 *     private String idCard;
 *
 *     @Sensitive(type = SensitiveType.CUSTOM, prefixKeep = 3, suffixKeep = 4)
 *     private String bankCard;
 * }
 * }</pre>
 *
 * <p><b>配合使用：</b></p>
 * <ul>
 *   <li>JSON 序列化时通过 {@code SensitiveJsonSerializer} 自动脱敏</li>
 *   <li>日志输出时通过 AOP 切面自动脱敏</li>
 *   <li>审计记录时通过 {@code AuditAspect} 自动脱敏</li>
 * </ul>
 *
 * <p><b>安全约束：</b>仅在展示层和日志层脱敏，数据库中应保留原始数据，
 * 以便业务追溯、客服查询。脱敏不是加密，不应代替真正的安全存储。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Sensitive {

    /**
     * 脱敏类型
     *
     * @return 脱敏类型，默认为 {@link SensitiveType#DEFAULT}
     */
    SensitiveType type() default SensitiveType.DEFAULT;

    /**
     * 保留前缀字符数（仅 {@link SensitiveType#CUSTOM} 类型生效）
     *
     * @return 前缀保留字符数，默认 3
     */
    int prefixKeep() default 3;

    /**
     * 保留后缀字符数（仅 {@link SensitiveType#CUSTOM} 类型生效）
     *
     * @return 后缀保留字符数，默认 4
     */
    int suffixKeep() default 4;

    /**
     * 替换字符
     *
     * @return 替换中间部分的字符，默认 '*'
     */
    char replacement() default '*';
}