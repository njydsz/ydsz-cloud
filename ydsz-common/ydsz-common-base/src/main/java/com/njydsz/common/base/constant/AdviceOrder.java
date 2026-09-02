package com.njydsz.common.base.constant;

/**
 * ControllerAdvice 执行顺序常量。
 *
 * <p>所有数字必须与 {@code docs/BASE_INTERCEPTOR_ORDER.md} 保持一致。 修改任何数字前请先更新文档。
 *
 * <p>ControllerAdvice 使用自然数体系（0, 10, 20...）， 数值越小优先级越高（最先执行）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class AdviceOrder {

  private AdviceOrder() {
    throw new UnsupportedOperationException("Constants class");
  }

  /** GlobalResponseAdvice - 统一响应包装（最先） */
  public static final int GLOBAL_RESPONSE = 0;

  /** BaseExceptionHandler - 业务异常 */
  public static final int BASE_EXCEPTION = 10;

  /** MvcExceptionHandler - MVC 框架异常 */
  public static final int MVC_EXCEPTION = 20;

  /** ValidationExceptionHandler - 参数校验异常 */
  public static final int VALIDATION_EXCEPTION = 30;
}
