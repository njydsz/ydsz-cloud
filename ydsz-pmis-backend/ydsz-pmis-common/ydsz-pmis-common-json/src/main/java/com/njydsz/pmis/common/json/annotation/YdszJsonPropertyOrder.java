package com.njydsz.pmis.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JSON 属性排序注解
 *
 * <p>用于控制序列化时字段的输出顺序，对标 Jackson @JsonPropertyOrder。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 按指定顺序输出
 * &#064;YdszJsonPropertyOrder({"id", "name", "email"})
 * public class User {
 *     private String name;
 *     private Long id;
 *     private String email;
 * }
 * // 输出：{"id":1,"name":"John","email":"john@example.com"}
 *
 * // 字母排序
 * &#064;YdszJsonPropertyOrder(alphabetic = true)
 * public class Product {
 *     private String name;
 *     private Double price;
 * }
 * // 输出：{"name":"iPhone","price":999.0}
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface YdszJsonPropertyOrder {

    /**
     * 属性名称数组，按指定顺序输出
     */
    String[] value() default {};

    /**
     * 是否按字母顺序排序
     *
     * <p>如果 value 为空且 alphabetic=true，则按字母顺序排序输出。</p>
     */
    boolean alphabetic() default false;
}
