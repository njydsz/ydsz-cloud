package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

/**
 * 嵌套属性展开（参考 Jackson 的 @JsonUnwrapped）。
 *
 * <p>标注在嵌套对象字段上，序列化时将其属性展开到父对象中。</p>
 *
 * <p><b>注意：此注解已创建但尚未实现序列化/反序列化逻辑。标记为 Roadmap 待实现。</b></p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * public class Order {
 *     private String orderId;
 *
 *     {@literal @}JsonUnwrapped
 *     private Address shippingAddress;  // 展开为 street/city/zipCode
 * }
 *
 * public class Address {
 *     private String street;
 *     private String city;
 *     private String zipCode;
 * }
 * </pre>
 *
 * <p>序列化结果：{"orderId":"123","street":"...","city":"...","zipCode":"..."}</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface JsonUnwrapped {

    /**
     * 展开属性的前缀（所有展开的属性名前添加此前缀）。
     *
     * @return 前缀字符串
     */
    String prefix() default "";

    /**
     * 展开属性的后缀（所有展开的属性名后添加此后缀）。
     *
     * @return 后缀字符串
     */
    String suffix() default "";
}
