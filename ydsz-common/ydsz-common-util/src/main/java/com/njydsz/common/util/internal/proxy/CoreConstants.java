package com.njydsz.common.util.internal.proxy;

/**
 * ydsz-common-core 模块中使用的常量副本。
 *
 * <p>L1 工具层不直接依赖 L2 的常量类，而是在此维护必要的常量副本。 这些常量值与 core 模块保持一致。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class CoreConstants {

  private CoreConstants() {
    throw new UnsupportedOperationException("Utility class should not be instantiated");
  }

  /** 标准 HTTP 授权头名称 */
  public static final String AUTHORIZATION = "Authorization";

  /** 认证信息在请求上下文中的键名 */
  public static final String KEY_AUTH_INFO = "authInfo";

  /** Trace ID 在 MDC 中的键名 */
  public static final String MDC_TRACE_ID_KEY = "traceId";
}
