package com.njydsz.common.safe.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sensitive operation secondary authentication annotation.
 *
 * <p>Marked on Controller methods to indicate that the operation requires secondary identity verification
 * (password confirmation) before execution. Typical scenarios: reset password, batch disable users, etc.
 *
 * <p>Secondary authentication flow:
 *
 * <ol>
 *   <li>Frontend first calls {@code /api/v1/user/sensitive-verify} endpoint with current admin password
 *   <li>Backend verifies password and writes a short-lived (5 min) verification flag in Redis
 *   <li>When frontend sends sensitive operation request, AOP aspect checks if Redis flag is valid
 *   <li>If not verified or expired, throws {@code SENSITIVE_VERIFY_REQUIRED} exception
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SensitiveOperation {

  /**
   * Sensitive operation description (for audit log).
   *
   * @return operation description text
   */
  String value() default "";
}
