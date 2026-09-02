package com.njydsz.common.base.constant;

import org.springframework.core.Ordered;

/**
 * Servlet Filter 执行顺序常量。
 *
 * <p>所有数字必须与 {@code docs/BASE_INTERCEPTOR_ORDER.md} 保持一致。 修改任何数字前请先更新文档。
 *
 * <p>Servlet Filter 使用 {@link Ordered#HIGHEST_PRECEDENCE} 为基准的整数体系， 数值越小优先级越高（最先执行）。
 *
 * <p><b>注意：</b>仅保留 base 模块实际使用的常量，其他模块的 Filter 顺序常量 应定义在各自模块中。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class FilterOrder {

  private FilterOrder() {
    throw new UnsupportedOperationException("Constants class");
  }

  /** SecurityHeaderFilter：在响应中追加安全头 */
  public static final int SECURITY_HEADER_FILTER = Ordered.HIGHEST_PRECEDENCE + 30;
}
