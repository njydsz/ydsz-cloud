package com.njydsz.common.safe.util;

import jakarta.servlet.http.HttpServletRequest;

import com.njydsz.common.util.net.ClientIpResolver;

/**
 * 客户端 IP 解析工具（已下沉到 ydsz-common-util）。
 *
 * <p>本类保留为向后兼容的转发类，所有方法委托给 {@link com.njydsz.common.util.net.ClientIpResolver}。 新代码请直接使用 {@code com.njydsz.common.util.net.ClientIpResolver}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 已下沉到 {@link com.njydsz.common.util.net.ClientIpResolver}，请使用新版本
 */
@Deprecated
public final class ClientIpResolver {

  private ClientIpResolver() {}

  /**
   * 从 HTTP 请求中解析客户端真实 IP（含可信代理校验）
   *
   * @param request HTTP 请求
   * @return 客户端 IP 地址
   * @deprecated 委托 {@link com.njydsz.common.util.net.ClientIpResolver#getClientIp(HttpServletRequest)}
   */
  @Deprecated
  public static String getClientIp(HttpServletRequest request) {
    return com.njydsz.common.util.net.ClientIpResolver.getClientIp(request);
  }

  /**
   * 从直连 IP 和代理头中解析客户端真实 IP（框架无关版本）。
   *
   * @param directIp 直连 IP（不可伪造，由 TCP 连接获得）
   * @param xForwardedFor X-Forwarded-For 头值（可为 null）
   * @param xRealIp X-Real-IP 头值（可为 null）
   * @return 客户端真实 IP
   * @deprecated 委托 {@link com.njydsz.common.util.net.ClientIpResolver#resolveFromHeaders(String, String, String)}
   */
  @Deprecated
  public static String resolveFromHeaders(String directIp, String xForwardedFor, String xRealIp) {
    return com.njydsz.common.util.net.ClientIpResolver.resolveFromHeaders(directIp, xForwardedFor, xRealIp);
  }

  /**
   * 判断 IP 是否为可信代理
   *
   * @param ip IP 地址
   * @return true 为可信代理
   * @deprecated 委托 {@link com.njydsz.common.util.net.ClientIpResolver#isTrustedProxy(String)}
   */
  @Deprecated
  public static boolean isTrustedProxy(String ip) {
    return com.njydsz.common.util.net.ClientIpResolver.isTrustedProxy(ip);
  }

  /**
   * 判断 IP 是否为内网地址
   *
   * @param ip IP 地址
   * @return true 为内网地址
   * @deprecated 委托 {@link com.njydsz.common.util.net.ClientIpResolver#isInternalIp(String)}
   */
  @Deprecated
  public static boolean isInternalIp(String ip) {
    return com.njydsz.common.util.net.ClientIpResolver.isInternalIp(ip);
  }
}
