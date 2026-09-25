package com.njydsz.common.safe.spi;

/**
 * 数据脱敏策略提供者 SPI。
 *
 * <p>定义统一的脱敏扩展点，使业务模块能够：
 * <ul>
 *   <li>为特定类型（手机号、身份证、银行卡、地址等）注册自定义脱敏策略</li>
 *   <li>在运行时按优先级选择合适的脱敏实现（默认实现 → 业务覆盖）</li>
 *   <li>统一请求参数脱敏、响应字段脱敏、数据库层脱敏的编程接口</li>
 * </ul>
 *
 * <p>脱敏场景分层：
 * <table border="1">
 *   <tr><th>层级</th><th>位置</th><th>使用方式</th></tr>
 *   <tr><td>响应层</td><td>{@code @Sensitive} 注解 + ColumnDesensitizationExecutor</td><td>声明式脱敏（已有）</td></tr>
 *   <tr><td>请求参数层</td><td>Filter/Advice 拦截后调用 {@link #desensitize}</td><td>编程式脱敏</td></tr>
 *   <tr><td>数据库层</td><td>自定义 TypeHandler 调用 {@link #desensitize}</td><td>入库前脱敏</td></tr>
 * </table>
 *
 * <p><b>默认实现</b>：ydsz-common-safe 提供 {@code DefaultDesensitizeProvider}（基于 {@link
 * com.njydsz.common.safe.desensitize.SensitiveUtils SensitiveUtils} 的内置规则）；
 * 业务模块可通过 {@code @Primary} 注解或 Spring Boot 自动装配优先级覆盖默认实现。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Component
 * public class CustomDesensitizeProvider implements DesensitizeProvider {
 *     @Override
 *     public String desensitize(String raw, String type) {
 *         if ("BANK_CARD".equals(type)) {
 *             return raw.substring(0, 4) + " **** **** " + raw.substring(raw.length() - 4);
 *         }
 *         return DesensitizeProvider.super.desensitize(raw, type);
 *     }
 * }
 * }</pre>
 *
 * <p><b>能力对标</b>：Hutool SecureUtil 脱敏扩展 / Apache Commons 自研 Converter SPI
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see com.njydsz.common.safe.desensitize.SensitiveUtils
 * @see com.njydsz.common.safe.desensitize.SensitiveType
 */
public interface DesensitizeProvider {

  /**
   * 对原始数据按指定脱敏类型执行脱敏。
   *
   * <p>实现类应先匹配自身支持的脱敏类型（如 {@code MOBILE}、 {@code ID_CARD}、 {@code BANK_CARD}、
   * {@code EMAIL}、 {@code ADDRESS}、 {@code NAME}），若无法处理则调用 {@link #defaultDesensitize} 回退到内置规则。
   *
   * @param raw 原始数据（非 null；空字符串或长度 &lt; 2 时直接返回原值）
   * @param type 脱敏类型标识（对应 {@link com.njydsz.common.safe.desensitize.SensitiveType#name()}）
   * @return 脱敏后的字符串
   */
  String desensitize(String raw, String type);

  /**
   * 对原始数据按指定脱敏类型和自定义掩码规则执行脱敏。
   *
   * @param raw 原始数据（非 null）
   * @param type 脱敏类型标识
   * @param keepPrefix 前缀保留字符数
   * @param keepSuffix 后缀保留字符数
   * @return 脱敏后的字符串
   */
  default String desensitize(String raw, String type, int keepPrefix, int keepSuffix) {
    if (raw == null || raw.length() < 2) {
      return raw;
    }
    int prefix = Math.max(0, Math.min(keepPrefix, raw.length()));
    int suffix = Math.max(0, Math.min(keepSuffix, raw.length() - prefix));
    int maskLen = raw.length() - prefix - suffix;
    if (maskLen <= 0) {
      return raw;
    }
    StringBuilder sb = new StringBuilder(raw.length());
    sb.append(raw, 0, prefix);
    sb.append("*".repeat(maskLen));
    sb.append(raw, raw.length() - suffix, raw.length());
    return sb.toString();
  }

  /**
   * 判断当前 Provider 是否支持指定脱敏类型。
   *
   * <p>默认实现全部返回 true（回退到内置规则），业务覆盖实现可仅返回自己关心的类型。
   *
   * @param type 脱敏类型标识
   * @return true 表示当前 Provider 能处理该类型
   */
  default boolean supports(String type) {
    return true;
  }

  /**
   * 回退到默认脱敏实现（基于 SensitiveUtils 内置规则）。
   *
   * <p>当自定义 Provider 无法处理特定类型时调用本方法。
   *
   * @param raw 原始数据
   * @param type 脱敏类型标识
   * @return 脱敏后的字符串，未知类型时返回 "******"
   */
  default String defaultDesensitize(String raw, String type) {
    if (raw == null || raw.isEmpty()) {
      return raw;
    }
    try {
      com.njydsz.common.safe.desensitize.SensitiveType sensitiveType =
          com.njydsz.common.safe.desensitize.SensitiveType.valueOf(type.toUpperCase());
      return com.njydsz.common.safe.desensitize.SensitiveUtils.mask(raw, sensitiveType);
    } catch (IllegalArgumentException e) {
      return "******";
    }
  }
}
