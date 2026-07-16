package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 忽略指定属性（类级别注解，参考 Jackson 的 @JsonIgnoreProperties）。
 *
 * <p>标注在类上，指定序列化/反序列化时要忽略的属性名列表。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonIgnoreProperties({"password", "salt"})
 * public class User {
 *     private String name;
 *     private String password;
 *     private String salt;
 * }
 * </pre>
 *
 * @since 1.4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonIgnoreProperties {

    /**
     * 要忽略的属性名列表。
     *
     * @return 属性名数组
     */
    String[] value();

    /**
     * 是否忽略未知属性（反序列化时遇到未知的属性不报错）。
     *
     * @return 是否忽略未知属性
     */
    boolean ignoreUnknown() default false;
}
