package com.njydsz.pmis.common.lock.annotation;

import java.lang.annotation.*;

/**
 * 幂等豁免注解（兼容旧 com.njydsz.pmis.common.annotation.IdempotentExempt）。
 *
 * <p>标注在方法参数或字段上，表示该参数/字段不参与幂等键的计算。
 * 用于排除分页参数、时间戳等不影响业务唯一性的字段。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentExempt {
}
