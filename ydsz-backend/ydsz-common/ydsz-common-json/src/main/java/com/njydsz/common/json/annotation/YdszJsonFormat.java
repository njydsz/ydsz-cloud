package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段格式注解
 *
 * <p>用于控制字段序列化/反序列化的格式，对标 FastJSON @JSONField(format)。</p>
 *
 * <p><b>使用场景：</b></p>
 * <ul>
 *   <li>日期格式化：format = "yyyy-MM-dd HH:mm:ss"</li>
 *   <li>数字格式化：format = "0.00" 或 "#,###.##"</li>
 *   <li>布尔值格式化：format = "yes/no" 或 "on/off"</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public class User {
 *     &#064;YdszJsonFormat("yyyy-MM-dd")
 *     private LocalDate birthday;
 *
 *     &#064;YdszJsonFormat("0.00")
 *     private double score;
 *
 *     &#064;YdszJsonFormat("yes/no")
 *     private boolean active;
 * }
 * </pre>
 *
 * @deprecated 使用 {@link JsonFormat} 替代。功能完全重复，统一使用 Jackson 兼容注解名。
 * @author ydsz-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Deprecated
public @interface YdszJsonFormat {

    /**
     * 格式字符串
     *
     * <p>支持的格式：</p>
     * <ul>
     *   <li>日期格式：yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, ISO-8601 等</li>
     *   <li>数字格式：0.00, #,###.##, 0.000 等</li>
     *   <li>布尔格式：yes/no, on/off, true/false 等</li>
     * </ul>
     */
    String value() default "";

    /**
     * 时区（仅适用于日期时间）
     *
     * <p>默认使用系统时区，可指定如 "Asia/Shanghai"</p>
     */
    String timezone() default "";
}
