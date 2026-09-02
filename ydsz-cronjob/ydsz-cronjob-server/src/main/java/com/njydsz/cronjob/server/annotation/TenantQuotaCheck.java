package com.njydsz.cronjob.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 租户配额检查注解（P0-5）。
 *
 * <p>标注在方法上，AOP 切面会在方法执行前自动进行租户配额校验。 支持任务数配额、并发配额、日执行量配额的检查。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @TenantQuotaCheck(type = QuotaType.JOB)
 * public String create(Job job) {
 *     // 创建任务前会自动检查任务数配额
 * }
 *
 * @TenantQuotaCheck(type = QuotaType.CONCURRENT)
 * public void execute(Job job) {
 *     // 执行前会自动检查并发配额
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantQuotaCheck {

  /**
   * 配额检查类型。
   *
   * @return 配额检查类型（JOB / CONCURRENT / DAILY_EXECUTION）
   */
  QuotaType type();

  /**
   * 租户 ID 参数名（从方法参数中提取）。
   *
   * <p>默认从 {@link com.njydsz.common.security.TenantContext} 获取， 如果方法参数中有 tenantId，可指定参数名以优先使用。
   *
   * @return 租户 ID 参数名；默认空字符串表示自动从上下文获取
   */
  String tenantIdParam() default "";

  /** 配额检查类型枚举。 */
  enum QuotaType {
    /** 任务数配额（创建任务时检查） */
    JOB,

    /** 并发配额（执行任务时检查） */
    CONCURRENT,

    /** 日执行量配额（派发任务时检查） */
    DAILY_EXECUTION
  }
}
