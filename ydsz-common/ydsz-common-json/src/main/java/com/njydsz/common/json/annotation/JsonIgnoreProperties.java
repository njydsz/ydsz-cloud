package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 忽略指定属性（类级别注解，参考 Jackson 的 @JsonIgnoreProperties）。
 *
 * <p>标注在类上，指定序列化/反序列化时要忽略的属性名列表。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>
 * {@literal @}JsonIgnoreProperties({"password", "salt"})
 * public class User {
 *     private String name;
 *     private String password;
 *     private String salt;
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
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
