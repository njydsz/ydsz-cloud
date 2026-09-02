package com.njydsz.cronjob.server.core.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 下次触发时间计算器（P1-3：统一 Cron next_fire_time 计算）。
 *
 * <p>收敛 JobScanner / DefaultTaskDispatcher / JobServiceImpl 三处重复且时区语义不一致的
 * {@code nextFireTime} 实现，统一为单一入口：
 *
 * <ul>
 *   <li><b>任务级时区</b>：优先使用 {@link Job#getTimezone()}，为空时回退到默认时区 Asia/Shanghai，
 *       避免多时区部署下触发时间漂移
 *   <li><b>本地缓存</b>：缓存 key = cron + timezone（60s TTL），同一表达式在扫描周期内只解析一次；
 *       缓存 key 含时区，避免不同时区共用同一缓存导致计算结果串扰
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class NextFireTimeCalculator {

  /** 默认时区（与调度基线一致） */
  private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

  /** 缓存 TTL：60s 内同一表达式计算结果相同，避免扫描周期内重复解析 */
  private static final Duration CACHE_TTL = Duration.ofSeconds(60);

  /** 缓存条目：Key = cron + "|" + timezone */
  private static final class CacheEntry {
    final LocalDateTime calculatedAt;
    final LocalDateTime nextFireTime;

    CacheEntry(LocalDateTime calculatedAt, LocalDateTime nextFireTime) {
      this.calculatedAt = calculatedAt;
      this.nextFireTime = nextFireTime;
    }

    boolean isExpired() {
      return Duration.between(calculatedAt, LocalDateTime.now()).getSeconds() >= CACHE_TTL.getSeconds();
    }
  }

  /** 计算结果缓存 */
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>(64);

  /**
   * 计算任务的下次触发时间（优先使用任务级时区）。
   *
   * <p>按 {@code scheduleType} 分发：
   *
   * <ul>
   *   <li>{@code CRON}：按 cron 表达式 + 任务时区计算
   *   <li>{@code FIXED_RATE} / {@code FIXED_DELAY}：now + 对应间隔（由 Leader 扫描器统一驱动，
   *       支持故障转移；FIXED_DELAY 在扫描模型下近似为"触发后延迟"，如需精确"完成后再延迟"可在派发器完成回调中再推进）
   *   <li>其他（API 等）：返回 null（不自动调度）
   * </ul>
   *
   * @param job 任务定义（含调度类型、cron 表达式与可选时区）
   * @return 下次触发时间；表达式非法或类型不支持时返回 null
   */
  public LocalDateTime calculate(JobVO job) {
    if (job == null) {
      return null;
    }
    String scheduleType = job.getScheduleType();
    if ("FIXED_RATE".equalsIgnoreCase(scheduleType)) {
      return calculateFixed(job.getFixedRateMs());
    }
    if ("FIXED_DELAY".equalsIgnoreCase(scheduleType)) {
      return calculateFixed(job.getFixedDelayMs());
    }
    return calculate(job.getCronExpression(), job.getTimezone());
  }

  /**
   * 计算固定频率/固定延迟任务的下次触发时间（now + interval）。
   *
   * @param intervalMs 间隔毫秒数（必须为正）
   * @return 下次触发时间；间隔非法时返回 null
   */
  private LocalDateTime calculateFixed(Long intervalMs) {
    if (intervalMs == null || intervalMs <= 0) {
      log.warn("[NextFireTimeCalculator] 固定频率任务间隔非法, 返回 null: intervalMs={}", intervalMs);
      return null;
    }
    return LocalDateTime.now().plusNanos(TimeUnit.MILLISECONDS.toNanos(intervalMs));
  }

  /**
   * 计算指定 cron 表达式的下次触发时间（指定时区，可为 null 使用默认时区）。
   *
   * @param cronExpression cron 表达式
   * @param timezone 时区 ID（可为 null，回退 Asia/Shanghai）
   * @return 下次触发时间；表达式非法时返回 null
   */
  public LocalDateTime calculate(String cronExpression, String timezone) {
    try {
      Assert.hasText(cronExpression, "cron 表达式不能为空");
      String tz = timezone != null && !timezone.isBlank() ? timezone : DEFAULT_TIMEZONE;
      String cacheKey = cronExpression + "|" + tz;
      CacheEntry cached = cache.get(cacheKey);
      if (cached != null && !cached.isExpired()) {
        return cached.nextFireTime;
      }
      ZoneId zoneId = ZoneId.of(tz);
      CronExpression expr = CronExpression.parse(cronExpression);
      LocalDateTime now = LocalDateTime.now(zoneId);
      LocalDateTime next = expr.next(now);
      cache.put(cacheKey, new CacheEntry(LocalDateTime.now(), next));
      return next;
    } catch (Exception e) {
      log.warn(
          "[NextFireTimeCalculator] 计算 nextFireTime 失败: cron={} tz={} err={}",
          cronExpression,
          timezone,
          e.getMessage());
      return null;
    }
  }

  /** 定时清理过期的缓存条目（防止长期运行后缓存堆积不再使用的表达式）。 */
  public void cleanup() {
    cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
  }
}
