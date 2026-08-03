package com.njydsz.common.core.sensitive;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段标注注解。
 *
 * <p>标注在需要脱敏的字段上，指定敏感数据类型。
 * 序列化链路可通过反射读取该注解，自动对字段值执行脱敏。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * public class UserVO {
 *     @Sensitive(type = SensitiveType.MOBILE)
 *     private String mobile;
 *
 *     @Sensitive(type = SensitiveType.ID_CARD)
 *     private String idCard;
 * }
 * }</pre>
 *
 * <p><b>自定义脱敏：</b>设置 {@link #type()} 为 {@link SensitiveType#CUSTOM}，
 * 并通过 {@link #masker()} 指定自定义脱敏器实现。</p>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see SensitiveType
 * @see SensitiveDataMasker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sensitive {

    /**
     * 敏感数据类型，默认手机号。
     *
     * @return 敏感数据类型
     */
    SensitiveType type() default SensitiveType.MOBILE;

    /**
     * 自定义脱敏器（仅当 {@link #type()} 为 {@link SensitiveType#CUSTOM} 时生效）。
     *
     * <p>实现 {@link SensitiveDataMasker.SensitiveMasker} 接口，
     * 必须提供公开无参构造函数。</p>
     *
     * @return 自定义脱敏器实现类
     */
    Class<? extends SensitiveDataMasker.SensitiveMasker> masker() default SensitiveDataMasker.DefaultMasker.class;
}
