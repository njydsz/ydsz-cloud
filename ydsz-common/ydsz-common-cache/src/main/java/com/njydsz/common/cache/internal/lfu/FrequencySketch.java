package com.njydsz.common.cache.internal.lfu;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Count-Min Sketch 频率草图（LFU 淘汰策略核心组件）。
 *
 * <p>基于 4 路哈希 + CAS 无锁更新的紧凑频率计数器，用于 {@link WindowTinyLFUCache} 中估计元素的访问频率，以在淘汰时保留高频访问的条目。
 *
 * <p>每个计数器占用 4 bit（默认）或 8 bit（可配置），所有计数器定期衰减一半 以实现滑动窗口效果。表大小始终为 2 的幂次，通过位掩码索引。
 *
 * <p>线程安全：所有读写操作使用 {@link VarHandle} CAS 保证原子性。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class FrequencySketch {

  private static final long[] SEEDS = {
    0xc3a5c85c97cb3127L, 0xb492b66fbe98f273L, 0x9ae16a3b2f90404fL, 0xcbf29ce484222325L
  };

  private static final VarHandle TABLE_VARHANDLE;

  static {
    try {
      TABLE_VARHANDLE = MethodHandles.arrayElementVarHandle(long[].class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private volatile long[] table;
  private int resetMask;
  /** 计数器索引掩码：{@code table.length * countersPerLong - 1}，用于哈希探测定位计数器索引 */
  private int indexMask;
  private int counterMask = 0xf;
  private int maxCount = 15;
  private long resetHalveMask = 0x7777777777777777L;
  private int counterShift = 2;
  /** 每个 long 可承载的计数器个数（4bit 为 16，8bit 为 8） */
  private int countersPerLong = 16;
  /** 定位 long 槽所需移位位数（4bit 为 4，8bit 为 3） */
  private int countersPerLongShift = 4;
  /** 分片衰减游标：下一个待衰减 chunk 的起始槽位（推进依赖调用方写锁串行化，见 {@link #resetPortion}） */
  private int resetCursor;

  public FrequencySketch() {
    ensureCapacity(1024);
  }

  /**
   * 配置计数器位宽（4 或 8 bit）。
   *
   * <p>4 bit 计数器上限 15、表压缩率更高；8 bit 上限 255、精度更高但占内存翻倍。 应在 {@link #ensureCapacity}
   * 之前调用，否则只会影响后续新建计数器的位宽； 传入其他位宽抛出 {@link IllegalArgumentException}。
   *
   * @param bitSize 计数器位宽，仅支持 4 或 8
   * @throws IllegalArgumentException 当 bitSize 不是 4 或 8 时抛出
   */
  public void setBitSize(int bitSize) {
    if (bitSize == 4) {
      counterMask = 0xf;
      maxCount = 15;
      resetHalveMask = 0x7777777777777777L;
      counterShift = 2;
      countersPerLong = 16;
      countersPerLongShift = 4;
    } else if (bitSize == 8) {
      counterMask = 0xff;
      maxCount = 255;
      resetHalveMask = 0x7f7f7f7f7f7f7f7fL;
      counterShift = 3;
      countersPerLong = 8;
      countersPerLongShift = 3;
    } else {
      throw new IllegalArgumentException("不支持位宽: " + bitSize + "，仅支持 4 或 8");
    }
    // 同步更新计数器索引掩码，避免位宽切换后探测索引越界
    if (table != null) {
      indexMask = table.length * countersPerLong - 1;
    }
  }

  /**
   * 按缓存容量扩容频率表，使草图规模与缓存条目数相匹配。
   *
   * <p>表长按 {@code maximum} 分配（每个 long 承载 16 个 4bit 计数器，提供 16 倍采样率以抑制哈希碰撞）， 并向上取整到
   * 2 的幂次以便用位掩码替代取模，下限 1024、上限受 {@code maximumSize} 约束，避免小缓存也出现过高的哈希碰撞率。
   *
   * <p>仅在新容量<b>大于</b>现有表长时才重建；缩容请求会被忽略， 因为缩容带来的碰撞率上升得不偿失。
   *
   * <p><b>并发注意</b>：重建会直接替换 {@code table} 引用并重置掩码， 已积累的全部频率统计<b>丢失</b>，且与并发执行的 {@link
   * #increment(Object)} / {@link #reset()} 之间没有互斥， 可能出现新旧表混写。因此本方法只应在缓存初始化或重配置阶段调用， 不可在稳态运行期频繁触发。
   *
   * @param maximum 期望支撑的缓存最大条目数
   */
  public void ensureCapacity(int maximum) {
    int count = maximumSize(maximum);
    if (table != null && table.length >= count) {
      return;
    }
    int size = Integer.highestOneBit(count - 1);
    if (size < count) {
      size <<= 1;
    }
    size = Math.max(64, size);
    table = new long[size];
    resetMask = size - 1;
    indexMask = size * countersPerLong - 1;
  }

  private int maximumSize(long maximum) {
    // 表长按 maximum 分配：每个 long 承载 countersPerLong 个计数器，
    // 提供 countersPerLong 倍采样率（4bit 为 16 倍），与 Caffeine 实现保持一致
    long count = maximum;
    return (int) Math.min(Integer.MAX_VALUE, Math.max(1024, count));
  }

  /**
   * 记录一次元素访问，对其对应的 4 个计数器分别加一。
   *
   * <p>采用双哈希探测（{@code start + i * increment}）定位 4 个槽位， 每个槽位用 CAS 自增；计数器已达上限（4bit 为 15、8bit 为 255）时
   * 该槽位<b>饱和不再增长</b>，靠 {@link #reset()} 的整体减半来腾出空间。
   *
   * <p>Count-Min Sketch 的固有特性：哈希碰撞只会让频率被<b>高估</b>， 不会低估，因此偶发碰撞不会导致高频热点被误淘汰。
   *
   * <p>线程安全：全程 CAS 无锁，可被多线程高并发调用； 但与 {@link #ensureCapacity(int)} 的表重建不互斥。
   *
   * @param e 被访问的元素（通常是缓存 key）；为 {@code null} 时按哈希 0 处理
   */
  public void increment(Object e) {
    int start = hash(e) & indexMask;
    int increment = hash2(e) & indexMask;
    // 保证增量与计数器索引空间（2 的幂）互质：若为偶数或 0，强制置为奇数增量
    // 避免 4 路探测退化为更少槽位，导致 Count-Min Sketch 质量下降
    if ((increment & 1) == 0 || increment == 0) {
      increment = (increment | 1) + 1;
      if ((increment & 1) == 0) {
        increment = 1;
      }
    }

    for (int i = 0; i < 4; i++) {
      int index = (start + i * increment) & indexMask;
      incrementSlot(index);
    }
  }

  /**
   * 对指定槽位计数器执行 CAS 自增（饱和则丢弃）
   *
   * @param index 计数器索引（低位定位 long 内计数器，高位定位 long 槽）
   */
  private void incrementSlot(int index) {
    int offset = (index & (countersPerLong - 1)) << counterShift;
    int slotIndex = index >>> countersPerLongShift;
    long slot;
    int count;
    do {
      slot = (long) TABLE_VARHANDLE.getVolatile(table, slotIndex);
      count = (int) ((slot >>> offset) & counterMask);
      if (count >= maxCount) {
        break;
      }
    } while (!TABLE_VARHANDLE.compareAndSet(
        table, slotIndex, slot, slot + (1L << offset)));
  }

  /**
   * 估算元素的历史访问频率，取 4 个哈希槽位计数的<b>最小值</b>。
   *
   * <p>取最小值是 Count-Min Sketch 抑制哈希碰撞误差的标准做法： 只有 4 个槽位同时被碰撞才会产生高估，概率极低。
   *
   * <p>返回的是<b>估计值而非精确计数</b>，且受 {@link #reset()} 周期性减半影响， 体现的是「近期热度」而非「累计访问总量」，
   * 仅可用于淘汰时的相对比较，不可作为业务统计口径。
   *
   * <p>线程安全：全程 volatile 读，无锁且不阻塞写入。
   *
   * @param e 待查询的元素（通常是缓存 key）；为 {@code null} 时按哈希 0 处理
   * @return 频率估计值，取值范围 {@code [0, maxCount]}（4bit 上限 15，8bit 上限 255）
   */
  public int frequency(Object e) {
    int start = hash(e) & indexMask;
    int increment = hash2(e) & indexMask;
    // 与 increment 保持一致的互质处理，保证探测路径一致
    if ((increment & 1) == 0 || increment == 0) {
      increment = (increment | 1) + 1;
      if ((increment & 1) == 0) {
        increment = 1;
      }
    }

    int min = Integer.MAX_VALUE;

    for (int i = 0; i < 4; i++) {
      int index = (start + i * increment) & indexMask;
      int offset = (index & (countersPerLong - 1)) << counterShift;
      int slotIndex = index >>> countersPerLongShift;
      long slot = (long) TABLE_VARHANDLE.getVolatile(table, slotIndex);
      int count = (int) ((slot >>> offset) & counterMask);
      if (count < min) {
        min = count;
      }
    }
    return min;
  }

  /**
   * 将全部计数器整体减半，实现频率统计的滑动窗口老化。
   *
   * <p>通过「右移 1 位 + {@code resetHalveMask} 掩码」在一个 long 中 同时对多个打包计数器做减半，掩码用于清除移位时从相邻计数器高位借来的脏位。
   *
   * <p><b>为什么必须周期性调用</b>：计数器会饱和，若只增不减， 早期的热点会永久占据高频位置，新兴热点无法获得公平竞争机会， 缓存命中率将随运行时间持续劣化。
   *
   * <p>本方法是 O(表长) 的全表扫描且逐槽 CAS，开销不小， 应由淘汰策略按访问计数触发，而非每次访问都调用。
   *
   * <p>线程安全：逐槽 CAS，可与 {@link #increment(Object)} 并发执行； 执行期间读到的频率可能是新旧混合值，对淘汰决策的影响可忽略。
   */
  public void reset() {
    long[] currentTable = table;
    for (int i = 0; i < currentTable.length; i++) {
      long slot;
      do {
        slot = (long) TABLE_VARHANDLE.getVolatile(currentTable, i);
      } while (!TABLE_VARHANDLE.compareAndSet(
          currentTable, i, slot, (slot >>> 1) & resetHalveMask));
    }
    resetCursor = 0;
  }

  /**
   * 分片衰减：每次调用仅对 {@code 1/chunks} 的表做减半，把全表衰减的停顿摊平到多次触发。
   *
   * <p><b>动机（P1 性能修复）</b>：旧路径在写锁内调用 {@link #reset()} 做全表 O(表长) 逐槽 CAS——
   * 容量 10,000 的表长约 16k，持锁期间写路径完全停顿，读路径的机会性提升也被阻塞。
   * 分片后每次仅重置一个 chunk，持锁时间缩短为原来的 {@code 1/chunks}。
   *
   * <p><b>语义等价性</b>：调用方以 {@code 原阈值 / chunks} 的频率触发本方法， 累计 {@code chunks}
   * 次后全表各槽恰好减半一轮——与单次全表减半的总衰减量一致。衰减过渡期内（部分 chunk 已减半、
   * 部分未减半）不同 key 的频率比较基准存在最多 1 bit 的偏差， 远小于 Count-Min Sketch
   * 本身的哈希碰撞误差，对淘汰决策的影响可忽略。
   *
   * <p>线程安全：逐槽 CAS，可与 {@link #increment(Object)} 并发执行。游标 {@code resetCursor}
   * 的推进依赖调用方的串行化保证（{@code WindowTinyLFUCache} 在写锁内调用，天然串行），
   * 并发调用本方法会导致 chunk 重复衰减（频率被多减一半，趋势性偏冷），不会损坏数据结构。
   *
   * @param chunks 分片数（每次重置表长的 1/chunks），必须 >= 1；大于表长时退化为逐槽分片
   */
  public void resetPortion(int chunks) {
    long[] currentTable = table;
    int chunkSize = Math.max(1, currentTable.length / Math.max(1, chunks));
    int start = resetCursor;
    if (start >= currentTable.length) {
      start = 0;
    }
    int end = Math.min(currentTable.length, start + chunkSize);
    for (int i = start; i < end; i++) {
      long slot;
      do {
        slot = (long) TABLE_VARHANDLE.getVolatile(currentTable, i);
      } while (!TABLE_VARHANDLE.compareAndSet(
          currentTable, i, slot, (slot >>> 1) & resetHalveMask));
    }
    resetCursor = end >= currentTable.length ? 0 : end;
  }

  private int hash(Object e) {
    int hash = e == null ? 0 : e.hashCode();
    hash = (hash ^ (int) (SEEDS[0] & 0xffffffffL)) * 0x85ebca6b;
    hash = hash ^ (hash >>> 13);
    return hash;
  }

  private int hash2(Object e) {
    int hash = e == null ? 0 : e.hashCode();
    hash = (hash ^ (int) (SEEDS[1] & 0xffffffffL)) * 0xc2b2ae35;
    hash = hash ^ (hash >>> 16);
    return hash;
  }
}
