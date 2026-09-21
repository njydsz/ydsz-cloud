package com.njydsz.common.redis.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.redis.constant.RedisScriptConstants;

/**
 * 分布式序号生成器（基于 Redis Lua 脚本原子操作）
 *
 * <p>提供基于 {@code INCR + EXPIRE} 原子操作的按日序列号生成能力，按业务键和日期生成递增序号，每日自动重置。
 *
 * <p><b>Key 规则：</b>{@code idgen:seq:{businessKey}:{yyyyMMdd}}，TTL 自动设为当日剩余秒数。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 日序号（自动使用当日日期）
 * long seq1 = idGenerator.nextId("order");
 *
 * // 日序号（指定起始日期，epoch 毫秒 -> yyyyMMdd）
 * long seq2 = idGenerator.nextId("order", 1704067200000L);
 *
 * // 查询当前序号（不递增）
 * long current = idGenerator.getCurrentCount("order");
 *
 * // 重置当日序号
 * idGenerator.reset("order");
 * }</pre>
 *
 * <p><b>线程安全：</b>所有方法均为线程安全，底层基于 Redis 单线程 + Lua 脚本原子性保证。
 *
 * <p>如需 Snowflake 风格 64-bit 全局唯一 ID，请使用 {@code ydsz-common-util} 中的
 * {@code com.njydsz.common.util.id.SnowflakeIdGenerator}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RequiredArgsConstructor
public class RedisIdGenerator {

  /** Key 前缀 */
  private static final String KEY_PREFIX = "idgen";

  /** 日序号 Key 子前缀 */
  private static final String SEQ_SUB_PREFIX = "seq";

  /** 日期格式化器（yyyyMMdd） */
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  /** 东八区（中国时区） */
  private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

  /** 编译后的 Lua 脚本缓存 */
  private final ConcurrentHashMap<String, DefaultRedisScript<?>> scriptCache = new ConcurrentHashMap<>();

  private final RedisTemplate<String, Object> redisTemplate;

  // ======================== 日序号方法 ========================

  /**
   * 获取指定业务键的当日序列号（基于当日日期自动管理）。
   *
   * <p>Key 格式为 {@code idgen:seq:{businessKey}:{yyyyMMdd}}，TTL 自动设为当日剩余秒数，
   * 次日零时 key 自动过期，重新从 1 开始计数。
   *
   * @param businessKey 业务标识（如 "order"、"refund"），不可为 null
   * @return 当前序列号（>=1），异常时返回 -1
   */
  public long nextId(String businessKey) {
    return nextId(businessKey, System.currentTimeMillis());
  }

  /**
   * 获取指定业务键基于起始日期的序列号。
   *
   * <p>将 {@code startDate}（epoch 毫秒）转换为 {@code yyyyMMdd} 日期字符串，作为 Key 后缀。
   * 常用于需要按指定日期维度生成序号的场景（如补单、批处理）。
   *
   * <p>TTL 计算规则：
   *
   * <ul>
   *   <li>指定日期为今天：设为当日剩余秒数
   *   <li>指定日期为未来：设为距今秒数 + 86400
   *   <li>指定日期为过去：设为 60 秒（用于查询近期 key）
   * </ul>
   *
   * @param businessKey 业务标识，不可为 null
   * @param startDate 起始日期（epoch 毫秒），用于决定日期后缀和 TTL
   * @return 当前序列号（>=1），异常时返回 -1
   */
  public long nextId(String businessKey, long startDate) {
    if (businessKey == null || businessKey.isEmpty()) {
      log.warn("【RedisIdGenerator】nextId 参数非法 | businessKey 为空");
      return -1L;
    }
    try {
      String dateStr = formatDate(startDate);
      String key = buildSeqKey(businessKey, dateStr);
      long ttlSeconds = computeTtlForDate(startDate);
      DefaultRedisScript<Long> script =
          getOrCreateScript(
              "daily_seq_incr", RedisScriptConstants.DAILY_SEQ_INCR_LUA, Long.class);
      Object rawResult =
          redisTemplate.execute(
              script, Collections.singletonList(key), String.valueOf(ttlSeconds));
      return rawResult instanceof Number ? ((Number) rawResult).longValue() : -1L;
    } catch (Exception e) {
      log.error("【RedisIdGenerator】nextId 异常 | businessKey={} | startDate={} | error={}",
          businessKey, startDate, e.getMessage(), e);
      return -1L;
    }
  }

  // ======================== Snowflake 方法 ========================

  /**
   * 基于类 Snowflake 算法生成分布式唯一 ID。
   *
   * <p>ID 结构（64-bit long）：
   *
   * <pre>
   *   ((timestampMs - EPOCH) &lt;&lt; 22) | (workerId &lt;&lt; 12) | sequence
   * </pre>
   *
   * <p>序列号在同一毫秒内自增，溢出（>4095 /ms/worker）时自动等待下一毫秒重试。
   * 最大吞吐：409,6000 IDs/s/worker。
   *
   * <p><b>注意：</b>系统时钟回拨时不会主动检测，建议业务方配合 NTP 确保时钟单调递增。
   *
   * @param workerId Worker 标识（0 ~ 1023），用于多实例区分会话
   * @return 64-bit Snowflake ID，异常时返回 -1
   */
  public long nextSnowflakeId(long workerId) {
    if (workerId < 0 || workerId >= (1L << (int) SNOWFLAKE_WORKER_BITS)) {
      log.warn("【RedisIdGenerator】nextSnowflakeId 参数非法 | workerId={} 超出有效范围 [0, {})",
          workerId, (1L << (int) SNOWFLAKE_WORKER_BITS));
      return -1L;
    }
    try {
      String key = buildSnowflakeKey(workerId);
      int maxRetries = 10;
      for (int retry = 0; retry < maxRetries; retry++) {
        long now = System.currentTimeMillis();
        DefaultRedisScript<List> script =
            getOrCreateScript(
                "snowflake_seq", RedisScriptConstants.SNOWFLAKE_SEQ_LUA, List.class);
        Object rawResult =
            redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(now),
                String.valueOf(SNOWFLAKE_MAX_SEQ));
        List<Long> result = castToLongList(rawResult);
        if (result.isEmpty() || result.size() < 2) {
          log.warn("【RedisIdGenerator】Snowflake 脚本返回异常 | workerId={}", workerId);
          return -1L;
        }
        long ts = result.get(0);
        long seq = result.get(1);
        if (ts == -1L && seq == -1L) {
          // 序列溢出，等待 1ms 后重试
          Thread.sleep(1L);
          continue;
        }
        return composeSnowflakeId(ts, workerId, seq);
      }
      log.error("【RedisIdGenerator】Snowflake 连续重试 {} 次仍溢出 | workerId={}", maxRetries, workerId);
      return -1L;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("【RedisIdGenerator】nextSnowflakeId 中断 | workerId={}", workerId, e);
      return -1L;
    } catch (Exception e) {
      log.error("【RedisIdGenerator】nextSnowflakeId 异常 | workerId={} | error={}",
          workerId, e.getMessage(), e);
      return -1L;
    }
  }

  // ======================== 查询与重置 ========================

  /**
   * 查询指定业务键的当前序列号（不递增）。
   *
   * <p>查询当日 Key 对应的计数值，Key 不存在时返回 0。
   *
   * @param businessKey 业务标识，不可为 null
   * @return 当前序列号（>=0），key 不存在返回 0，异常返回 -1
   */
  public long getCurrentCount(String businessKey) {
    if (businessKey == null || businessKey.isEmpty()) {
      log.warn("【RedisIdGenerator】getCurrentCount 参数非法 | businessKey 为空");
      return -1L;
    }
    try {
      String dateStr = formatDate(System.currentTimeMillis());
      String key = buildSeqKey(businessKey, dateStr);
      DefaultRedisScript<Long> script =
          getOrCreateScript("get_count", RedisScriptConstants.GET_COUNT_LUA, Long.class);
      Object rawResult =
          redisTemplate.execute(script, Collections.singletonList(key));
      return rawResult instanceof Number ? ((Number) rawResult).longValue() : 0L;
    } catch (Exception e) {
      log.error("【RedisIdGenerator】getCurrentCount 异常 | businessKey={} | error={}",
          businessKey, e.getMessage(), e);
      return -1L;
    }
  }

  /**
   * 重置指定业务键的当日序列号。
   *
   * <p>删除当日序号 Key，使下次调用 {@link #nextId(String)} 时重新从 1 开始计数。
   *
   * @param businessKey 业务标识，不可为 null
   */
  public void reset(String businessKey) {
    if (businessKey == null || businessKey.isEmpty()) {
      log.warn("【RedisIdGenerator】reset 参数非法 | businessKey 为空");
      return;
    }
    try {
      String dateStr = formatDate(System.currentTimeMillis());
      String key = buildSeqKey(businessKey, dateStr);
      redisTemplate.delete(key);
      log.info("【RedisIdGenerator】reset 序列号成功 | businessKey={} | key={}", businessKey, key);
    } catch (Exception e) {
      log.warn("【RedisIdGenerator】reset 异常 | businessKey={} | error={}",
          businessKey, e.getMessage(), e);
    }
  }

  // ======================== 私有方法 ========================

  /**
   * 组合 Snowflake ID
   *
   * @param timestampMs 毫秒时间戳
   * @param workerId Worker ID
   * @param sequence 序列号
   * @return 64-bit Snowflake ID
   */
  private long composeSnowflakeId(long timestampMs, long workerId, long sequence) {
    long tsDelta = timestampMs - SNOWFLAKE_EPOCH;
    return (tsDelta << (int) SNOWFLAKE_TIMESTAMP_SHIFT)
        | (workerId << (int) SNOWFLAKE_WORKER_SHIFT)
        | sequence;
  }

  /**
   * 构建日序号 Key
   *
   * @param businessKey 业务标识
   * @param dateStr 日期字符串（yyyyMMdd）
   * @return 完整 Redis Key
   */
  private String buildSeqKey(String businessKey, String dateStr) {
    return String.join(":", KEY_PREFIX, SEQ_SUB_PREFIX, businessKey, dateStr);
  }

  /**
   * 构建 Snowflake Key
   *
   * @param workerId Worker ID
   * @return 完整 Redis Key
   */
  private String buildSnowflakeKey(long workerId) {
    return String.join(":", KEY_PREFIX, SNOWFLAKE_SUB_PREFIX, String.valueOf(workerId));
  }

  /**
   * 将 epoch 毫秒转换为 yyyyMMdd 日期字符串（东八区）
   *
   * @param epochMs epoch 毫秒
   * @return yyyyMMdd 格式日期字符串
   */
  private String formatDate(long epochMs) {
    return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(epochMs), ZONE_CN)
        .format(DATE_FORMAT);
  }

  /**
   * 计算指定日期对应的 TTL 秒数
   *
   * <p>规则：
   *
   * <ul>
   *   <li>今天：当日剩余秒数
   *   <li>未来日期：距今秒数 + 86400
   *   <li>过去日期：60 秒
   * </ul>
   *
   * @param epochMs 目标日期（epoch 毫秒）
   * @return 建议 TTL（秒），最小 60
   */
  private long computeTtlForDate(long epochMs) {
    try {
      LocalDateTime targetDateTime =
          LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), ZONE_CN);
      LocalDate targetDate = targetDateTime.toLocalDate();
      LocalDate today = LocalDate.now(ZONE_CN);

      if (targetDate.equals(today)) {
        // 今天：到当天结束的秒数
        LocalDateTime endOfDay = targetDate.plusDays(1).atStartOfDay();
        long secondsLeft = ChronoUnit.SECONDS.between(LocalDateTime.now(ZONE_CN), endOfDay);
        return Math.max(60L, secondsLeft);
      } else if (targetDate.isAfter(today)) {
        // 未来日期：到目标日期结束的秒数
        LocalDateTime endOfTargetDay = targetDate.plusDays(1).atStartOfDay();
        long secondsUntil = ChronoUnit.SECONDS.between(LocalDateTime.now(ZONE_CN), endOfTargetDay);
        return Math.max(60L, secondsUntil);
      } else {
        // 过去日期：保留 60 秒供查询
        return 60L;
      }
    } catch (Exception e) {
      return 86400L;
    }
  }

  /**
   * 将 Redis 脚本执行结果安全转换为 List<Long>
   *
   * @param rawResult Redis 执行返回的原始对象
   * @return 转换后的 Long 列表，无法转换时返回空列表
   */
  @SuppressWarnings("unchecked")
  private static List<Long> castToLongList(Object rawResult) {
    if (rawResult instanceof List<?> list) {
      List<Long> result = new java.util.ArrayList<>(list.size());
      for (Object item : list) {
        if (item instanceof Number num) {
          result.add(num.longValue());
        } else {
          result.add(0L);
        }
      }
      return result;
    }
    return Collections.emptyList();
  }

  /**
   * 获取或编译 Lua 脚本（线程安全，懒编译）
   *
   * @param name 脚本标识名
   * @param scriptText 脚本文本
   * @param returnType 返回类型
   * @return 编译后的 DefaultRedisScript 实例
   */
  @SuppressWarnings("unchecked")
  private <T> DefaultRedisScript<T> getOrCreateScript(
      String name, String scriptText, Class<T> returnType) {
    return (DefaultRedisScript<T>)
        scriptCache.computeIfAbsent(
            name,
            k -> {
              DefaultRedisScript<T> script = new DefaultRedisScript<>();
              script.setScriptText(scriptText);
              script.setResultType(returnType);
              return script;
            });
  }
}
