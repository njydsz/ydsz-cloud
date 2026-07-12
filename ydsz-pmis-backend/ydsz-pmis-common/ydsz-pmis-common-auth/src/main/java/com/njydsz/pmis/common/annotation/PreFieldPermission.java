package com.njydsz.pmis.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级权限控制注解
 *
 * <p>标注在 DTO/VO 字段上，控制字段的可见性。
 * 当用户缺少指定权限时，字段值会被脱敏或置空。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Data
 * public class UserVO {
 *     private String username;
 *
 *     @PreFieldPermission(value = "system:user:view-sensitive", strategy = FieldStrategy.MASK)
 *     private String idCard;
 *
 *     @PreFieldPermission(value = "system:user:view-sensitive", strategy = FieldStrategy.HIDE)
 *     private String phone;
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PreFieldPermission {

    /**
     * 所需权限码
     *
     * @return 权限码
     */
    String value();

    /**
     * 无权限时的处理策略
     *
     * @return 策略
     */
    FieldStrategy strategy() default FieldStrategy.HIDE;

    /**
     * 字段级权限策略
     */
    enum FieldStrategy {
        /** 隐藏字段（置 null） */
        HIDE,
        /** 脱敏（保留部分字符） */
        MASK,
        /** 替换为固定文本 */
        REPLACE
    }
}
