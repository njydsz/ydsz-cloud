package com.njydsz.common.audit.storage;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.audit.core.AuditWriter;
import com.njydsz.common.audit.domain.AuditLog;

/**
 * 默认审计日志存储实现
 *
 * <p>通过内存队列缓存审计日志，仅用于开发和测试环境。 生产环境应使用 {@link JdbcAuditStorage} 持久化到数据库。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DefaultAuditStorage implements AuditWriter {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultAuditStorage.class);

  /** 默认最大容量 */
  private static final int DEFAULT_MAX_CAPACITY = 10000;

  /** 有界阻塞队列，限制内存中审计日志的最大数量 */
  private final BlockingQueue<AuditLog> queue;

  /** 队列最大容量 */
  private final int maxCapacity;

  /** 使用默认最大容量（10000）构造 */
  public DefaultAuditStorage() {
    this(DEFAULT_MAX_CAPACITY);
  }

  /**
   * 使用指定最大容量构造
   *
   * @param maxCapacity 最大容量
   */
  public DefaultAuditStorage(int maxCapacity) {
    this.maxCapacity = maxCapacity;
    this.queue = new LinkedBlockingQueue<>(maxCapacity);
  }

  @Override
  public void write(AuditLog auditLog) {
    if (auditLog == null) {
      LOG.warn("【审计存储】审计日志为空,跳过保存");
      return;
    }
    // 尝试将日志加入队列，队列满时丢弃最旧日志
    if (!queue.offer(auditLog)) {
      AuditLog dropped = queue.poll();
      if (dropped != null) {
        LOG.warn("【审计存储】队列已满(容量={})，丢弃最旧日志: {}", maxCapacity, dropped);
      }
      if (!queue.offer(auditLog)) {
        LOG.error("【审计存储】队列已满且无法插入新日志，该日志被丢弃: {}", auditLog);
      }
    }
    LOG.debug("【审计日志】{}", auditLog);
  }

  @Override
  public void writeBatch(List<AuditLog> auditLogs) {
    if (auditLogs == null || auditLogs.isEmpty()) {
      LOG.warn("【审计存储】审计日志列表为空,跳过保存");
      return;
    }
    for (AuditLog auditLog : auditLogs) {
      write(auditLog);
    }
  }

  @Override
  public String getType() {
    return "DEFAULT";
  }

  /**
   * 获取当前队列中的日志数量
   *
   * @return 队列大小
   */
  public int size() {
    return queue.size();
  }

  /**
   * 获取最大容量
   *
   * @return 最大容量
   */
  public int getMaxCapacity() {
    return maxCapacity;
  }

  /**
   * 获取队列中排队的日志（用于消费）
   *
   * @return 审计日志
   */
  public AuditLog poll() {
    return queue.poll();
  }
}
