package com.njydsz.common.json.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 多态子类型注解
 *
 * <p>定义单个子类型的映射关系，与 {@link JsonSubTypes} 配合使用。</p>
 *
 * <p><b>参数说明：</b></p>
 * <ul>
 *   <li>value: 具体的子类</li>
 *   <li>name: JSON 中 type 属性的值，用于识别该子类型</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
@Deprecated(since = "1.0.0", forRemoval = true)
public @interface JsonSubType {

    /**
     * 子类型类
     */
    Class<?> value();

    /**
     * 类型标识名称
     *
     * <p>JSON 中 type 属性的值，用于反序列化时识别具体子类</p>
     */
    String name();
}
