package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 控制属性包含策略（参考 Jackson 的 @JsonInclude）。
 *
 * <p>标注在类或字段上，控制序列化时何时包含属性值。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonInclude(JsonInclude.Include.NON_NULL)
 * public class User {
 *     private String name;
 *     private String email;  // null 时不输出
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JsonInclude {

    /**
     * 包含策略。
     */
    enum Include {
        /** 始终包含 */
        ALWAYS,
        /** 非 null 时包含 */
        NON_NULL,
        /** 非空时包含（空字符串、空集合、空 Map 等不输出） */
        NON_EMPTY,
        /** 非默认值时包含（基本类型默认值不输出） */
        NON_DEFAULT,
        /** 仅在反序列化时使用，序列化时不输出 */
        USE_DEFAULTS
    }

    /**
     * 包含策略值。
     *
     * @return 包含策略
     */
    Include value() default Include.ALWAYS;
}
