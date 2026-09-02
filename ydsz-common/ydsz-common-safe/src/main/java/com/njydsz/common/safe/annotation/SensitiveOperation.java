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
 * @since 26.09.01
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

  /**
   * Sensitive operation level (P1-8).
   *
   * <p>用于差异化控制二次认证的强度与时效：{@link SensitiveLevel#CRITICAL} 使用更短
   * 的验证标记时效并强制审计，{@link SensitiveLevel#HIGH} / {@link SensitiveLevel#MEDIUM}
   * 使用常规时效。默认 {@link SensitiveLevel#HIGH}，向后兼容。
   *
   * @return sensitive operation level
   */
  SensitiveLevel level() default SensitiveLevel.HIGH;
}
