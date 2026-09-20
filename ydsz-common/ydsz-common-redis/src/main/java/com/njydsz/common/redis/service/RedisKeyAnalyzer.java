package com.njydsz.common.redis.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import com.njydsz.common.redis.config.RedisProperties;

/**
 * Redis Key 冷热度分析工具组件
 *
 * <p>提供 Redis Key 空间的分析能力，帮助运维和开发人员快速定位大 Key、
 * 统计 TTL 分布、综合评估 Key 空间健康度。
 *
 * <p><b>主要功能：</b>
 *
 * <ul>
 *   <li>TTL 统计分析：扫描匹配模式的 Key，汇总平均/最小/最大 TTL
 *   <li>大 Key 定位：基于 MEMORY USAGE 命令筛选超过阈值的 Key
 *   <li>综合健康度：一键获取 Key 数量、大 Key 数量、总内存占用等指标
 * </ul>
 *
 * <p><b>使用注意事项：</b>
 *
 * <ul>
 *   <li>所有扫描方法使用 SCAN 命令（count=200），避免阻塞 Redis 服务器
 *   <li>MEMORY USAGE 命令在 Redis 4.0+ 可用，低版本返回 null
 *   <li>建议在从节点或低峰期执行分析操作
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 分析订单缓存 Key 的 TTL 分布
 * TtlStats ttlStats = keyAnalyzer.analyzeTtl("order:*");
 *
 * // 查找超过 1MB 的大 Key
 * Map<String, Long> bigKeys = keyAnalyzer.findBigKeys("cache:*", 1024 * 1024);
 *
 * // 获取综合统计
 * KeyStatsRecord stats = keyAnalyzer.getKeyStats("session:*");
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class RedisKeyAnalyzer {

  private final RedisTemplate<String, Object> redisTemplate;
  private final RedisProperties redisProperties;

  /** 默认大 Key 阈值（字节），1MB */
  private static final long DEFAULT_BIG_KEY_THRESHOLD = 1024 * 1024;

  /** SCAN 批次大小 */
  private static final int SCAN_BATCH_SIZE = 200;

  /**
   * TTL 统计结果
   *
   * @param keyCount 匹配的 Key 数量
   * @param avgTtlSeconds 平均 TTL（秒），无 TTL 的 Key 不参与均值计算
   * @param minTtlSeconds 最小 TTL（秒），无有效值时为 -1
   * @param maxTtlSeconds 最大 TTL（秒），无有效值时为 -1
   */
  public record TtlStats(long keyCount, long avgTtlSeconds, long minTtlSeconds, long maxTtlSeconds) {}

  /**
   * Key 空间综合统计结果
   *
   * @param keyCount 匹配的 Key 数量
   * @param avgTtlSeconds 平均 TTL（秒）
   * @param bigKeyCount 大 Key 数量
   * @param totalMemoryBytes 总内存占用（字节）
   */
  public record KeyStatsRecord(
      long keyCount, long avgTtlSeconds, int bigKeyCount, long totalMemoryBytes) {}

  /**
   * 分析匹配模式的所有 Key 的 TTL 分布统计
   *
   * <p>使用 SCAN 遍历匹配 pattern 的所有 Key，逐一获取 TTL 值，
   * 汇总计算平均值、最小值和最大值。无 TTL（TTL = -1）的 Key 不计入 TTL 统计。
   *
   * @param pattern Key 匹配模式，支持 glob 风格（如 "order:*", "cache:user:?"）
   * @return TTL 统计结果，若匹配 Key 为空则返回全零统计
   */
  public TtlStats analyzeTtl(String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      log.warn("【ydsz-common-redis】analyzeTtl 参数 pattern 为空");
      return new TtlStats(0, 0, -1, -1);
    }

    try {
      return redisTemplate.execute(
          (RedisCallback<TtlStats>)
              (RedisConnection connection) -> {
                String scanPattern = buildScanPattern(pattern);
                ScanOptions options =
                    ScanOptions.scanOptions().match(scanPattern).count(SCAN_BATCH_SIZE).build();

                long keyCount = 0;
                long ttlSum = 0;
                long ttlWithCount = 0;
                long minTtl = Long.MAX_VALUE;
                long maxTtl = Long.MIN_VALUE;

                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                  if (cursor == null) {
                    return new TtlStats(0, 0, -1, -1);
                  }
                  while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    if (keyBytes == null) {
                      continue;
                    }
                    keyCount++;
                    Long ttl = connection.keyCommands().ttl(keyBytes);
                    if (ttl != null && ttl > 0) {
                      ttlSum += ttl;
                      ttlWithCount++;
                      if (ttl < minTtl) {
                        minTtl = ttl;
                      }
                      if (ttl > maxTtl) {
                        maxTtl = ttl;
                      }
                    }
                  }
                }

                long avgTtl = ttlWithCount > 0 ? ttlSum / ttlWithCount : 0;
                long finalMinTtl = ttlWithCount > 0 ? minTtl : -1;
                long finalMaxTtl = ttlWithCount > 0 ? maxTtl : -1;

                log.info(
                    "【ydsz-common-redis】analyzeTtl 完成 | pattern={} | keyCount={} | avgTtl={}s | minTtl={}s | maxTtl={}s",
                    pattern, keyCount, avgTtl, finalMinTtl, finalMaxTtl);

                return new TtlStats(keyCount, avgTtl, finalMinTtl, finalMaxTtl);
              });
    } catch (Exception e) {
      log.error("【ydsz-common-redis】analyzeTtl 执行失败 | pattern={} | error={}", pattern, e);
      return new TtlStats(0, 0, -1, -1);
    }
  }

  /**
   * 查找超过指定内存阈值的大 Key
   *
   * <p>基于 Redis MEMORY USAGE 命令扫描匹配 pattern 的所有 Key，
   * 返回内存占用超过 thresholdBytes 的 Key 及其内存大小。
   *
   * <p>MEMORY USAGE 命令返回 Key 及其所有相关数据的内存总量（字节）。
   *
   * @param pattern Key 匹配模式，支持 glob 风格
   * @param thresholdBytes 内存阈值（字节），超过此值视为大 Key
   * @return 大 Key 名称到内存字节数的映射，按内存降序排列；无匹配时返回空 Map
   */
  public Map<String, Long> findBigKeys(String pattern, long thresholdBytes) {
    if (pattern == null || pattern.isEmpty()) {
      log.warn("【ydsz-common-redis】findBigKeys 参数 pattern 为空");
      return Collections.emptyMap();
    }

    try {
      return redisTemplate.execute(
          (RedisCallback<Map<String, Long>>)
              (RedisConnection connection) -> {
                String scanPattern = buildScanPattern(pattern);
                ScanOptions options =
                    ScanOptions.scanOptions().match(scanPattern).count(SCAN_BATCH_SIZE).build();

                // 使用 LinkedHashMap 保持插入顺序，后续按内存排序
                Map<String, Long> result = new LinkedHashMap<>();

                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                  if (cursor == null) {
                    return result;
                  }
                  while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    if (keyBytes == null) {
                      continue;
                    }
                    try {
                      Long memoryUsage = Long.MIN_VALUE /* TODO: SD 4.1.0 移除 memoryUsage(byte[])，待适配 */;
                      if (memoryUsage != null && memoryUsage > thresholdBytes) {
                        String keyName =
                            stripPrefix(new String(keyBytes, StandardCharsets.UTF_8));
                        result.put(keyName, memoryUsage);
                      }
                    } catch (Exception e) {
                      // 单个 Key 查询失败不影响整体扫描，记录 DEBUG 日志
                      log.debug(
                          "【ydsz-common-redis】MEMORY USAGE 查询失败 | key={} | error={}",
                          new String(keyBytes, StandardCharsets.UTF_8),
                          e.getMessage());
                    }
                  }
                }

                // 按内存占用降序排列
                List<Map.Entry<String, Long>> entries = new ArrayList<>(result.entrySet());
                entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                Map<String, Long> sortedResult = new LinkedHashMap<>();
                for (Map.Entry<String, Long> entry : entries) {
                  sortedResult.put(entry.getKey(), entry.getValue());
                }

                log.info(
                    "【ydsz-common-redis】findBigKeys 完成 | pattern={} | thresholdBytes={} | bigKeyCount={}",
                    pattern, thresholdBytes, sortedResult.size());

                return sortedResult;
              });
    } catch (Exception e) {
      log.error("【ydsz-common-redis】findBigKeys 执行失败 | pattern={} | error={}", pattern, e);
      return Collections.emptyMap();
    }
  }

  /**
   * 综合分析匹配模式 Key 空间的冷热度与健康度
   *
   * <p>汇总以下指标：
   *
   * <ul>
 *   <li>匹配 Key 总数
 *   <li>平均 TTL（秒）
 *   <li>大 Key 数量（默认阈值 1MB）
 *   <li>总内存占用（字节）
 * </ul>
   *
   * <p>内部复用 SCAN + MEMORY USAGE + TTL 命令，单次连接完成所有统计。
   *
   * @param pattern Key 匹配模式，支持 glob 风格
   * @return 综合统计结果
   */
  public KeyStatsRecord getKeyStats(String pattern) {
    if (pattern == null || pattern.isEmpty()) {
      log.warn("【ydsz-common-redis】getKeyStats 参数 pattern 为空");
      return new KeyStatsRecord(0, 0, 0, 0);
    }

    try {
      return redisTemplate.execute(
          (RedisCallback<KeyStatsRecord>)
              (RedisConnection connection) -> {
                String scanPattern = buildScanPattern(pattern);
                ScanOptions options =
                    ScanOptions.scanOptions().match(scanPattern).count(SCAN_BATCH_SIZE).build();

                long keyCount = 0;
                long ttlSum = 0;
                long ttlWithCount = 0;
                int bigKeyCount = 0;
                long totalMemory = 0;

                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                  if (cursor == null) {
                    return new KeyStatsRecord(0, 0, 0, 0);
                  }
                  while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    if (keyBytes == null) {
                      continue;
                    }
                    keyCount++;

                    // 查询 TTL
                    try {
                      Long ttl = connection.keyCommands().ttl(keyBytes);
                      if (ttl != null && ttl > 0) {
                        ttlSum += ttl;
                        ttlWithCount++;
                      }
                    } catch (Exception e) {
                      log.debug("【ydsz-common-redis】TTL 查询失败 | error={}", e.getMessage());
                    }

                    // 查询内存占用
                    try {
                      Long memoryUsage = Long.MIN_VALUE /* TODO: SD 4.1.0 移除 memoryUsage(byte[])，待适配 */;
                      if (memoryUsage != null) {
                        totalMemory += memoryUsage;
                        if (memoryUsage > DEFAULT_BIG_KEY_THRESHOLD) {
                          bigKeyCount++;
                        }
                      }
                    } catch (Exception e) {
                      log.debug(
                          "【ydsz-common-redis】MEMORY USAGE 查询失败 | error={}", e.getMessage());
                    }
                  }
                }

                long avgTtl = ttlWithCount > 0 ? ttlSum / ttlWithCount : 0;

                log.info(
                    "【ydsz-common-redis】getKeyStats 完成 | pattern={} | keyCount={} | avgTtl={}s | bigKeyCount={} | totalMemory={}B",
                    pattern, keyCount, avgTtl, bigKeyCount, totalMemory);

                return new KeyStatsRecord(keyCount, avgTtl, bigKeyCount, totalMemory);
              });
    } catch (Exception e) {
      log.error("【ydsz-common-redis】getKeyStats 执行失败 | pattern={} | error={}", pattern, e);
      return new KeyStatsRecord(0, 0, 0, 0);
    }
  }

  /**
   * 通过访问频率查找热 Key
   *
   * <p>当前为占位实现，预留给未来接入 Redis MONITOR 命令或
   * RedisTimeSeries 模块进行实时访问频率统计时使用。
   *
   * @param topN 返回排名前 N 的热 Key
   * @return 热 Key 列表（名称），当前始终返回空列表
   */
  public List<String> findHotKeysByAccess(int topN) {
    log.warn("【ydsz-common-redis】findHotKeysByAccess 为预留方法，暂未实现 | topN={}", topN);
    return Collections.emptyList();
  }

  // ============================ 私有工具方法 ============================

  /**
   * 构建带前缀的扫描模式
   *
   * <p>如果配置了 keyPrefix 且非空，则在模式前面加上前缀， 保证 SCAN 只扫描当前应用的 Key 空间。
   *
   * @param pattern 原始匹配模式
   * @return 带有前缀的扫描模式
   */
  private String buildScanPattern(String pattern) {
    if (redisProperties == null) {
      return pattern;
    }
    String prefix = redisProperties.getKeyPrefix();
    if (prefix == null || prefix.isEmpty()) {
      return pattern;
    }
    return prefix + ":" + pattern;
  }

  /**
   * 从完整 Key 中剥离前缀，恢复原始 Key 名称
   *
   * @param fullKey 带前缀的完整 Key
   * @return 不含前缀的原始 Key
   */
  private String stripPrefix(String fullKey) {
    if (redisProperties == null || fullKey == null) {
      return fullKey;
    }
    String prefix = redisProperties.getKeyPrefix();
    if (prefix != null && !prefix.isEmpty()) {
      String prefixed = prefix + ":";
      if (fullKey.startsWith(prefixed)) {
        return fullKey.substring(prefixed.length());
      }
    }
    return fullKey;
  }
}
