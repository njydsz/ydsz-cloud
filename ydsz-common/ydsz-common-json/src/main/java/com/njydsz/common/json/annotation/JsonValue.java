package com.njydsz.common.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注枚举值的序列化方式（参考 Jackson 的 @JsonValue）。
 *
 * <p>标注在枚举类的方法上，指定序列化时使用该方法的返回值作为 JSON 值。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public enum Status {
 *     ACTIVE(1),
 *     INACTIVE(0);
 *
 *     private final int code;
 *
 *     Status(int code) { this.code = code; }
 *
 *     {@literal @}JsonValue
 *     public int getCode() { return code; }
 * }
 * </pre>
 *
 * <p>序列化结果为数字 1/0，而非字符串 "ACTIVE"/"INACTIVE"。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface JsonValue {
}
