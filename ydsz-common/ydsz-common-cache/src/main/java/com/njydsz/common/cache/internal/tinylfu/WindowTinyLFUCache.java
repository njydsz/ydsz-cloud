package com.njydsz.common.cache.internal.tinylfu;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.cache.api.CachePolicy;
import com.njydsz.common.cache.internal.AbstractCache;
import com.njydsz.common.cache.internal.lfu.FrequencySketch;
import com.njydsz.common.cache.listener.RemovalCause;

/**
 * Window-TinyLFU 缓存实现（Caffeine 架构）。
 *
 * <p>采用三段式 LRU 队列（Window / Probation / Protected）配合 {@link FrequencySketch} 频率草图，在淘汰时优先保留高频访问条目，兼顾
 * recency 和 frequency。
 *
 * <p>新条目进入 Window 队列；再次访问从 Probation 提升到 Protected（机会性 tryLock，失败不阻塞读）； 淘汰时从 Window 尾部开始，若 Probation 队列非空则比较频率决定淘汰对象。
 * 频率草图周期性衰减（每 {@code shiftThreshold / 8} 次访问分片减半 1/8 表，累计一轮全表减半），实现滑动窗口效果。
 *
 * <p>线程安全：读操作全程无锁（{@code ConcurrentHashMap} 数据表 + CAS 频率草图 + tryLock 机会性
 * 队列提升，失败不阻塞），写操作（put/remove/evict）使用 {@link ReentrantReadWriteLock} 写锁。
 * Probation 节点的读触发 {@code tryMoveToProtected} 机会性提升：tryLock 成功时在写锁内重新校验节点
 * 有效性后提升，失败时跳过（频率草图已登记访问，淘汰竞争仍受频率保护）。
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class WindowTinyLFUCache<K, V> extends AbstractCache<K, V> {

  private static final Logger LOG = LoggerFactory.getLogger(WindowTinyLFUCache.class);

  /** 最大容量（条目数）；volatile 支持经 {@link #policy()} 运行时调整 */
  private volatile int maxSize;
  private final ConcurrentHashMap<K, Node<K, V>> data;
  private final Node<K, V> windowHead;
  private Node<K, V> windowTail;
  private final Node<K, V> probationHead;
  private Node<K, V> probationTail;
  private final Node<K, V> protectedHead;
  private Node<K, V> protectedTail;
  private final FrequencySketch frequencySketch;
  private final AtomicLong sizeCounter;
  private final AtomicLong protectedSize;
  private final AtomicLong windowSize;
  private final ReentrantReadWriteLock rwLock;
  private final ReentrantReadWriteLock.WriteLock writeLock;
  private final int shiftThreshold;
  /** 衰减分片数：全表减半拆分为 8 次分片执行，摊平写锁内的周期性停顿 */
  private static final int RESET_CHUNKS = 8;
  /** 分片衰减触发阈值（shiftThreshold / RESET_CHUNKS，至少 1） */
  private final int resetThreshold;
  /** Window 队列容量上限（maxSize 的 1%，至少 1），Caffeine 标准分段；随容量调整同步重算 */
  private volatile int maxWindowSize;
  private volatile long totalCount;

  public WindowTinyLFUCache(int maxCapacity) {
    this(maxCapacity, 1);
  }

  public WindowTinyLFUCache(int maxCapacity, int stripes) {
    this.maxSize = maxCapacity;
    this.data = new ConcurrentHashMap<>(Math.max(16, maxCapacity / 2));
    this.windowHead = new Node<>(null, null, 0);
    this.windowTail = this.windowHead;
    this.probationHead = new Node<>(null, null, 1);
    this.probationTail = this.probationHead;
    this.protectedHead = new Node<>(null, null, 2);
    this.protectedTail = this.protectedHead;
    this.frequencySketch = new FrequencySketch();
    this.frequencySketch.ensureCapacity(maxCapacity);
    this.sizeCounter = new AtomicLong(0);
    this.protectedSize = new AtomicLong(0);
    this.windowSize = new AtomicLong(0);
    this.rwLock = new ReentrantReadWriteLock(false);
    this.writeLock = rwLock.writeLock();
    this.totalCount = 0;
    this.shiftThreshold = Math.max(maxCapacity, 1000);
    this.resetThreshold = Math.max(1, this.shiftThreshold / RESET_CHUNKS);
    this.maxWindowSize = Math.max(1, (int) (maxCapacity * 0.01));
    LOG.info(
        "Window-TinyLFU 缓存已创建（Caffeine 架构，并发安全增强，周期性衰减机制），maxCapacity={}, maxWindowSize={}, shiftThreshold={}",
        maxCapacity,
        maxWindowSize,
        shiftThreshold);
  }

  /**
   * 获取缓存值（不触发加载），并更新访问热度。
   *
   * <p>命中时向频率草图登记访问（用于淘汰决策）； 处于 Probation 队列的条目会尝试提升到 Protected 队列 （提升在写锁内重新校验有效性，避免并发淘汰下的野指针）。
   * null 键返回 null 且不计入 miss（与其他实现的统计口径统一）。
   *
   * @param key 缓存键，为 null 时返回 {@code null}
   * @return 缓存值；未命中时返回 {@code null}
   */
  @Override
  public V getIfPresent(K key) {
    if (key == null) {
      return null;
    }
    Node<K, V> node = data.get(key);
    if (node == null) {
      missCount.increment();
      return null;
    }
    frequencySketch.increment(key);
    if (node.queue == 1) {
      // 非阻塞机会性提升（P0 性能修复）：tryLock 拿不到即跳过，读线程绝不因提升而阻塞。
      // 语义保障：频率草图已先于提升登记本次访问，未提升的节点在淘汰竞争时仍受频率保护，
      // 后续访问或写路径会再次尝试提升，命中率不受损。
      tryMoveToProtected(node, key);
    }
    hitCount.increment();
    return node.value;
  }

  /**
   * 写入键值对；容量满时按 W-TinyLFU 频率策略淘汰候选条目。
   *
   * <p>整个流程持有写锁，保证容量检查、淘汰与写入原子化。新条目进入 Window 队列； 键已存在时仅覆盖值并登记访问。每累计 {@code resetThreshold}
   * 次访问对频率草图的一个分片做 减半衰减（8 个分片轮转，摊平停顿），维持滑动窗口热度语义。null 键或 null 值被静默忽略。
   *
   * @param key 缓存键，为 null 时忽略
   * @param value 缓存值，为 null 时忽略
   */
  @Override
  public void put(K key, V value) {
    if (key == null || value == null) {
      return;
    }
    // 整个 put 流程均在 writeLock 内执行，避免：
    // 1) 与 evictOnce() 中 data.remove + notifyRemoval(victim.value) 的数据竞争
    // 2) existing.value = value 与 frequencySketch.increment(key) 的非原子组合
    // 3) LFU 容量超限（无锁 size 检查与 put 不原子）
    writeLock.lock();
    try {
      Node<K, V> existing = data.get(key);
      if (existing != null) {
        existing.value = value;
        frequencySketch.increment(key);
        return;
      }
      long currentTotal = totalCount;
      if (currentTotal >= resetThreshold) {
        // 分片衰减（P1 性能修复）：每次仅重置 1/RESET_CHUNKS 的表，把 O(表长) 的全表 CAS
        // 停顿摊平到 RESET_CHUNKS 次触发；累计一轮后全表各槽恰好减半一次，总衰减量与
        // 旧实现（全表 reset）等价，写锁内不再出现周期性长停顿。
        frequencySketch.resetPortion(RESET_CHUNKS);
        totalCount = 0;
      }
      totalCount++;
      // 新条目进入 Window 队列
      Node<K, V> newNode = new Node<>(key, value, 0);
      data.put(key, newNode);
      addFirst(windowHead, newNode);
      sizeCounter.incrementAndGet();
      windowSize.incrementAndGet();
      frequencySketch.increment(key);

      // 分段容量治理（对齐 Caffeine W-TinyLFU）：
      // 1) Window 超限 → 尾部作为 candidate 迁入 Probation（不直接淘汰，保留竞争机会）
      while (windowSize.get() > maxWindowSize) {
        Node<K, V> node = removeLast(windowHead);
        if (node == null) {
          break;
        }
        windowSize.decrementAndGet();
        node.queue = 1;
        addFirst(probationHead, node);
      }
      // 2) 总容量超限 → admission 淘汰（candidate 与 victim 频率比较）
      while (sizeCounter.get() > maxSize) {
        if (!evictOnce()) {
          break;
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * 执行一次 TinyLFU admission 淘汰。
   *
   * <p><b>Admission 策略（对齐 Caffeine 标准 W-TinyLFU）：</b>
   *
   * <ol>
   *   <li>candidate 取 Probation 头部（最近迁入 main 区的元素）
   *   <li>victim 取 Probation 尾部（最老的 Probation 元素）
   *   <li>candidate 频率 >= victim 频率 → victim 出局、candidate 晋升 Protected（Protected 满则降级其 LRU）；
   *       否则 candidate 出局、victim 放回 Probation
   * </ol>
   *
   * @return 是否发生了淘汰（false 表示 Probation 已空，无需继续）
   */
  private boolean evictOnce() {
    // 1. candidate：Probation 头部（最近进入 main 区的候选）
    Node<K, V> candidate = removeFirst(probationHead);
    if (candidate == null) {
      return false;
    }
    // 2. victim：Probation 尾部（最老的）
    Node<K, V> victim = removeLast(probationHead);
    if (victim == null) {
      // Probation 仅有 candidate 一个元素：直接淘汰 candidate
      evictNode(candidate);
      return true;
    }

    // 3. Admission：频率比较决定去留
    int candidateFreq = frequencySketch.frequency(candidate.key);
    int victimFreq = frequencySketch.frequency(victim.key);
    if (candidateFreq >= victimFreq) {
      // candidate 胜出：victim 出局，candidate 晋升 Protected
      evictNode(victim);
      promoteToProtected(candidate);
    } else {
      // victim 胜出：candidate 出局，victim 放回 Probation
      evictNode(candidate);
      addFirst(probationHead, victim);
    }
    return true;
  }

  /**
   * 从数据表与链表移除节点并发送淘汰通知，同步递减总容量计数。
   *
   * @param node 待淘汰节点
   */
  private void evictNode(Node<K, V> node) {
    remove(node);
    data.remove(node.key);
    sizeCounter.decrementAndGet();
    notifyRemoval(node.key, node.value, RemovalCause.SIZE);
  }

  /**
   * 将节点晋升到 Protected 队列；若 Protected 已达 80% 容量上限，降级其 LRU（尾部）回 Probation。
   *
   * @param node 待晋升节点（当前位于 Probation 或 Window）
   */
  private void promoteToProtected(Node<K, V> node) {
    remove(node);
    node.queue = 2;
    addFirst(protectedHead, node);
    long pSize = protectedSize.incrementAndGet();
    if (pSize > maxSize * 0.80) {
      Node<K, V> demoted = removeLast(protectedHead);
      if (demoted != null) {
        demoted.queue = 1;
        addFirst(probationHead, demoted);
        protectedSize.decrementAndGet();
      }
    }
  }

  /**
   * 非阻塞机会性提升 Probation 节点到 Protected 队列。
   *
   * <p><b>与旧实现（阻塞获取写锁）的差异</b>：tryLock 失败时直接返回，读线程绝不因队列提升而
   * 阻塞——旧实现使 Probation 节点的每次读都串行化在全局写锁上（JMH 16 线程实测：读吞吐随线程数
   * 不升反降，19.4k → 3.7k ops/ms）。
   *
   * <p><b>正确性</b>：tryLock 成功后在写锁内重新校验节点有效性（仍在 data 中且仍为 Probation 队列），
   * 避免并发淘汰导致野指针；跳过的提升由后续访问或淘汰时的频率竞争兜底——admission 决策以频率草图
   * 为准，草图先于提升登记访问，未提升的高频节点在与 candidate 的频率比较中仍受保护。
   *
   * @param node 待提升的节点
   * @param key 缓存键（用于重新校验 data 中是否仍存在）
   */
  private void tryMoveToProtected(Node<K, V> node, K key) {
    if (!writeLock.tryLock()) {
      return;
    }
    try {
      // 重新校验：node 仍在 data 中且仍为 probation 队列
      Node<K, V> current = data.get(key);
      if (current == node && current.queue == 1) {
        promoteToProtected(node);
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * 移除指定键并返回被移除的值。
   *
   * <p>写锁内同时从数据表与所在链表移除节点，并向监听器发送 {@link RemovalCause#EXPLICIT} 通知。
   *
   * @param key 缓存键，为 null 时返回 {@code null}
   * @return 被移除的值；键不存在时返回 {@code null}
   */
  @Override
  public V remove(K key) {
    if (key == null) {
      return null;
    }
    writeLock.lock();
    try {
      Node<K, V> node = data.remove(key);
      if (node != null) {
        remove(node);
        sizeCounter.decrementAndGet();
        notifyRemoval(node.key, node.value, RemovalCause.EXPLICIT);
        return node.value;
      }
      return null;
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * 清空缓存，重置三段 LRU 队列与全部尺寸计数。
   *
   * <p>写锁内对全部条目发送 {@link RemovalCause#EXPLICIT} 通知后清空； 同时重置 {@code windowSize} 与
   * {@code totalCount}（P1 修复：此前漏重置导致 Window 段容量治理在 clear 后永久失真、 每次多付一次空轮询）。
   */
  @Override
  public void clear() {
    writeLock.lock();
    try {
      for (Node<K, V> node : data.values()) {
        notifyRemoval(node.key, node.value, RemovalCause.EXPLICIT);
      }
      data.clear();
      clearAll(windowHead);
      clearAll(probationHead);
      clearAll(protectedHead);
      sizeCounter.set(0);
      protectedSize.set(0);
      windowSize.set(0);
      totalCount = 0;
    } finally {
      writeLock.unlock();
    }
  }

  private void addFirst(Node<K, V> head, Node<K, V> node) {
    node.prev = head;
    node.next = head.next;
    if (head.next != null) {
      head.next.prev = node;
    } else {
      // 列表之前为空，更新 tail
      updateTail(head, null, node);
    }
    head.next = node;
  }

  /** 更新指定头节点对应的 tail 指针 */
  private void updateTail(Node<K, V> head, Node<K, V> expectedTail, Node<K, V> newTail) {
    if (head == windowHead) {
      windowTail = newTail;
    } else if (head == probationHead) {
      probationTail = newTail;
    } else if (head == protectedHead) {
      protectedTail = newTail;
    }
  }

  private void remove(Node<K, V> node) {
    if (node.prev != null) {
      node.prev.next = node.next;
    }
    if (node.next != null) {
      node.next.prev = node.prev;
    }
    // 更新 tail 指针（如果删除的是尾节点）
    if (node == windowTail && node.prev != windowHead) {
      windowTail = node.prev;
    } else if (node == windowTail && node.prev == windowHead) {
      windowTail = windowHead;
    }
    if (node == probationTail && node.prev != probationHead) {
      probationTail = node.prev;
    } else if (node == probationTail && node.prev == probationHead) {
      probationTail = probationHead;
    }
    if (node == protectedTail && node.prev != protectedHead) {
      protectedTail = node.prev;
    } else if (node == protectedTail && node.prev == protectedHead) {
      protectedTail = protectedHead;
    }
    node.prev = null;
    node.next = null;
  }

  private Node<K, V> removeFirst(Node<K, V> head) {
    Node<K, V> first = head.next;
    if (first != null) {
      head.next = first.next;
      if (first.next != null) {
        first.next.prev = head;
      } else {
        // 列表变空，重置 tail
        updateTail(head, null, head);
      }
      first.prev = null;
      first.next = null;
    }
    return first;
  }

  /** O(1) 尾删除（使用 tail 指针） */
  private Node<K, V> removeLast(Node<K, V> head) {
    Node<K, V> tail = getTail(head);
    if (tail == head || tail == null) {
      return null;
    }
    // 从链表中移除尾节点
    tail.prev.next = null;
    Node<K, V> newTail = (tail.prev == head) ? head : tail.prev;
    updateTail(head, tail, newTail);
    tail.prev = null;
    tail.next = null;
    return tail;
  }

  /** 获取指定头节点对应的 tail 指针 */
  private Node<K, V> getTail(Node<K, V> head) {
    if (head == windowHead) {
      return windowTail;
    }
    if (head == probationHead) {
      return probationTail;
    }
    if (head == protectedHead) {
      return protectedTail;
    }
    return null;
  }

  private boolean isEmpty(Node<K, V> head) {
    return head.next == null;
  }

  private void clearAll(Node<K, V> head) {
    head.next = null;
    updateTail(head, null, head);
  }

  /**
   * 返回缓存条目数（原子计数器，精确的近似值）。
   *
   * @return 当前缓存条目数
   */
  @Override
  public long estimatedSize() {
    return sizeCounter.get();
  }

  /**
   * 判断缓存中是否存在指定键。
   *
   * @param key 缓存键，为 null 时返回 {@code false}
   * @return 键存在时返回 {@code true}
   */
  @Override
  public boolean containsKey(K key) {
    if (key == null) {
      return false;
    }
    return data.containsKey(key);
  }

  /**
   * 返回缓存键集合。
   *
   * <p>复制为新的 {@link HashSet}，返回一次性快照，不含弱一致视图语义。
   *
   * @return 当前缓存键的快照集合
   */
  @Override
  public Set<K> keySet() {
    return new HashSet<>(data.keySet());
  }

  /**
   * 返回缓存值集合。
   *
   * <p>复制为一次性快照，值为节点中的实际数据。
   *
   * @return 当前缓存值的快照集合
   */
  @Override
  public Collection<V> values() {
    List<V> values = new ArrayList<>(data.size());
    for (Node<K, V> node : data.values()) {
      values.add(node.value);
    }
    return values;
  }

  /**
   * 获取缓存命中率。
   *
   * @return 命中率，范围 [0.0, 1.0]
   */
  @Override
  public double getHitRate() {
    long total = hitCount.sum() + missCount.sum();
    return total == 0 ? 0.0 : (double) hitCount.sum() / total;
  }

  // getStats() 不再覆写：继承 AbstractCache 的完整统计
  // （旧覆写仅返回命中/未命中，丢弃了淘汰计数与加载统计，属统计有损回归）。

  /**
   * 获取缓存策略查询接口 — 支持运行时调整最大容量。
   *
   * <p>缩容立即生效（写锁内按 W-TinyLFU 频率竞争淘汰至新容量）； 扩容仅影响后续写入的淘汰判定。
   * Window 配额（容量的 1%）随容量同步重算； 频率草图容量不追溯扩容（计数精度随容量增长平缓退化，与 Caffeine 同口径）。
   *
   * @return 缓存策略
   */
  @Override
  public CachePolicy policy() {
    return new CachePolicy() {
      /**
       * 查询淘汰策略（W-TinyLFU 频率淘汰，按条目数计量）。
       *
       * @return 淘汰策略，始终非空
       */
      @Override
      public Optional<EvictionPolicy> eviction() {
        return Optional.of(
            new EvictionPolicy() {
              /**
               * 获取当前最大容量。
               *
               * @return 当前最大容量（条目数）
               */
              @Override
              public OptionalLong getMaximum() {
                return OptionalLong.of(maxSize);
              }

              /**
               * 调整最大容量（&lt;1 抛 IllegalArgumentException）。
               *
               * <p>缩容时在写锁内触发频率竞争淘汰；扩容立即放宽写入门槛。
               *
               * @param maximumSize 新的最大容量（条目数，&ge;1）
               */
              @Override
              public void setMaximum(long maximumSize) {
                if (maximumSize < 1) {
                  throw new IllegalArgumentException("maximumSize must be >= 1");
                }
                if (maximumSize > Integer.MAX_VALUE) {
                  throw new IllegalArgumentException(
                      "maximumSize must be <= " + Integer.MAX_VALUE);
                }
                int oldMaxSize = maxSize;
                maxSize = (int) maximumSize;
                maxWindowSize = Math.max(1, (int) (maximumSize * 0.01));
                LOG.info(
                    "WindowTinyLFUCache 最大容量调整: {} -> {}", oldMaxSize, maxSize);
                if (maximumSize < oldMaxSize) {
                  shrinkToCapacity();
                }
              }

              /**
               * 获取当前加权大小（本实现按条目数计量，不支持权重）。
               *
               * @return {@link OptionalLong#empty()}，不支持权重统计
               */
              @Override
              public OptionalLong weightedSize() {
                return OptionalLong.empty();
              }

              /**
               * 是否使用权重（本实现按条目数淘汰）。
               *
               * @return 恒为 {@code false}
               */
              @Override
              public boolean isWeighted() {
                return false;
              }
            });
      }

      /**
       * 查询过期策略（过期由 ExpirableCache 装饰器负责，内核不支持）。
       *
       * @return {@link Optional#empty()}，本内核不管理过期
       */
      @Override
      public Optional<ExpirationPolicy> expiration() {
        return Optional.empty();
      }
    };
  }

  /** 运行时缩容：写锁内按频率竞争淘汰至总量不超过当前容量。 */
  private void shrinkToCapacity() {
    writeLock.lock();
    try {
      while (sizeCounter.get() > maxSize) {
        if (!evictOnce()) {
          break;
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  /**
   * W-TinyLFU 链表节点：同时服务于 Window / Probation / Protected 三段队列。
   *
   * <p>{@code queue} 标记当前所在队列（0=Window，1=Probation，2=Protected）， 提升与降级即修改该字段并在目标队列头尾插入； {@code
   * prev}/{@code next} 构成双向链表，由各队列的头节点（哨兵）组织。 除 key 外的字段均为 volatile，保证并发读写下的可见性。
   *
   * @author ydsz-team
   * @since 1.0.0
   */
  private static final class Node<K, V> {
    final K key;
    volatile V value;
    volatile int queue;
    volatile Node<K, V> prev;
    volatile Node<K, V> next;

    Node(K key, V value, int queue) {
      this.key = key;
      this.value = value;
      this.queue = queue;
    }
  }
}
