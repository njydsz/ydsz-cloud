package com.njydsz.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.safe.idempotent.annotation.IdempotentExempt;

/**
 * 幂等豁免注解（已迁移）
 *
 * <p><b>已迁移至 {@link com.njydsz.common.safe.idempotent.annotation.IdempotentExempt}。</b>
 * 本类仅为向后兼容保留，请不要在新代码中使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.annotation.IdempotentExempt} 替代
 */
@Deprecated
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SuppressWarnings("all")
public @interface IdempotentExempt {

  /**
   * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.annotation.IdempotentExempt#value()} 替代
   */
  @Deprecated
  String value() default "";
}
