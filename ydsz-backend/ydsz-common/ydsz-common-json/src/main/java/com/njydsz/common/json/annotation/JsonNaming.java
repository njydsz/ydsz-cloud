package com.njydsz.common.json.annotation;

import java.lang.annotation.*;

import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * 类级命名策略注解（参考 Jackson 的 @JsonNaming）。
 *
 * <p>标注在类上，指定序列化/反序列化时使用的属性命名策略。
 * 优先级低于字段级 @JsonProperty，高于全局配置。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * {@literal @}JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
 * public class User {
 *     private String userName;  // 序列化为 "user_name"
 *     private String emailAddress;  // 序列化为 "email_address"
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.4.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonNaming {

    /**
     * 指定命名策略类。
     *
     * <p>该类必须是 {@code com.njydsz.common.json.naming.PropertyNamingStrategy} 的子类。</p>
     *
     * @return 命名策略类
     */
    Class<? extends PropertyNamingStrategy> value();
}
