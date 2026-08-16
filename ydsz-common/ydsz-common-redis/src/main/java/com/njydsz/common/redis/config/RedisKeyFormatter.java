package com.njydsz.common.redis.config;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * Redis Key 前缀格式化器
 *
 * <p>统一所有 Redis Key 的拼接规范，散落各处的 formatKey 逻辑均通过本组件完成。
 *
 * <p>Key 拼接规则：
 *
 * <ul>
 *   <li>未配置 prefix 时：返回原始 key
 *   <li>配置了 prefix 时：返回 {@code prefix + ":" + key}
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Resource
 * private RedisKeyFormatter keyFormatter;
 *
 * // 限流 key
 * String rateLimitKey = keyFormatter.withCategory("ratelimit", "api:user:" + userId);
 * // → "myapp:ratelimit:api:user:10086"
 *
 * // 通用 key
 * String cacheKey = keyFormatter.format("user:" + userId);
 * // → "myapp:user:10086"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.3.0
 */
@Component
@RequiredArgsConstructor
public class RedisKeyFormatter {

  private final RedisProperties redisProperties;

  /**
   * 格式化 Key（添加统一前缀）
   *
   * @param key 业务 Key
   * @return 带前缀的完整 Key；未配置前缀时返回原始 Key
   */
  public String format(String key) {
    if (key == null) {
      return null;
    }
    String prefix = redisProperties.getKeyPrefix();
    if (prefix == null || prefix.isEmpty()) {
      return key;
    }
    return prefix + ":" + key;
  }

  /**
   * 格式化 Key（带分类类别）
   *
   * <p>在统一前缀与业务 Key 之间插入类别段， 提高 Key 可读性与可管理性。
   *
   * @param category 分类名称（如 "ratelimit"、"delayed:queue"、"lock"）
   * @param key 业务 Key
   * @return 带类别前缀的完整 Key
   */
  public String withCategory(String category, String key) {
    if (key == null) {
      return null;
    }
    String prefix = redisProperties.getKeyPrefix();
    if (prefix == null || prefix.isEmpty()) {
      return category + ":" + key;
    }
    return prefix + ":" + category + ":" + key;
  }

  /**
   * 格式化 Key（带分类类别和子段）
   *
   * @param category 分类名称
   * @param sub 子段名称
   * @param key 业务 Key
   * @return 带类别和子段前缀的完整 Key
   */
  public String withCategoryAndSub(String category, String sub, String key) {
    if (key == null) {
      return null;
    }
    String prefix = redisProperties.getKeyPrefix();
    if (prefix == null || prefix.isEmpty()) {
      return category + ":" + sub + ":" + key;
    }
    return prefix + ":" + category + ":" + sub + ":" + key;
  }

  /**
   * 获取当前前缀
   *
   * @return 当前配置的前缀
   */
  public String getPrefix() {
    return redisProperties.getKeyPrefix();
  }

  /**
   * 判断是否已配置前缀
   *
   * @return true 表示已配置非空前缀
   */
  public boolean hasPrefix() {
    String prefix = redisProperties.getKeyPrefix();
    return prefix != null && !prefix.isEmpty();
  }

  /**
   * 从完整 Key 中剥离前缀，还原业务 Key
   *
   * @param fullKey 带前缀的完整 Key
   * @return 剥离前缀后的业务 Key；无前缀时返回原始值
   */
  public String stripPrefix(String fullKey) {
    if (fullKey == null) {
      return null;
    }
    String prefix = redisProperties.getKeyPrefix();
    if (prefix == null || prefix.isEmpty()) {
      return fullKey;
    }
    String prefixWithColon = prefix + ":";
    if (fullKey.startsWith(prefixWithColon)) {
      return fullKey.substring(prefixWithColon.length());
    }
    return fullKey;
  }
}
