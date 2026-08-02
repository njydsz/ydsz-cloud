package com.njydsz.common.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 枚举反序列化默认值（对标 Jackson {@code @JsonEnumDefaultValue}）。
 *
 * <p>标记在枚举字段上，当反序列化遇到未识别的枚举值时，使用此字段的值作为默认值，
 * 而非抛出异常。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * enum Status {
 *     ACTIVE, INACTIVE,
 *     &#64;JsonEnumDefaultValue
 *     UNKNOWN
 * }
 *
 * // "unknown_value" 将反序列化为 Status.UNKNOWN
 * </pre>
 *
 * @author ydsz-team
 * @since 1.1.0
 * @see com.fasterxml.jackson.annotation.JsonEnumDefaultValue
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonEnumDefaultValue {
}
