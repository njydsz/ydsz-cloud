package com.njydsz.system.server.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 强类型配置服务 — 提供类型安全的配置值获取能力。
 *
 * <p>消除业务方自行解析字符串配置值的重复代码，统一类型转换逻辑。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 获取字符串配置
 * String host = typedConfigService.getString("system.redis.host", "localhost");
 *
 * // 获取整数配置
 * int port = typedConfigService.getInt("system.redis.port", 6379);
 *
 * // 获取布尔配置
 * boolean enabled = typedConfigService.getBoolean("system.feature.xxx", false);
 *
 * // 获取 JSON 配置
 * Map<String, Object> map = typedConfigService.getJson("system.rule.config", Map.class, null);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
public interface TypedConfigService {

  /**
   * 获取字符串配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在时返回默认值
   */
  String getString(String configKey, String defaultValue);

  /**
   * 获取整数配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  Integer getInt(String configKey, Integer defaultValue);

  /**
   * 获取长整数配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  Long getLong(String configKey, Long defaultValue);

  /**
   * 获取布尔配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  Boolean getBoolean(String configKey, Boolean defaultValue);

  /**
   * 获取数值配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  BigDecimal getDecimal(String configKey, BigDecimal defaultValue);

  /**
   * 获取 JSON 配置值并反序列化为指定类型
   *
   * @param configKey 配置键
   * @param clazz 目标类型
   * @param defaultValue 默认值
   * @param <T> 目标类型泛型
   * @return 配置值，不存在或解析失败时返回默认值
   */
  <T> T getJson(String configKey, Class<T> clazz, T defaultValue);

  /**
   * 获取 JSON 配置值并转换为 Map
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return Map 类型配置值，不存在或解析失败时返回默认值
   */
  Map<String, Object> getJsonAsMap(String configKey, Map<String, Object> defaultValue);
}
