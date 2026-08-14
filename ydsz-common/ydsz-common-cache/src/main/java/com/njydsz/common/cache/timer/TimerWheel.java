package com.njydsz.common.cache.timer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分层时间轮（Hierarchical Timer Wheel）— O(1) 过期调度算法。
 *
 * <p>相比 {@link java.util.concurrent.DelayQueue} 的 O(log n) 插入/删除，
 * 时间轮提供<b>均摊 O(1)</b> 的插入和过期检测，适合大规模键值过期场景。
 *
 * <p>算法原理：
 *
 * <ul>
 *   <li>每层是一个环形数组，每个槽位对应一个时间间隔（tick）
 *   <li>第 0 层：tick = 1 秒，60 槽 → 覆盖 60 秒
 *   <li>第 1 层：tick = 60 秒，60 槽 → 覆盖 1 小时
 *   <li>第 2 层：tick = 1 小时，24 槽 → 覆盖 1 天
 *   <li>第 3 层：tick = 1 天，31 槽 → 覆盖 1 月
 *   <li>当某层转完一圈，溢出条目降级到上一层
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * TimerWheel<String> wheel = new TimerWheel<>();
 * wheel.schedule("key1", System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
 *
 * // 每 tick 调用一次（通常 1 秒）
 * List<String> expired = wheel.advance();
 * }</pre>
 *
 * <p>线程安全：{@link #schedule} 和 {@link #cancel} 可被多线程调用；
 * {@link #advance} 应由单线程调用（通常是定时任务的执行线程）。
 *
 * @param <K> 被调度元素的键类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class TimerWheel<K> {

  /** 单层时间轮 */
  private final class WheelLevel {
    final long tickNanos;
    final int wheelSize;
    final long spanNanos;
    final Set<K>[] slots;
    volatile int currentTick;

    @SuppressWarnings("unchecked")
    WheelLevel(long tickNanos, int wheelSize) {
      this.tickNanos = tickNanos;
      this.wheelSize = wheelSize;
      this.spanNanos = tickNanos * wheelSize;
      this.slots = (Set<K>[]) new Set[wheelSize];
      for (int i = 0; i < wheelSize; i++) {
        slots[i] = ConcurrentHashMap.newKeySet();
      }
    }
  }

  /** 第 0 层：1 秒 tick，60 槽 */
  private static final long LEVEL0_TICK = 1_000_000_000L;
  private static final int LEVEL0_SIZE = 60;

  /** 第 1 层：60 秒 tick，60 槽 */
  private static final long LEVEL1_TICK = LEVEL0_TICK * LEVEL0_SIZE;
  private static final int LEVEL1_SIZE = 60;

  /** 第 2 层：1 小时 tick，24 槽 */
  private static final long LEVEL2_TICK = LEVEL1_TICK * LEVEL1_SIZE;
  private static final int LEVEL2_SIZE = 24;

  /** 第 3 层：1 天 tick，31 槽 */
  private static final long LEVEL3_TICK = LEVEL2_TICK * LEVEL2_SIZE;
  private static final int LEVEL3_SIZE = 31;

  private final List<WheelLevel> levels;
  private final AtomicLong currentNanos = new AtomicLong(System.nanoTime());

  /** 创建分层时间轮（4 层，覆盖范围约 1 月） */
  public TimerWheel() {
    this.levels = Arrays.asList(
        new WheelLevel(LEVEL0_TICK, LEVEL0_SIZE),
        new WheelLevel(LEVEL1_TICK, LEVEL1_SIZE),
        new WheelLevel(LEVEL2_TICK, LEVEL2_SIZE),
        new WheelLevel(LEVEL3_TICK, LEVEL3_SIZE));
  }

  /**
   * 调度一个元素在指定时间过期
   *
   * @param key 元素键
   * @param expireAtNanos 过期时间戳（纳秒）
   */
  public void schedule(K key, long expireAtNanos) {
    long delay = expireAtNanos - currentNanos.get();
    if (delay <= 0) {
      // 已过期，放入第 0 层当前槽位
      levels.get(0).slots[levels.get(0).currentTick].add(key);
      return;
    }
    // 找到合适的层级
    for (int i = levels.size() - 1; i >= 0; i--) {
      WheelLevel level = levels.get(i);
      if (delay > level.spanNanos) {
        // 超出本层范围，放入最高层
        int slot = (int) ((currentNanos.get() + delay) / level.tickNanos) % level.wheelSize;
        level.slots[slot].add(key);
        return;
      }
      if (delay > level.tickNanos || i == 0) {
        int slot = (int) ((currentNanos.get() + delay) / level.tickNanos) % level.wheelSize;
        level.slots[slot].add(key);
        return;
      }
    }
  }

  /**
   * 取消一个已调度的元素
   *
   * @param key 元素键
   * @return true 表示成功取消
   */
  public boolean cancel(K key) {
    for (WheelLevel level : levels) {
      for (Set<K> slot : level.slots) {
        if (slot.remove(key)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * 推进时间轮一个 tick，返回当前已过期的所有元素
   *
   * <p>应由单线程周期性调用（通常 1 秒一次）。
   *
   * @return 已过期的元素列表
   */
  public List<K> advance() {
    List<K> expired = new ArrayList<>();
    currentNanos.addAndGet(LEVEL0_TICK);
    advanceLevel(0, expired);
    return expired;
  }

  /** 推进指定层，处理溢出降级 */
  private void advanceLevel(int levelIdx, List<K> expired) {
    WheelLevel level = levels.get(levelIdx);
    level.currentTick = (level.currentTick + 1) % level.wheelSize;

    // 收集当前槽位的过期元素
    Set<K> slot = level.slots[level.currentTick];
    if (!slot.isEmpty()) {
      expired.addAll(slot);
      slot.clear();
    }

    // 转完一圈，降级上一层溢出的条目
    if (level.currentTick == 0 && levelIdx < levels.size() - 1) {
      advanceLevel(levelIdx + 1, expired);
    }
  }

  /**
   * 获取当前调度中的元素总数（近似值）
   *
   * @return 所有层槽位中元素数量之和
   */
  public int size() {
    int total = 0;
    for (WheelLevel level : levels) {
      for (Set<K> slot : level.slots) {
        total += slot.size();
      }
    }
    return total;
  }
}
