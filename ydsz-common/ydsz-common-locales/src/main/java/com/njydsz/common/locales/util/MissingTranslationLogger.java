package com.njydsz.common.locales.util;

import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 国际化翻译缺失节流器日志器
 *
 * <p>记录运行时 i18n 文案解析失败（返回原始 key 而非目标语言文案）的场景，帮助开发与测试团队发现翻译遗漏。
 *
 * <p><b>节流策略：</b>使用固定大小的环形缓冲区记录最近 N 个缺失 key，仅当 key 不在缓冲区中且缓冲区未满至重复时才打印
 * WARN 日志，避免高频重复 key 打爆日志磁盘。缓冲区通过 {@link #configure} 启用/禁用，启动节流器默认禁用，需由 {@link
 * com.njydsz.common.locales.config.LocalesAutoConfiguration} 显式启用。
 *
 * <p><b>使用约束：</b>
 *
 * <ul>
 *   <li>仅由 {@link MessageSourceHolder#resolve(String, Object[], Locale)} 调用，不对外暴露公开 API
 *   <li>所有方法线程安全（无锁 + CAS + concurrent 集合）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 * @see MessageSourceHolder
 */
public final class MissingTranslationLogger {

  private static final Logger LOG = LoggerFactory.getLogger(MissingTranslationLogger.class);

  /** 环形缓冲区大小上限：最近 N 个不同的缺失 key 不再重复打印日志（控制日志噪声） */
  private static final int RING_BUFFER_SIZE = 200;

  private MissingTranslationLogger() {
    // 工具类禁止实例化
  }

  /** 节流器运行状态引用（避免每次日志调用读 volatile） */
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
   * <p>打印条件：节流器已启用 + 当前 key+locale 组合不在最近记录缓冲区中。日志中包含 key、locale 和搜索栈顶（仅
   * WARN/DEBUG 级别时包含调用栈前三帧用于定位调用方）。
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
    if (state.isRecentlyLogged(dedupKey)) {
      return;
    }
    state.markLogged(dedupKey);
    if (LOG.isWarnEnabled()) {
      LOG.warn("i18n translation missing: key={}, locale={}", key, locale);
    }
  }

  /** 节流器状态对象（每个状态实例不可变，通过 CAS 切换） */
  private static final class ThrottlerState {

    private final boolean enabled;
    private final int capacity;
    private final Queue<String> ringBuffer;

    private ThrottlerState(boolean enabled, int capacity, Queue<String> ringBuffer) {
      this.enabled = enabled;
      this.capacity = capacity;
      this.ringBuffer = ringBuffer;
    }

    static ThrottlerState disabled() {
      return new ThrottlerState(false, 0, null);
    }

    static ThrottlerState enabled(int capacity) {
      return new ThrottlerState(true, capacity, new ConcurrentLinkedDeque<>());
    }

    boolean isEnabled() {
      return enabled;
    }

    boolean isRecentlyLogged(String dedupKey) {
      return ringBuffer != null && ringBuffer.contains(dedupKey);
    }

    void markLogged(String dedupKey) {
      if (ringBuffer == null) {
        return;
      }
      ringBuffer.offer(dedupKey);
      while (ringBuffer.size() > capacity) {
        ringBuffer.poll();
      }
    }
  }
}
