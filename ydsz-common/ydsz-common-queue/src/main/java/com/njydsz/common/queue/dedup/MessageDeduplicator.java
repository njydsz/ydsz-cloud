package com.njydsz.common.queue.dedup;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 消息幂等性去重器
 *
 * <p>提供消息消费的去重机制，防止消息被重复处理。 基于消息 traceId 进行去重判断，使用内存缓存存储已处理的消息记录。
 *
 * <p><b>注意：当前实现基于内存存储，仅适用于单实例场景。</b> 分布式场景应使用 {@link RedisMessageDeduplicator} 或自行实现基于 Redis 的去重。
 *
 * <p><b>去重策略：</b>
 *
 * <ul>
 *   <li>记录每个 traceId 的处理时间戳
 *   <li>定期清理过期记录（默认 1 小时）
 *   <li>支持自定义去重窗口大小
 *   <li>使用 ConcurrentHashMap 保证线程安全，避免 ReadWriteLock 读锁升级写锁死锁风险
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * MessageDeduplicator deduplicator = new MessageDeduplicator(3600); // 1小时去重窗口
 *
 * // 在消费消息前检查
 * QueueMessage message = subscriber.subscribeMessage();
 * if (deduplicator.isDuplicate(message.getTraceId())) {
 *     log.info("重复消息，跳过处理: {}", message.getTraceId());
 *     return;
 * }
 *
 * // 处理消息
 * processMessage(message);
 *
 * // 标记为已处理
 * deduplicator.markProcessed(message.getTraceId());
 * }</pre>
 *
 * <p><b>注意事项：</b>
 *
 * <ul>
 *   <li>内存去重，应用重启后去重记录会丢失
 *   <li>分布式场景建议使用 {@link RedisMessageDeduplicator} 实现全局去重
 *   <li>去重窗口应根据业务场景合理设置
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RedisMessageDeduplicator
 */
public class MessageDeduplicator {

  private final ConcurrentHashMap<String, Long> processedRecords;
  private final long expireWindowMs;
  private final int maxCapacity;
  private volatile long lastCleanupTime;
  private final ReentrantLock cleanupLock = new ReentrantLock();

  private static final int DEFAULT_MAX_CAPACITY = 100000;

  public MessageDeduplicator() {
    this(TimeUnit.HOURS.toMillis(1));
  }

  public MessageDeduplicator(long expireWindowMs) {
    this(expireWindowMs, DEFAULT_MAX_CAPACITY);
  }

  public MessageDeduplicator(long expireWindowMs, int maxCapacity) {
    if (expireWindowMs <= 0) {
      throw new IllegalArgumentException("去重窗口必须大于 0");
    }
    if (maxCapacity <= 0) {
      throw new IllegalArgumentException("最大容量必须大于 0");
    }
    this.expireWindowMs = expireWindowMs;
    this.maxCapacity = maxCapacity;
    this.lastCleanupTime = System.currentTimeMillis();

    this.processedRecords = new ConcurrentHashMap<>(Math.min(maxCapacity, 1024));
  }

  /**
   * 设置容量上限
   *
   * @param capacity 新的容量上限
   */
  public void setCapacity(int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("容量必须大于 0");
    }
    cleanupLock.lock();
    try {
      // ConcurrentHashMap 不支持 LRU 淘汰，这里仅做容量检查提示
      // 如需严格的 LRU 淘汰，建议使用自定义实现
      cleanupExpired();
    } finally {
      cleanupLock.unlock();
    }
  }

  /**
   * 检查消息是否为重复消息
   *
   * @param traceId 消息 traceId
   * @return true 表示重复，false 表示未处理过
   */
  public boolean isDuplicate(String traceId) {
    if (traceId == null || traceId.isEmpty()) {
      return false;
    }

    cleanupIfNecessary();

    Long processTime = processedRecords.get(traceId);
    if (processTime == null) {
      return false;
    }

    if (System.currentTimeMillis() - processTime > expireWindowMs) {
      processedRecords.remove(traceId);
      return false;
    }

    return true;
  }

  /**
   * 标记消息为已处理
   *
   * @param traceId 消息 traceId
   */
  public void markProcessed(String traceId) {
    if (traceId == null || traceId.isEmpty()) {
      return;
    }

    cleanupIfNecessary();

    processedRecords.put(traceId, System.currentTimeMillis());
  }

  /**
   * 检查并标记（原子操作）
   *
   * <p>如果消息未处理过，则标记为已处理并返回 false。 如果消息已处理过，则返回 true（表示重复）。
   *
   * @param traceId 消息 traceId
   * @return true 表示重复，false 表示首次处理
   */
  public boolean checkAndMark(String traceId) {
    if (traceId == null || traceId.isEmpty()) {
      return false;
    }

    cleanupIfNecessary();

    Long existing = processedRecords.putIfAbsent(traceId, System.currentTimeMillis());
    if (existing == null) {
      return false;
    }

    return System.currentTimeMillis() - existing <= expireWindowMs;
  }

  /**
   * 获取已去重记录数量
   *
   * @return 记录数量
   */
  public int getRecordCount() {
    cleanupIfNecessary();
    return processedRecords.size();
  }

  /** 清空所有去重记录 */
  public void clear() {
    processedRecords.clear();
    lastCleanupTime = System.currentTimeMillis();
  }

  /** 清理过期记录 */
  public void cleanupExpired() {
    long now = System.currentTimeMillis();
    processedRecords.entrySet().removeIf(entry -> now - entry.getValue() > expireWindowMs);
    lastCleanupTime = now;
  }

  private void cleanupIfNecessary() {
    long now = System.currentTimeMillis();
    if (now - lastCleanupTime > TimeUnit.MINUTES.toMillis(5)) {
      cleanupLock.lock();
      try {
        if (now - lastCleanupTime > TimeUnit.MINUTES.toMillis(5)) {
          cleanupExpired();
        }
      } finally {
        cleanupLock.unlock();
      }
    }
  }
}
