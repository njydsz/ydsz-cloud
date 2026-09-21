package com.njydsz.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等豁免注解（已迁移）
 *
 * <p><b>已迁移至 {@code com.njydsz.common.safe.idempotent.annotation.IdempotentExempt}。</b>
 * 本类仅为向后兼容保留，请不要在新代码中使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@code com.njydsz.common.safe.idempotent.annotation.IdempotentExempt} 替代
 */
@Deprecated
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
