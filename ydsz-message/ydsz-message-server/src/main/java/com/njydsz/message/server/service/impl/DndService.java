package com.njydsz.message.server.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * P2-14: 用户时区感知 DND（Do Not Disturb）服务。
 *
 * <p>全模块唯一的 DND 时段决策服务，统一提供：
 *
 * <ul>
 *   <li>Redis 持久化的用户级 DND 时段配置（如 22:00-08:00 Asia/Shanghai）
 *   <li>本地缓存加速（5 分钟 TTL）
 *   <li>跨天窗口判断工具方法 {@link #isInWindow} 与 {@link #resolveWindowEnd}
 *   <li>统一决策入口 {@link #evaluate(String, String, String)}
 * </ul>
 *
 * <p>Redis Key 格式：{@code dnd:{userId}} → "22:00-08:00 Asia/Shanghai"
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DndService {

  private final RedisStringOps redisStringOps;

  /** DND Key 前缀 */
  private static final String DND_KEY_PREFIX = "dnd:";

  /** 默认时区 */
  private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

  /** 本地缓存（减少 Redis 访问） */
  private final ConcurrentMap<String, CachedDndConfig> configCache = new ConcurrentHashMap<>();

  /** D-3: 缓存过期时间（毫秒），默认 5 分钟 */
  private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

  /**
   * DND 决策结果枚举。
   *
   * <p>统一描述一次 DND 决策的行动建议。
   */
  public enum DndDecision {
    /** 放行：不在 DND 窗口内，或配置缺失 */
    ALLOW,
    /** 延迟：在 DND 窗口内，应延迟到 {@code deferUntil} 后发送 */
    DEFER,
    /** 丢弃：延迟时间超过调用方允许的最大延迟 */
    DROP
  }

  /**
   * DND 决策结果（行动建议 + 延迟目标时间）。
   *
     * @param decision DND 决策类型（ALLOW / DEFER / DROP）
   * @param deferUntil 延迟目标时间（仅 DEFER 时非 null）
   */
  public record DndResult(DndDecision decision, LocalDateTime deferUntil) {
    /** 放行结果单例。 */
    public static final DndResult ALLOW = new DndResult(DndDecision.ALLOW, null);

    /**
     * 构造延迟结果。
     *
     *
     * @param deferUntil 延迟发送的目标时间
     * @return 包含 DEFER 决策和延迟目标时间的结果
     */
    public static DndResult defer(LocalDateTime deferUntil) {
      return new DndResult(DndDecision.DEFER, deferUntil);
    }

    /**
     * 构造丢弃结果。
     *
     * @return DROP 结果
     */
    public static DndResult drop() {
      return new DndResult(DndDecision.DROP, null);
    }
  }

  /**
   * 统一 DND 决策入口。
   *
   * <p>综合 Redis 免打扰配置与消息优先级给出决策：URGENT 消息始终放行； 在窗口内时根据相对当前时间与 {@code maxDeferSeconds} 的关系返回 DEFER 或
   * DROP。
   *
   * @param userId 用户 ID
   * @param channel 通道标识（仅用于日志，不影响决策）
   * @param priority 消息优先级（URGENT 始终放行）
   * @return DND 决策结果
   */
  public DndResult evaluate(String userId, String channel, String priority) {
    return evaluate(userId, channel, priority, Long.MAX_VALUE);
  }

  /**
   * 统一 DND 决策入口（含最大延迟限制）。
   *
   * @param userId 用户 ID
   * @param channel 通道标识
   * @param priority 消息优先级
   * @param maxDeferSeconds 允许的最大延迟秒数，超过即 DROP
   * @return DND 决策结果
   */
  public DndResult evaluate(String userId, String channel, String priority, long maxDeferSeconds) {
    if ("URGENT".equalsIgnoreCase(priority)) {
      return DndResult.ALLOW;
    }
    DndConfig config = getDndConfig(userId);
    if (config == null) {
      return DndResult.ALLOW;
    }
    ZoneId zoneId = ZoneId.of(config.timezone);
    LocalDateTime nowZoned = ZonedDateTime.now(zoneId).toLocalDateTime();
    LocalTime now = nowZoned.toLocalTime();
    if (!isInWindow(now, config.startTime, config.endTime)) {
      return DndResult.ALLOW;
    }
    // 在 DND 窗口内，计算结束时间
    LocalDateTime windowEnd = resolveWindowEnd(nowZoned, config.startTime, config.endTime);
    long deferSeconds = Duration.between(nowZoned, windowEnd).getSeconds();
    if (deferSeconds > maxDeferSeconds) {
      log.info(
          "[DND] 延迟超过阈值,丢弃: userId={} channel={} defer={}s max={}s",
          userId,
          channel,
          deferSeconds,
          maxDeferSeconds);
      return DndResult.drop();
    }
    log.info(
        "[DND] 消息在免打扰时段内,延迟发送: userId={} channel={} now={} window={}~{} tz={} deferUntil={}",
        userId,
        channel,
        now,
        config.startTime,
        config.endTime,
        config.timezone,
        windowEnd);
    return DndResult.defer(windowEnd);
  }

  /**
   * 保留向后兼容的便捷方法：检查消息是否应被 DND 延迟。
   *
   * @param userId 用户 ID
   * @param priority 消息优先级
   * @return true 表示在 DND 时段内且应延迟，false 表示放行
   */
  public boolean shouldDelay(String userId, String priority) {
    DndResult result = evaluate(userId, "legacy", priority);
    return result.decision() == DndDecision.DEFER;
  }

  /**
   * 设置用户 DND 配置。
   *
   * @param userId 用户 ID
   * @param startTime 开始时间（HH:mm）
   * @param endTime 结束时间（HH:mm）
   * @param timezone 时区 ID
   */
  public void setDnd(String userId, String startTime, String endTime, String timezone) {
    String value =
        startTime + "-" + endTime + " " + (timezone != null ? timezone : DEFAULT_TIMEZONE);
    redisStringOps.set(DND_KEY_PREFIX + userId, value);
    // 更新本地缓存
    configCache.put(userId, new CachedDndConfig(parseConfig(value), System.currentTimeMillis()));
    log.info(
        "[DND] 用户免打扰配置已设置: userId={} window={}~{} tz={}", userId, startTime, endTime, timezone);
  }

  /**
   * 移除用户 DND 配置。
   *
   * @param userId 用户 ID
   */
  public void removeDnd(String userId) {
    redisStringOps.del(DND_KEY_PREFIX + userId);
    configCache.remove(userId);
    log.info("[DND] 用户免打扰配置已移除: userId={}", userId);
  }

  /**
   * 获取用户 DND 配置（带本地缓存）。
   *
   * @param userId 用户 ID
   * @return DND 配置，null 表示未设置
   */
  private DndConfig getDndConfig(String userId) {
    CachedDndConfig cached = configCache.get(userId);
    // D-3: 检查缓存是否过期
    if (cached != null && (System.currentTimeMillis() - cached.cachedAt) < CACHE_TTL_MS) {
      return cached.config;
    }
    // 缓存过期或不存在，从 Redis 加载
    String value = redisStringOps.get(DND_KEY_PREFIX + userId, String.class);
    if (value == null || value.isBlank()) {
      configCache.remove(userId);
      return null;
    }
    DndConfig config = parseConfig(value);
    if (config != null) {
      configCache.put(userId, new CachedDndConfig(config, System.currentTimeMillis()));
    }
    return config;
  }

  /**
   * 解析 DND 配置字符串。
   *
   * @param value 配置字符串（格式：{@code HH:mm-HH:mm timezone}）
   * @return DND 配置对象
   */
  private DndConfig parseConfig(String value) {
    try {
      String[] parts = value.split(" ");
      String[] times = parts[0].split("-");
      String tz = parts.length > 1 ? parts[1] : DEFAULT_TIMEZONE;
      return new DndConfig(LocalTime.parse(times[0]), LocalTime.parse(times[1]), tz);
    } catch (Exception e) {
      log.warn("[DND] 配置解析失败: value={} err={}", value, e.getMessage(), e);
      return null;
    }
  }

  /**
   * 检查当前时间是否在 DND 窗口内。
   *
   * <p>支持跨天窗口（如 22:00-08:00）。
   *
   * @param now 当前时间
   * @param startTime 窗口开始时间
   * @param endTime 窗口结束时间
   * @return true 表示在窗口内
   */
  public static boolean isInWindow(LocalTime now, LocalTime startTime, LocalTime endTime) {
    if (startTime.isBefore(endTime)) {
      // 同一天内（如 09:00-18:00）
      return !now.isBefore(startTime) && now.isBefore(endTime);
    } else {
      // 跨天（如 22:00-08:00）
      return !now.isBefore(startTime) || now.isBefore(endTime);
    }
  }

  /**
   * 计算 DND 窗口结束时间（下次可发送时间）。
   *
   * <p>纯工具方法，无状态，线程安全。供 PreferenceHandler 等场景复用， 消除重复的跨天窗口结束时间计算逻辑。
   *
   * @param now 当前时间
   * @param startTime 窗口开始时间
   * @param endTime 窗口结束时间
   * @return 窗口结束的 LocalDateTime
   */
  public static LocalDateTime resolveWindowEnd(
      LocalDateTime now, LocalTime startTime, LocalTime endTime) {
    LocalDateTime todayEnd = now.toLocalDate().atTime(endTime);
    if (startTime.isBefore(endTime)) {
      // 同天：若当前已过今日结束时间，则窗口结束为明日此时（不应发生）
      return !now.isBefore(todayEnd) ? todayEnd.plusDays(1) : todayEnd;
    } else {
      // 跨天：若当前在凌晨 0 点到结束时间之间，今日结束即可；否则明日结束
      return now.toLocalTime().isBefore(endTime) ? todayEnd : todayEnd.plusDays(1);
    }
  }

  /** DND 配置内部类 */
  private record DndConfig(LocalTime startTime, LocalTime endTime, String timezone) {}

  /** D-3: 带时间戳的缓存包装类 */
  private record CachedDndConfig(DndConfig config, long cachedAt) {}
}
