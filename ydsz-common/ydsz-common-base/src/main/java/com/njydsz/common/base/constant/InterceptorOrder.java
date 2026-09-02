package com.njydsz.common.base.constant;

/**
 * Spring MVC Interceptor 执行顺序常量。
 *
 * <p>所有数字必须与 {@code docs/BASE_INTERCEPTOR_ORDER.md} 保持一致。 修改任何数字前请先更新文档。
 *
 * <p>Spring MVC Interceptor 使用自然数体系（0, 10, 20...）， 数值越小优先级越高（最先执行）。
 *
 * <p><b>注意：</b>仅保留 base 模块实际使用的常量，其他模块的 Interceptor 顺序常量 应定义在各自模块中。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class InterceptorOrder {

  private InterceptorOrder() {
    throw new UnsupportedOperationException("Constants class");
  }

  /** RequestLogInterceptor - 请求/响应日志 */
  public static final int REQUEST_LOG = 10;
}
