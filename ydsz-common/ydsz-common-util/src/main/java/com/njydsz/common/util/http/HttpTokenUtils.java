package com.njydsz.common.util.http;

import jakarta.servlet.http.HttpServletRequest;

import com.njydsz.common.util.internal.proxy.CoreConstants;
import com.njydsz.common.util.string.StringUtils;

/**
 * HTTP Token 提取工具类
 *
 * <p>封装从 HTTP 请求头中提取访问令牌（Access Token）的标准化逻辑， 支持多种 Token 传输头和前缀剥离。
 *
 * <h2>支持的 Token 头</h2>
 *
 * <ol>
 *   <li>{@code X-Access-Token}（Ydsz 自定义头，优先级最高）
 *   <li>{@code Authorization}（标准 OAuth2/JWT 头）
 * </ol>
 *
 * <h2>前缀剥离</h2>
 *
 * <p>若 Token 以 "{@code ydsz}" 开头（如 {@code ydsz eyJhbG...}）， 自动剥离前缀并返回仅包含 Token 部分的内容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class HttpTokenUtils {

  /** 标准 HTTP 授权头名称（引用 core HeaderConstants，避免重复定义） */
  public static final String AUTHORIZATION_HEADER = CoreConstants.AUTHORIZATION;

  /** Ydsz 访问令牌自定义头名称（与 AuthHeaderConstants.X_ACCESS_TOKEN 值相同） */
  public static final String X_ACCESS_TOKEN_HEADER = "X-Access-Token";

  /** 访问令牌前缀（用于剥离 Ydsz 私有前缀） */
  private static final String TOKEN_PREFIX = "ydsz";

  private HttpTokenUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 从当前请求上下文中获取 Token。
   *
   * <p>依次尝试 {@code X-Access-Token}、{@code Authorization} 头， 自动剥离 Ydsz 私有前缀（如果存在）。
   *
   * @return Token 字符串，若无 Token 返回 null
   */
  public static String getToken() {
    HttpServletRequest request = RequestContextUtils.getRequest();
    if (request == null) {
      return null;
    }
    return getToken(request);
  }

  /**
   * 从指定请求中提取 Token。
   *
   * <p>依次尝试 {@code X-Access-Token}、{@code Authorization} 头， 自动剥离 Ydsz 私有前缀（如果存在）。
   *
   * @param request HTTP 请求
   * @return Token 字符串，若无 Token 或 request 为 null 返回 null
   */
  public static String getToken(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String token = request.getHeader(X_ACCESS_TOKEN_HEADER);
    if (StringUtils.isEmpty(token)) {
      token = request.getHeader(AUTHORIZATION_HEADER);
    }
    return stripPrefix(token);
  }

  /**
   * 剥离 Token 前缀。
   *
   * <p>若 Token 以 "ydsz" 开头（忽略大小写敏感默认）， 返回去除前缀及后续空白后的内容。
   *
   * @param token 原始 Token 字符串
   * @return 剥离前缀后的 Token；若 token 为空白则返回原值
   */
  public static String stripPrefix(String token) {
    if (StringUtils.isNotEmpty(token) && token.startsWith(TOKEN_PREFIX)) {
      return token.substring(TOKEN_PREFIX.length()).trim();
    }
    return token;
  }

  /**
   * 判断当前请求是否携带有效 Token。
   *
   * @return true 表示存在非空 Token
   */
  public static boolean hasToken() {
    return StringUtils.isNotEmpty(getToken());
  }

  /**
   * 判断指定请求是否携带有效 Token。
   *
   * @param request HTTP 请求
   * @return true 表示存在非空 Token
   */
  public static boolean hasToken(HttpServletRequest request) {
    return StringUtils.isNotEmpty(getToken(request));
  }
}
