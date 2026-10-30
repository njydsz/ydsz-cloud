package com.njydsz.common.redis;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 大 Key / 热 Key 检测工具
 *
 * <p>提供静态方法检测 Redis Key 是否超过预设阈值（大 Key）或访问频次过高（热 Key）。
 * 适用于写入前预检、定时巡检等场景，检测结果通过日志日志输出告警信息。
 *
 * <p><b>大 Key 判定标准：</b>
 *
 * <ul>
 *   <li>String 类型：值的字节数超过 {@link BigKeyThreshold#getStringValueBytesThreshold()}（默认 10KB）
 *   <li>Hash/Set/List/ZSet 类型：成员数量超过 {@link BigKeyThreshold#getCollectionMembersThreshold()}（默认 10000）
 * </ul>
 *
 * <p><b>热 Key 判定标准：</b>
 *
 * <ul>
 *   <li>访问频次超过 {@link BigKeyThreshold#getHotKeyAccessCountThreshold()}（默认 100 次）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 使用默认阈值检测大 Key
 * boolean isBig = BigKeyDetector.checkBigKey("order:detail:123", orderObj, BigKeyThreshold.defaults());
 *
 * // 自定义阈值
 * BigKeyThreshold threshold = BigKeyThreshold.builder()
 *     .stringValueBytesThreshold(5 * 1024)
 *     .collectionMembersThreshold(5000)
 *     .build();
 * BigKeyDetector.checkBigKey("cache:list:hot", dataList, threshold);
 *
 * // 检测热 Key
 * boolean isHot = BigKeyDetector.isHotKey("user:session:1001", 150, 100);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see BigKeyThreshold
 */
@Slf4j
public final class BigKeyDetector {

  /** 默认阈值实例 */
  private static volatile BigKeyThreshold defaultThreshold = BigKeyThreshold.defaults();

  /** 私有构造器，禁止实例化 */
  private BigKeyDetector() {
    throw new UnsupportedOperationException("工具类禁止实例化");
  }

  /**
   * 设置全局默认阈值
   *
   * <p>通过此方法可在应用启动时统一配置检测阈值，后续调用 {@link #checkBigKey(String, Object)} 无需重复传参。
   *
   * @param threshold 新的默认阈值，不可为 null
   */
  public static void setDefaultThreshold(BigKeyThreshold threshold) {
    defaultThreshold = Objects.requireNonNull(threshold, "threshold 不可为 null");
  }

  /**
   * 获取当前全局默认阈值
   *
   * @return 默认阈值实例
   */
  public static BigKeyThreshold getDefaultThreshold() {
    return defaultThreshold;
  }

  /**
   * 检测指定 Key 是否为大 Key（使用全局默认阈值）
   *
   * @param key Redis Key 名称
   * @param value Key 对应的值
   * @return true = 超过阈值触发大 Key 告警；false = 未超过阈值
   */
  public static boolean checkBigKey(String key, Object value) {
    return checkBigKey(key, value, defaultThreshold);
  }

  /**
   * 检测指定 Key 是否为大 Key
   *
   * <p>根据值的数据类型自动选择检测策略：
   *
   * <ul>
   *   <li>String 类型：检测 UTF-8 编码后的字节长度
   *   <li>Collection 类型（List/Set 等）：检测元素数量
   *   <li>Map 类型（Hash 等）：检测条目数量
   *   <li>其他类型：检测 {@link Object#toString()} 序列化后的字节长度
   * </ul>
   *
   * <p>超过阈值时输出 warn 级别告警日志（LOG-002 合规）。
   *
   * @param key Redis Key 名称
   * @param value Key 对应的值
   * @param threshold 大 Key 判定阈值
   * @return true = 超过阈值触发大 Key 告警；false = 未超过阈值
   */
  public static boolean checkBigKey(String key, Object value, BigKeyThreshold threshold) {
    Objects.requireNonNull(threshold, "threshold 不可为 null");
    if (key == null || key.isEmpty()) {
      log.warn("【ydsz-common-redis】checkBigKey 参数 key 为空，跳过检测");
      return false;
    }
    if (value == null) {
      return false;
    }

    String type = resolveType(value);
    long size = calculateSize(value, type);
    long thresholdValue = resolveThreshold(threshold, type);

    if (size > thresholdValue) {
      log.warn(
          "【ydsz-common-redis】Redis大key告警 key={}, type={}, size={}, threshold={}",
          key, type, size, thresholdValue);
      return true;
    }
    return false;
  }

  /**
   * 判断指定 Key 是否为热 Key
   *
   * <p>根据访问计数与阈值判断，当访问次数超过 {@link BigKeyThreshold#getHotKeyAccessCountThreshold()} 时
   * 视为热 Key，并输出 warn 级别告警日志。
   *
   * @param key Redis Key 名称
   * @param accessCount 单位时间窗口内的访问次数
   * @param threshold 热 Key 访问次数阈值（如使用全局默认阈值请传 {@link BigKeyThreshold#getDefaultThreshold()} 中的值）
   * @return true = 访问次数超过阈值，判定为热 Key；false = 未超过阈值
   */
  public static boolean isHotKey(String key, int accessCount, int threshold) {
    if (key == null || key.isEmpty()) {
      log.warn("【ydsz-common-redis】isHotKey 参数 key 为空，跳过检测");
      return false;
    }
    if (accessCount > threshold) {
      log.warn(
          "【ydsz-common-redis】Redis热key告警 key={}, accessCount={}, threshold={}",
          key, accessCount, threshold);
      return true;
    }
    return false;
  }

  /**
   * 判断指定 Key 是否为热 Key（使用全局默认阈值）
   *
   * @param key Redis Key 名称
   * @param accessCount 单位时间窗口内的访问次数
   * @return true = 访问次数超过阈值，判定为热 Key；false = 未超过阈值
   */
  public static boolean isHotKey(String key, int accessCount) {
    return isHotKey(key, accessCount, defaultThreshold.getHotKeyAccessCountThreshold());
  }

  // ============================ 内部工具方法 ============================

  /**
   * 解析值的数据类型标识
   *
   * @param value 待检测值
   * @return 类型标识字符串（STRING / HASH / LIST / SET / ZSET / OTHER）
   */
  private static String resolveType(Object value) {
    if (value instanceof String) {
      return "STRING";
    }
    if (value instanceof Map) {
      return "HASH";
    }
    if (value instanceof Collection) {
      return "LIST";
    }
    return "OTHER";
  }

  /**
   * 计算值的大小度量
   *
   * <p>对 String 和 OTHER 类型返回 UTF-8 字节长度，对 Collection 和 Map 返回元素/条目数量。
   *
   * @param value 值的对象
   * @param type 由 {@link #resolveValue} 解析的类型标识
   * @return 大小度量（字节数 或 元素数量）
   */
  private static long calculateSize(Object value, String type) {
    switch (type) {
      case "STRING":
        return ((String) value).getBytes(StandardCharsets.UTF_8).length;
      case "HASH":
        return ((Map<?, ?>) value).size();
      case "LIST":
        return ((Collection<?>) value).size();
      default:
        return value.toString().getBytes(StandardCharsets.UTF_8).length;
    }
  }

  /**
   * 根据数据类型选择对应的阈值
   *
   * @param threshold 阈值配置
   * @param type 由 {@link #resolveType} 解析的类型标识
   * @return 对应类型的阈值（字节数 或 成员数量）
   */
  private static long resolveThreshold(BigKeyThreshold threshold, String type) {
    switch (type) {
      case "STRING":
      case "OTHER":
        return threshold.getStringValueBytesThreshold();
      case "HASH":
      case "LIST":
        return threshold.getCollectionMembersThreshold();
      default:
        return threshold.getCollectionMembersThreshold();
    }
  }
}
