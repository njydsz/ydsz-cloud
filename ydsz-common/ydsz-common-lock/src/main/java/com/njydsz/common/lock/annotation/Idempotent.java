package com.njydsz.common.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等性注解（已迁移）
 *
 * <p><b>已迁移至 {@code com.njydsz.common.safe.idempotent.annotation.Idempotent}。</b>
 * 本类仅为向后兼容保留，请不要在新代码中使用。
 *
 * <p>迁移说明：幂等能力已整合至安全模块（ydsz-common-safe），以获得更好的安全防护集成。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@code com.njydsz.common.safe.idempotent.annotation.Idempotent} 替代
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

  /**
   * 幂等键，支持 SpEL 表达式。
   *
   * <p>为空时自动根据 类名 + 方法名 + 参数摘要 生成。
   *
   * @return 幂等键
   */
  String key() default "";

  /**
   * 幂等锁过期时间（秒），超时后自动释放。
   *
   * <p>默认 5 秒，覆盖大部分重复点击场景。
   *
   * @return TTL 秒数
   */
  int ttlSeconds() default 5;

  /**
   * 重复提交时的提示信息。
   *
   * @return 提示信息
   */
  String message() default "请勿重复提交";

  /**
   * 幂等校验生效条件（SpEL 表达式）。
   *
   * <p>返回 {@code true} 时执行幂等校验，返回 {@code false} 时跳过幂等检查直接执行业务方法。
   *
   * <p>SpEL 变量：方法参数名（如 {@code #req} 引用入参）。
   *
   * <p>默认空串（恒生效，不做条件判断）。
   *
   * @return SpEL 条件表达式，空串表示无条件生效
   */
  String condition() default "";
}
