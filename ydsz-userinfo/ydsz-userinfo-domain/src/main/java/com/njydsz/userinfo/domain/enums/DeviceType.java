package com.njydsz.userinfo.domain.enums;

import java.util.Locale;

import com.njydsz.common.util.message.MessageUtils;

/**
 * 设备类型枚举。
 *
 * <p>用于分端会话控制，按设备类型（Web/APP/API）独立限制并发会话数。
 * 设备类型推断逻辑：优先使用 {@code X-Platform} 请求头，其次从 {@code User-Agent} 推断。
 *
 * <p><b>推断规则：</b>
 *
 * <ul>
 *   <li>{@code X-Platform} 请求头为 {@code web}/{@code app}/{@code api} 时直接映射</li>
 *   <li>{@code User-Agent} 包含 {@code Mobile}/{@code Android}/{@code iPhone} 等关键字时判定为 APP</li>
 *   <li>{@code User-Agent} 包含 {@code curl}/{@code Postman}/{@code HTTPie} 等关键字时判定为 API</li>
 *   <li>其他情况判定为 WEB</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DeviceType {

  /** Web 浏览器 */
  WEB("web", "Web浏览器"),

  /** 移动应用（iOS/Android） */
  APP("app", "移动应用"),

  /** API 调用（curl/Postman/程序调用） */
  API("api", "API调用"),

  /** 未知设备（无法识别时兜底） */
  UNKNOWN("unknown", "未知设备");

  /** 设备类型编码（小写，与配置键一致） */
  private final String code;

  /** 设备类型描述 */
  private final String description;

  DeviceType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return MessageUtils.getMessage("userinfo.device." + code, description);
  }

  /**
   * 从 User-Agent 和 X-Platform 请求头推断设备类型。
   *
   * <p>推断优先级：
   *
   * <ol>
   *   <li>{@code X-Platform} 请求头（显式声明，最可靠）</li>
   *   <li>{@code User-Agent} 字符串模式匹配</li>
   * </ol>
   *
   * @param userAgent User-Agent 请求头，可为 null
   * @param platformHeader {@code X-Platform} 请求头，可为 null
   * @return 推断出的设备类型，不会返回 null（无法识别时返回 {@link #UNKNOWN}）
   */
  public static DeviceType resolve(String userAgent, String platformHeader) {
    // 优先使用 X-Platform 请求头（显式声明）
    if (platformHeader != null && !platformHeader.isBlank()) {
      String platform = platformHeader.trim().toLowerCase(Locale.ROOT);
      for (DeviceType type : values()) {
        if (type.code.equals(platform)) {
          return type;
        }
      }
    }

    // 从 User-Agent 推断
    if (userAgent != null && !userAgent.isBlank()) {
      String ua = userAgent.toLowerCase(Locale.ROOT);

      // APP 特征：Mobile / Android / iPhone / iPad
      if (containsAny(ua, "mobile", "android", "iphone", "ipad", "okhttp", "dart")) {
        return APP;
      }

      // API 特征：curl / Postman / HTTPie / python-requests / java/
      if (containsAny(
          ua,
          "curl",
          "postman",
          "httpie",
          "python-requests",
          "java/",
          "apache-httpclient",
          "go-http-client",
          "node-fetch",
          "axios")) {
        return API;
      }
    }

    // 默认兜底为 WEB
    return WEB;
  }

  /**
   * 判断字符串是否包含任意一个关键字。
   *
   * @param text 待检查文本
   * @param keywords 关键字列表
   * @return 包含任一关键字时返回 true
   */
  private static boolean containsAny(String text, String... keywords) {
    for (String keyword : keywords) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }
}
