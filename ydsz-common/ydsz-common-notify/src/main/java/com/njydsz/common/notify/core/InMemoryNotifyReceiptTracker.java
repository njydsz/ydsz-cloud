package com.njydsz.common.notify.core;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内存实现的通知回执追踪器
 *
 * <p>基于 {@link ConcurrentHashMap} 的内存存储，适用于单实例开发和测试环境。 生产环境应替换为基于 Redis 或数据库的实现以支持持久化和跨实例查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class InMemoryNotifyReceiptTracker implements NotifyReceiptTracker {

  private static final Logger log = LoggerFactory.getLogger(InMemoryNotifyReceiptTracker.class);

  private final ConcurrentHashMap<String, NotifyReceipt> receipts = new ConcurrentHashMap<>();

  @Override
  public NotifyReceipt createReceipt(String messageId, String channel, String receiver) {
    NotifyReceipt receipt = NotifyReceipt.pending(messageId, channel, receiver);
    receipts.put(messageId, receipt);
    log.debug("[NotifyReceiptTracker] 回执已创建: messageId={}, channel={}", messageId, channel);
    return receipt;
  }

  @Override
  public void markDelivered(String messageId) {
    NotifyReceipt existing = receipts.get(messageId);
    if (existing != null) {
      receipts.put(messageId, existing.markDelivered());
      log.debug("[NotifyReceiptTracker] 回执已投递: messageId={}", messageId);
    }
  }

  @Override
  public void markFailed(String messageId, String errorMessage) {
    NotifyReceipt existing = receipts.get(messageId);
    if (existing != null) {
      receipts.put(messageId, existing.markFailed(errorMessage));
      log.debug("[NotifyReceiptTracker] 回执投递失败: messageId={}, error={}", messageId, errorMessage);
    }
  }

  @Override
  public void markRead(String messageId) {
    NotifyReceipt existing = receipts.get(messageId);
    if (existing != null) {
      receipts.put(messageId, existing.markRead());
      log.debug("[NotifyReceiptTracker] 回执已读: messageId={}", messageId);
    }
  }

  @Override
  public Optional<NotifyReceipt> findReceipt(String messageId) {
    return Optional.ofNullable(receipts.get(messageId));
  }

  /**
   * 获取当前回执数量（用于监控）
   *
   * @return 回执总数
   */
  public int size() {
    return receipts.size();
  }

  /**
   * 清理已终态（DELIVERED/FAILED/READ）超过指定时间的回执
   *
   * @param maxAgeMs 最大保留时间（毫秒）
   * @return 清理数量
   */
  public int cleanup(long maxAgeMs) {
    long threshold = System.currentTimeMillis() - maxAgeMs;
    int removed = 0;
    for (var it = receipts.entrySet().iterator(); it.hasNext(); ) {
      var entry = it.next();
      NotifyReceipt r = entry.getValue();
      if (r.status() != NotifyReceipt.ReceiptStatus.PENDING
          && r.updatedAt().toEpochMilli() < threshold) {
        it.remove();
        removed++;
      }
    }
    if (removed > 0) {
      log.debug("[NotifyReceiptTracker] 清理过期回执: {} 条", removed);
    }
    return removed;
  }
}
