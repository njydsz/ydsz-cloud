package com.njydsz.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.safe.idempotent.annotation.Idempotent;

/**
 * 接口幂等性注解（已迁移）
 *
 * <p><b>已迁移至 {@link com.njydsz.common.safe.idempotent.annotation.Idempotent}。</b>
 * 本类仅为向后兼容保留，请不要在新代码中使用。
 *
 * <p>迁移说明：幂等能力已整合至安全模块（ydsz-common-safe），以获得更好的安全防护集成。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.annotation.Idempotent} 替代
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SuppressWarnings("all")
public @interface Idempotent {

  /**
   * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.annotation.Idempotent#key()} 替代
   */
  @Deprecated
  String key() default "";

  /**
   * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.annotation.Idempotent#ttlSeconds()} 替代
   */
  @Deprecated
  int ttlSeconds() default 5;

  /**
   * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.annotation.Idempotent#message()} 替代
   */
  @Deprecated
  String message() default "请勿重复提交";

  /**
   * @deprecated 使用 {@link com.njydsz.common.safe.idempotent.annotation.Idempotent#condition()} 替代
   */
  @Deprecated
  String condition() default "";
}
