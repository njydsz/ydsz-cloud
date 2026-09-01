package com.njydsz.common.base.interceptor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求 ID 解析器接口
 *
 * <p>统一定义请求 ID（requestId / traceId）的解析逻辑，供 Filter 和 Interceptor 共享。 子类提供具体的 ID 来源实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface RequestIdResolver {

  /**
   * 解析请求 ID
   *
   * <p>子类覆盖此方法提供具体的请求 ID 解析逻辑， 例如从 RequestContext、MDC 或请求头中获取。
   *
   * @param request HTTP 请求
   * @return 请求 ID，不存在时返回 null
   */
  String resolveRequestId(HttpServletRequest request);
}
