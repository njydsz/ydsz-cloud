package com.njydsz.pmis.common.lock.annotation;

import java.lang.annotation.*;

/**
 * 幂等豁免注解（兼容旧 com.njydsz.pmis.common.lock.annotation.IdempotentExempt）。
 *
 * <p>标注在方法参数或字段上，表示该参数/字段不参与幂等键的计算。
 * 用于排除分页参数、时间戳等不影响业务唯一性的字段。
 * <p>也可标注在方法上，表示该方法豁免幂等检查（如定时触发接口）。
 *
 * @since 1.0.0
 */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentExempt {

    /**
     * 豁免原因说明（方法级使用时填写）。
     *
     * @return 豁免原因
     */
    String value() default "";
}
