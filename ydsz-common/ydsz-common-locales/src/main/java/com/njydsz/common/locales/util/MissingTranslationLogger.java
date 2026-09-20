package com.njydsz.common.locales.util;

import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 国际化翻译缺失节流器日志器
 *
 * <p>记录运行时 i18n 文案解析失败（返回原始 key 而非目标语言文案）的场景，帮助开发与测试团队发现翻译遗漏。
 *
 * <p><b>节流策略：</b>使用固定大小的环形缓冲区记录最近 N 个缺失 key，仅当 key 不在缓冲区中时才打印
 * WARN 日志，避免高频重复 key 打爆日志磁盘。缓冲区去重通过 {@link ConcurrentHashMap#newKeySet()}
 * 提供 O(1) 查找，FIFO 淘汰通过 {@link ConcurrentLinkedDeque} 维护顺序。启动节流器默认禁用，
 * 需由 {@link com.njydsz.common.locales.config.LocalesAutoConfiguration} 显式启用。
 *
 * <p><b>使用约束：</b>
 *
 * <ul>
 *   <li>仅由 {@link MessageSourceHolder#resolve(String, Object[], Locale)} 调用，不对外暴露公开 API
 *   <li>所有方法线程安全（并发集合 + CAS 状态切换）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see MessageSourceHolder
 */
public final class MissingTranslationLogger {

  private static final Logger LOG = LoggerFactory.getLogger(MissingTranslationLogger.class);

  private MissingTranslationLogger() {
    // 工具类禁止实例化
  }

  /** 节流器运行状态引用（通过 CAS 切换） */
  private static final AtomicReference<ThrottlerState> STATE =
      new AtomicReference<>(ThrottlerState.disabled());

  /**
   * 配置节流器运行参数。
   *
   * @param enabled 是否启用缺失翻译告警（true = 打印 WARN 日志；false = 静默）
   * @param bufferCapacity 环形缓冲区容量上限（建议 100-500）
   */
  public static void configure(boolean enabled, int bufferCapacity) {
    if (!enabled) {
      STATE.set(ThrottlerState.disabled());
      LOG.debug("MissingTranslationLogger 已禁用");
      return;
    }
    STATE.set(ThrottlerState.enabled(bufferCapacity));
    LOG.debug("MissingTranslationLogger 已启用 | 缓冲区容量: {}", bufferCapacity);
  }

  /**
   * 重置节流器（清空缓冲区并禁用），仅用于测试场景。
   */
  public static void reset() {
    STATE.set(ThrottlerState.disabled());
  }

  /**
   * 尝试记录缺失翻译 WARN 日志（节流版）。
   *
   * <p>打印条件：节流器已启用 + 当前 key+locale 组合不在最近记录缓冲区中。
   *
   * @param key i18n 消息键
   * @param locale 请求的 Locale
   */
  public static void tryWarn(String key, Locale locale) {
    ThrottlerState state = STATE.get();
    if (!state.isEnabled()) {
      return;
    }
    String dedupKey = key + "|" + locale;
    // O(1) 去重判断：仅当 set 中不存在时才标记并记录
    if (!state.markAndCheck(dedupKey)) {
      return;
    }
    if (LOG.isWarnEnabled()) {
      LOG.warn("i18n translation missing: key={}, locale={}", key, locale);
    }
  }

  /** 节流器状态对象（每个状态实例不可变，通过 CAS 切换；内部 set/queue 并发安全） */
  private static final class ThrottlerState {

    private final boolean enabled;
    private final int capacity;
    private final Set<String> seenSet;
    private final Queue<String> evictionQueue;

    private ThrottlerState(boolean enabled, int capacity, Set<String> seenSet, Queue<String> evictionQueue) {
      this.enabled = enabled;
      this.capacity = capacity;
      this.seenSet = seenSet;
      this.evictionQueue = evictionQueue;
    }

    static ThrottlerState disabled() {
      return new ThrottlerState(false, 0, null, null);
    }

    static ThrottlerState enabled(int capacity) {
      return new ThrottlerState(true, capacity, ConcurrentHashMap.newKeySet(), new ConcurrentLinkedDeque<>());
    }

    boolean isEnabled() {
      return enabled;
    }

    /**
     * 标记为已记录，返回 true 表示是首次记录（应打印日志），false 表示已存在（应跳过）。
     *
     * <p>使用 set.add() 返回值作为原子判断：仅当 key 不存在时返回 true 并加入队列，达到 O(1) 去重。
     *
     * @param dedupKey key|locale 组合字符串
     * @return true = 首次记录（caller 应打印日志）；false = 已存在，跳过
     */
    boolean markAndCheck(String dedupKey) {
      if (seenSet == null) {
        return false;
      }
      boolean isNew = seenSet.add(dedupKey);
      if (!isNew) {
        return false;
      }
      evictionQueue.offer(dedupKey);
      while (evictionQueue.size() > capacity) {
        String oldest = evictionQueue.poll();
        if (oldest != null) {
          seenSet.remove(oldest);
        }
      }
      return true;
    }
  }
}
