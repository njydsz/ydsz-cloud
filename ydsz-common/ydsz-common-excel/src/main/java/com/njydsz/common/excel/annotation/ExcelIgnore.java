package com.njydsz.common.excel.annotation;

/**
 * ExcelIgnore 类
 *
 * @author ydsz-team

 * @version 26.09.01
 */
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excel忽略注解
 *
 * <p>用于标注类或字段,在Excel映射时忽略这些元素。 当标注在类上时,该类所有字段都会被忽略;
 *
 * <p>当标注在字段上时,该字段不会参与Excel的读写操作。
 *
 * <h3>示例</h3>
 *
 * <pre>{@code
 * // 标注在类上,整个类被忽略
 * @ExcelIgnore
 * public class IgnoredClass {
 *     private String field1;  // 会被忽略
 * }
 *
 * public class User {
 *     private String name;
 *
 *     // 标注在字段上,单个字段被忽略
 *     @ExcelIgnore
 *     private String password;
 *
 *     private String email;
 * }
 * }</pre>
 *
 * @see ExcelProperty
 * @author ydsz-team
 * @since 26.09.01
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
public @interface ExcelIgnore {

  /**
   * 是否忽略
   *
   * <p>默认为true,设置为false时可取消忽略(仅对类型级别注解有效)
   *
   * @return true表示忽略
   */
  boolean value() default true;
}
