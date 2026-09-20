package com.njydsz.common.socket.audit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 审计敏感字段脱敏器（SEC-003）。
 *
 * <p>在结构化审计日志输出前，对 Map 副本中命中的敏感字段执行脱敏处理；脱敏结果为 **
 * 或掩码后的字符串。
 *
 * <p>敏感字段名集合由 {@link #DEFAULT_SENSITIVE_KEYS} 与配置 {@code
 * ydsz.websocket.audit.sensitiveKeys} 共同决定，依据"字段名子串匹配"：只要在 full key path
 * 中（如嵌套 Map 中的 {@code headers.Authorization}）命中敏感字段，就执行脱敏。
 *
 * <p>脱敏规则：
 *
 * <ul>
 *   <li>长度 ≤ 8：全部替换为 {@code ****}
 *   <li>长度 9-32：保留前 2 后 2，中间替换为 ****
 *   <li>长度 > 32：保留前 4 后 4，中间替换为 ****
 * </ul>
 *
 * <p>本脱敏器仅作用于"收集侧（应用内 audit logger）"，日志采集层（Filebeat / Fluentd）的脱敏逻辑
 * 由各业务线自行配置；避免双脱敏导致审计字段被破坏，无法复盘。
 *
 * <p>线程安全：本实例无状态，可单例共享。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public class SensitiveFieldRedactor {

  /** 默认敏感字段集合（大小写不敏感子串匹配）。 */
  public static final Set<String> DEFAULT_SENSITIVE_KEYS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "password",
                  "passwd",
                  "token",
                  "accessToken",
                  "refreshToken",
                  "authorization",
                  "secret",
                  "apiKey",
                  "api_key",
                  "x-gateway-secret",
                  "idCard",
                  "idcard",
                  "mobile",
                  "phone",
                  "bankCard",
                  "email")));

  /** 敏感字段正则：字段路径中任意 token 命中集合即视为敏感。 */
  private static final Pattern PATH_TOKEN_PATTERN = Pattern.compile("[\\._\\-]");

  private final Set<String> sensitiveKeys;

  /** 构造脱敏器（使用默认敏感字段集合）。 */
  public SensitiveFieldRedactor() {
    this(DEFAULT_SENSITIVE_KEYS);
  }

  /**
   * 构造脱敏器（自定义敏感字段集合）。
   *
   * @param customKeys 自定义字段名集合
   */
  public SensitiveFieldRedactor(Set<String> customKeys) {
    this.sensitiveKeys = new HashSet<>(DEFAULT_SENSITIVE_KEYS);
    if (customKeys != null) {
      this.sensitiveKeys.addAll(customKeys);
    }
  }

  /**
   * 审计日志 Map 深拷贝并脱敏。
   *
   * <p>返回的是新 Map，原始 Map 不被修改。
   *
   * @param entry 审计日志 Map（由用户构建）
   * @return 脱敏后的新 Map
   */
  public Map<String, Object> redact(Map<String, Object> entry) {
    if (entry == null || entry.isEmpty()) {
      return entry;
    }
    Map<String, Object> copy = new LinkedHashMap<>(entry.size());
    for (Map.Entry<String, Object> kv : entry.entrySet()) {
      copy.put(kv.getKey(), redactValue(kv.getKey(), kv.getValue()));
    }
    return copy;
  }

  /**
   * 判定字段路径是否命中敏感关键字集合（大小写不敏感子串匹配）。
   *
   * @param keyPath 字段路径（如 "headers.Authorization" 或 "token"）
   * @return true 表示命中
   */
  public boolean isSensitiveKey(String keyPath) {
    if (keyPath == null || keyPath.isEmpty()) {
      return false;
    }
    String lower = keyPath.toLowerCase();
    // 路径整体匹配
    if (sensitiveKeys.contains(lower)) {
      return true;
    }
    // 路径 token 级匹配：split 后任一 token命中集合
    String[] tokens = PATH_TOKEN_PATTERN.split(lower);
    for (String token : tokens) {
      if (sensitiveKeys.contains(token)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 根据字段名与值类型进行脱敏（String / Map / 其他）。
   *
   * @param key 字段名 / 路径
   * @param value 字段值
   * @return 脱敏后的值（String 脱敏为掩码形式；递归脱敏 Map；其他类型原样返回）
   */
  @SuppressWarnings("unchecked")
  private Object redactValue(String key, Object value) {
    if (value == null) {
      return null;
    }
    if (!isSensitiveKey(key)) {
      if (value instanceof Map) {
        // 未命中但本身为 Map，继续递归脱敏其内层字段
        Map<String, Object> inner = (Map<String, Object>) value;
        Map<String, Object> copy = new LinkedHashMap<>(inner.size());
        for (Map.Entry<String, Object> kv : inner.entrySet()) {
          copy.put(kv.getKey(), redactValue(kv.getKey(), kv.getValue()));
        }
        return copy;
      }
      return value;
    }
    // 命中敏感字段：根据类型脱敏
    if (value instanceof String) {
      return maskString((String) value);
    }
    if (value instanceof Map) {
      // 整个 Map 替换（若整体判定为敏感，如 "headers" -> "Authorization" 已经命中）
      return "****";
    }
    if (value instanceof Number) {
      return "****";
    }
    // Boolean / 其他 — 保留原样
    return value;
  }

  /**
   * 对字符串值进行掩码（长度分档）。
   *
   * @param value 原始字符串
   * @return 脱敏后字符串
   */
  public static String maskString(String value) {
    if (value == null) {
      return null;
    }
    int len = value.length();
    if (len == 0) {
      return "";
    }
    if (len <= 8) {
      return "****";
    }
    if (len <= 32) {
      return value.substring(0, 2) + "****" + value.substring(len - 2);
    }
    return value.substring(0, 4) + "****" + value.substring(len - 4);
  }
}
