package com.njydsz.pmis.common.sensitive;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级脱敏注解
 *
 * <p>在 VO/DTO 字段上标记后，Jackson 序列化时自动脱敏。
 *
 * <pre>
 *   {@code @Sensitive(SensitiveStrategy.PHONE)}
 *   private String phone;
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {

    /**
     * 脱敏策略
     */
    SensitiveStrategy value() default SensitiveStrategy.NONE;

    /**
     * 自定义前置保留长度（用于 ADDRESS/NAME）
     */
    int prefixKeep() default 1;

    /**
     * 自定义后置保留长度
     */
    int suffixKeep() default 1;
}
