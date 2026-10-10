package com.njydsz.message.server.consumer;


import com.njydsz.common.locales.util.I18n;import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.thread.factory.InternalExecutorFactory;
import com.njydsz.common.util.BloomFilter;

/**
 * 基于 BloomFilter + Redis 的消息去重前置过滤器，降低重复消息处理的 Redis 查询压力。
 *
 * <p>在消费者处理消息前做第一层去重判定：本地 BloomFilter 作为快速路径（减少 Redis 调用），
 * Redis 共享 Set 作为误判兜底（多实例部署时各 BloomFilter 互相独立，需 Redis 保证一致性）。
 * BloomFilter 双缓冲滑动窗口设计（窗口可配，默认 60s 翻转），读写分离避免并发竞争。
 * 降级策略：Redis 异常时仅使用本地 BloomFilter（fail-open）。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.message.consumer",
    name = "bloom-filter-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class BloomFilterDeduplicator {
  /** 默认去重 TTL（秒） */
  private static final int DEFAULT_DEDUP_TTL_SECONDS = 60;


  /** Redis 共享 Set 名称前缀（多实例部署时共享） */
  private static final String REDIS_DEDUP_SET_PREFIX = "msg:bloom:dedup:";

  /** RedisTemplate（用于多实例共享去重） */
  private final RedisStringOps redisStringOps;

  /** Redis 去重 TTL（秒），与本地 BloomFilter 窗口一致 */
  private final int redisDedupTtlSeconds;

  /** 当前活跃的 BloomFilter（写入新条目） */
  private final AtomicReference<BloomFilter> activeFilter = new AtomicReference<>();

  /** 上一周期的 BloomFilter（保留用于防止边界误判） */
  private final AtomicReference<BloomFilter> previousFilter =
      new AtomicReference<>();

  /** 窗口翻转调度器（单线程，守护线程，由 InternalExecutorFactory 统一管理） */
  private final ScheduledExecutorService scheduler =
      InternalExecutorFactory.newSingleThreadScheduledPool("message-bloom-rotator");

  /** 预期最大消息数/窗口 */
  @Value("${ydsz.message.consumer.bloom-filter-capacity:1000000}")
  private int expectedInsertions;

  /** 误判率 */
  @Value("${ydsz.message.consumer.bloom-filter-fpp:0.001}")
  private double falsePositiveProbability;

  /** 窗口翻转间隔（秒） */
  @Value("${ydsz.message.consumer.bloom-filter-rotate-seconds:60}")
  private int rotateSeconds;

  /** 当前周期已添加条目数（监控用） */
  private volatile long currentWindowCount;

  /** 当前窗口创建时间戳（毫秒），用于计算窗口年龄 */
  private final AtomicLong windowCreatedAt = new AtomicLong(0);

  /** 累计命中次数（监控用） */
  private volatile long totalHits;

  /** Redis 降级模式标志（Redis 异常时切换） */
  private volatile boolean redisDegraded;

  /**
   * 构造 BloomFilter 去重器。
   *
   * @param redisStringOps Redis String 操作组件
   */
  @Autowired
  public BloomFilterDeduplicator(RedisStringOps redisStringOps) {
    this.redisStringOps = redisStringOps;
    this.redisDedupTtlSeconds = DEFAULT_DEDUP_TTL_SECONDS;
  }

  @PostConstruct
  public void init() {
    activeFilter.set(createFilter());
    previousFilter.set(createFilter());
    windowCreatedAt.set(System.currentTimeMillis());
    scheduler.scheduleAtFixedRate(
        this::rotateFilter, rotateSeconds, rotateSeconds, TimeUnit.SECONDS);
    log.info(
        "[BloomFilter] 初始化完成: capacity={} fpp={} rotate={}s redisTtl={}s",
        expectedInsertions,
        falsePositiveProbability,
        rotateSeconds,
        redisDedupTtlSeconds);
  }

  @PreDestroy
  public void destroy() {
    scheduler.shutdownNow();
  }

  /**
   * 检查消息是否已经处理过（BloomFilter 快速判定）。
   *
   * @param msgId 消息 ID
   * @return true 表示消息可能存在（需要进一步查 Redis），false 表示一定不存在（新消息）
   */
  public boolean mightContain(String msgId) {
    if (msgId == null || msgId.isBlank()) {
      return false;
    }

    BloomFilter active = activeFilter.get();
    BloomFilter previous = previousFilter.get();

    // 先查当前窗口，再查上一窗口（防止边界误判）
    boolean localMightContain = active != null && active.mightContain(msgId);
    if (!localMightContain && previous != null) {
      localMightContain = previous.mightContain(msgId);
    }

    // 本地 BloomFilter 判定"一定不存在" → 直接返回 false（快速路径）
    if (!localMightContain) {
      return false;
    }

    // 本地 BloomFilter 判定"可能存在" → 查 Redis 确认（误判兜底，多实例一致）
    if (!redisDegraded) {
      try {
        String redisKey = REDIS_DEDUP_SET_PREFIX + msgId;
        boolean exists = redisStringOps.hasKey(redisKey);
        if (exists) {
          // Redis 确认存在 → 确实重复
          totalHits++;
          return true;
        }
        // BloomFilter 误判：本地可能存在，但 Redis 中不存在
        return false;
      } catch (Exception e) {
        // Redis 异常时降级为纯本地 BloomFilter（fail-open）
        redisDegraded = true;
        log.warn(I18n.message("message.log.other.BloomFilter_Redis_{}.ed4d6f"), e.getMessage(), e);
        totalHits++;
        return true;
      }
    }

    // 已降级：仅依赖本地 BloomFilter
    totalHits++;
    return true;
  }

  /**
   * 将已处理的消息记录到 BloomFilter。
   *
   * @param msgId 消息 ID
   */
  public void put(String msgId) {
    if (msgId == null || msgId.isBlank()) {
      return;
    }

    BloomFilter active = activeFilter.get();
    if (active != null) {
      active.put(msgId);
      currentWindowCount++;
    }

    // 同步写入 Redis（多实例共享去重）
    if (!redisDegraded) {
      try {
        String redisKey = REDIS_DEDUP_SET_PREFIX + msgId;
        redisStringOps.set(redisKey, "1", redisDedupTtlSeconds);
      } catch (Exception e) {
        // Redis 异常时降级为纯本地 BloomFilter（fail-open）
        redisDegraded = true;
        log.warn(I18n.message("message.log.other.BloomFilter_Redis_{}.fc1563"), e.getMessage(), e);
      }
    }
  }

  /**
   * 获取当前窗口统计信息。
   *
   * @return 统计信息字符串
   */
  public String stats() {
    BloomFilter active = activeFilter.get();
    return String.format(
        "windowCount=%d totalHits=%d activeSize=%s",
        currentWindowCount, totalHits, active != null ? "active" : "null");
  }

  /** 翻转 BloomFilter 窗口：当前变历史，创建新的当前。 */
  private void rotateFilter() {
    try {
      BloomFilter newFilter = createFilter();
      BloomFilter oldActive = activeFilter.getAndSet(newFilter);
      previousFilter.set(oldActive);
      currentWindowCount = 0;
      windowCreatedAt.set(System.currentTimeMillis());
      log.debug(I18n.message("message.log.other.BloomFilter.00b561"));
    } catch (Exception e) {
      log.warn(I18n.message("message.log.other.BloomFilter_{}.dedc18"), e.getMessage(), e);
    }
  }

  /**
   * 创建新的 BloomFilter 实例。
   *
   * <p>YDIZ-COMMON-048: 使用 ydsz-common-util 零依赖 BloomFilter 替代 Guava。
   *
   * @return 配置好预期插入数和误判率的新 BloomFilter 实例
   */
  private BloomFilter createFilter() {
    return new BloomFilter(expectedInsertions, falsePositiveProbability);
  }

  /**
   * 获取当前已添加条目数（测试用）。
   *
   * @return 条目数
   */
  public long getCurrentWindowCount() {
    return currentWindowCount;
  }

  /**
   * 累计命中次数（测试用）。
   *
   * @return 命中次数
   */
  public long getTotalHits() {
    return totalHits;
  }

  /**
   * 获取预期最大插入条目数。
   *
   * @return 预期插入条目数
   */
  public int getExpectedInsertions() {
    return expectedInsertions;
  }

  /**
   * 获取误判率。
   *
   * @return 误判率
   */
  public double getFalsePositiveProbability() {
    return falsePositiveProbability;
  }

  /**
   * 获取窗口翻转间隔（秒）。
   *
   * @return 窗口翻转间隔
   */
  public int getRotateSeconds() {
    return rotateSeconds;
  }

  /**
   * 获取当前窗口已运行秒数。
   *
   * @return 窗口年龄（秒）
   */
  public long getWindowAgeSeconds() {
    return Duration.ofMillis(System.currentTimeMillis() - windowCreatedAt.get()).getSeconds();
  }
}
