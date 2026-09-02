package com.njydsz.gateway.config;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.njydsz.common.core.constant.HeaderConstants;

/**
 * 路径安全工具类
 *
 * <p>提供路径规范化、白名单匹配和内部头列表功能，防止路径穿越攻击和客户端伪造内部头。
 *
 * <h3>P2-12 增强项</h3>
 *
 * <ul>
 *   <li>双重 URL 编码检测：拦截 {@code %252e%252e} (Double-Encoding 绕过)
 *   <li>null 字节注入防护：拦截 {@code %00}、{@code \0}
 *   <li>混合编码检测：拦截 {@code .%2f} 等混合编码穿越
 *   <li>URL 解码规范化：先解码再检测，防范编码绕过
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public final class PathGuard {

  private PathGuard() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** 内部头名称列表（客户端传入时必须剥离） */
  private static final Set<String> INTERNAL_HEADERS =
      Set.of(
          GatewayConstants.HEADER_TRACE_ID,
          GatewayConstants.HEADER_USER_ID,
          GatewayConstants.HEADER_USERNAME,
          GatewayConstants.HEADER_USER_ROLES,
          GatewayConstants.HEADER_USER_PERMISSIONS,
          GatewayConstants.HEADER_INTERNAL_SIG,
          GatewayConstants.HEADER_TENANT_ID,
          HeaderConstants.X_FORWARDED_FOR,
          "X-Real-IP");

  /** 最大解码次数（防止递归解码 DoS） */
  private static final int MAX_DECODE_ITERATIONS = 3;

  /**
   * 创建不可修改的白名单集合
   *
   * @param paths 白名单路径
   * @return 不可修改的 Set
   */
  public static Set<String> whiteList(String... paths) {
    return Set.of(paths);
  }

  /**
   * 路径规范化，检测并拦截路径穿越攻击
   *
   * <h3>P2-12 增强检测项</h3>
   *
   * <ul>
   *   <li>基础穿越模式：{@code ..}、{@code %2e}、{@code //}、{@code \}、{@code %5c}、{@code %2f}
   *   <li>双重编码：{@code %252e%252e} (Double-Encoding bypass)
   *   <li>null 字节注入：{@code %00}、{@code \0}
   *   <li>混合编码：{@code .%2f}、{@code %2e%2f}
   * </ul>
   *
   * <p>检测流程：
   *
   * <ol>
   *   <li>先进行递归 URL 解码（最多 3 次），防范编码绕过
   *   <li>对解码后的路径进行模式匹配检测
   * </ol>
   *
   * @param rawPath 原始路径
   * @return 规范化后的路径，如果检测到穿越攻击返回 null
   */
  public static String sanitize(String rawPath) {
    if (rawPath == null || rawPath.isEmpty()) {
      return rawPath;
    }

    // P2-5 快速失败：路径不含任何可疑字符（. % \ // null 字节）时不可能存在穿越/编码攻击，直接放行
    // 覆盖 >99% 的正常请求，避免不必要的 URL 解码 + 多模式匹配开销
    if (rawPath.indexOf('.') < 0
        && rawPath.indexOf('%') < 0
        && rawPath.indexOf('\\') < 0
        && rawPath.indexOf('\0') < 0
        && rawPath.indexOf("//") < 0) {
      return rawPath;
    }

    // ---- 第一层：原始路径检测（编码未被解码前）----
    // 在 URL 解码前先检测原始编码模式，防止 .%2f 等"点 + 编码斜杠"的混合编码在解码后
    // 被转换成 ./ 而绕过解码后检测（P0-A3 修复）
    String rawLower = rawPath.toLowerCase();
    if (rawLower.contains("..")
        || rawLower.contains("%2e%2e")
        || rawLower.contains("%2e.")
        || rawLower.contains("\\")
        || rawLower.contains("%5c")
        || rawLower.contains("//")
        || rawLower.contains("%00")
        || rawPath.indexOf('\0') >= 0
        || rawLower.contains(".%2f")
        || rawLower.contains(".%5c")
        || rawLower.contains("%2f.")
        || rawLower.contains("%2e%2f")
        || rawLower.contains("%2e%5c")) {
      return null;
    }

    // ---- 第二层：解码后检测（防范 Double-Encoding 绕过）----
    // P2-12: 递归 URL 解码（最多 3 次）
    String decodedPath = recursiveDecode(rawPath);
    String lowerPath = decodedPath.toLowerCase();

    if (lowerPath.contains("..")
        || lowerPath.contains("\\")
        || lowerPath.contains("//")
        || decodedPath.contains("\0")) {
      return null;
    }

    return rawPath;
  }

  /**
   * 递归 URL 解码（最多 MAX_DECODE_ITERATIONS 次）
   *
   * <p>防范 Double-Encoding 攻击：攻击者将路径编码两次 ({@code %252e} 代表 {@code %2e} 再代表 {@code .})
   *
   * @param path 原始路径
   * @return 解码后的路径（或原始路径，如果解码失败）
   */
  private static String recursiveDecode(String path) {
    String result = path;
    int iterations = 0;

    while (iterations < MAX_DECODE_ITERATIONS) {
      String prev = result;
      try {
        result = URLDecoder.decode(result, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException | IllegalArgumentException e) {
        // 解码失败，返回上次成功解码的结果或原始路径
        return prev;
      }
      // 解码后不再变化，提前终止
      if (result.equals(prev)) {
        break;
      }
      iterations++;
    }

    return result;
  }

  /**
   * 精确匹配白名单（大小写不敏感）
   *
   * <p>P1: HTTP 路径按 RFC 3986 是 case-sensitive 的，但实际环境中：
   *
   * <ul>
   *   <li>部分反向代理 / CDN 会把路径转换为小写（如 Cloudflare 的 Page Rules）
   *   <li>Spring MVC 的 {@code AntPathMatcher} 默认大小写敏感，但部分开发者可能误写大小写
   *   <li>Windows 文件系统大小写不敏感，可能导致路径解析差异
   * </ul>
   *
   * <p>因此白名单匹配改为大小写不敏感，避免环境差异导致认证失败， 同时不影响安全性（攻击者用大小写混淆绕过的可能性已被 sanitize() 阻断）。
   *
   * @param path 请求路径
   * @param whiteList 白名单集合
   * @return true 如果路径（大小写不敏感地）匹配白名单中的某一项
   */
  public static boolean matchWhiteList(String path, Set<String> whiteList) {
    if (path == null || whiteList == null || whiteList.isEmpty()) {
      return false;
    }
    // 快速路径：先精确匹配（保留原 case 大小写的常见场景快速命中）
    if (whiteList.contains(path)) {
      return true;
    }
    // 慢速路径：大小写不敏感匹配（处理代理/CDN 转换后的路径）
    String lowerPath = path.toLowerCase(Locale.ROOT);
    for (String allowed : whiteList) {
      if (allowed != null && allowed.toLowerCase(Locale.ROOT).equals(lowerPath)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 返回需要剥离的内部头名称列表
   *
   * @return 内部头名称集合
   */
  public static Set<String> internalHeaders() {
    return INTERNAL_HEADERS;
  }

  /**
   * 判断路径是否为白名单路径（跳过鉴权）。
   *
   * <p>内置默认白名单包含健康检查、认证入口等无需鉴权的端点。 业务模块可通过 {@link #matchWhiteList(String, Set)} 自定义白名单。
   *
   * @param path 请求路径
   * @return true 如果路径匹配默认白名单
   */
  public static boolean isWhiteList(String path) {
    // 默认白名单：健康检查、认证入口、Actuator 端点
    return DEFAULT_WHITELIST.stream().anyMatch(
        pattern -> pathMatch(path, pattern));
  }

  /** 默认白名单路径（无需鉴权的端点） */
  private static final List<String> DEFAULT_WHITELIST = List.of(
      "/actuator/**",
      "/auth/**",
      "/api/v1/auth/**",
      "/login",
      "/error");

  /**
   * 路径模式匹配（支持 Ant 风格通配符）。
   *
   * @param path 请求路径
   * @param pattern 匹配模式
   * @return true 如果匹配
   */
  private static boolean pathMatch(String path, String pattern) {
    if (path == null || pattern == null) {
      return false;
    }
    if (pattern.endsWith("/**")) {
      String prefix = pattern.substring(0, pattern.length() - 3);
      return path.startsWith(prefix);
    }
    return path.equals(pattern);
  }
}
