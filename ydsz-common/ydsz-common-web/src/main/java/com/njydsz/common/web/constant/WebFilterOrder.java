package com.njydsz.common.web.constant;

import org.springframework.core.Ordered;

/**
 * Web 端 Servlet Filter 执行顺序常量。
 *
 * <p>Servlet Filter 使用 {@link Ordered#HIGHEST_PRECEDENCE} 为基准的整数体系， 数值越小优先级越高（最先执行）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class WebFilterOrder {

  private WebFilterOrder() {
    throw new UnsupportedOperationException("Constants class");
  }

  /** ContentCachingFilter：在鉴权之前包装 request body */
  public static final int CONTENT_CACHING_FILTER = Ordered.HIGHEST_PRECEDENCE + 20;

  /** TraceIdResponseFilter：traceId 注入到 response header */
  public static final int TRACE_ID_RESPONSE_FILTER = Ordered.HIGHEST_PRECEDENCE + 40;

  /** WebAuthFilter：JWT/Session 鉴权 */
  public static final int AUTH_FILTER = Ordered.HIGHEST_PRECEDENCE + 50;
}
