package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 自定义反序列化器（参考 Jackson 的 @JsonDeserialize）。
 *
 * <p>标注在类或字段上，指定反序列化时使用的自定义反序列化器类。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonDeserialize(using = MyDateDeserializer.class)
 * public class MyDate {
 *     // ...
 * }
 *
 * public class MyDateDeserializer implements com.njydsz.common.json.api.JsonDeserializer&lt;MyDate&gt; {
 *     {@literal @}Override
 *     public MyDate deserialize(String json, Class&lt;MyDate&gt; type) {
 *         // 自定义反序列化逻辑
 *     }
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface JsonDeserialize {

    /**
     * 指定自定义反序列化器类。
     *
     * <p>该类必须实现 {@code com.njydsz.common.json.api.JsonDeserializer} 接口。</p>
     *
     * @return 反序列化器类
     */
    Class<?> using() default Void.class;
}
