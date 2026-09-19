package com.njydsz.common.audit.core;

import java.util.List;

import com.njydsz.common.audit.domain.AuditLog;

/**
 * 审计记录器接口
 *
 * <p>定义审计日志记录的统一抽象，支持同步、异步、批量三种记录方式。 实现类可基于 BlockingQueue、Disruptor、消息队列等不同技术实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface AuditRecorder {

  /**
   * 同步记录单条审计日志
   *
   * @param auditLog 审计日志实体
   */
  void record(AuditLog auditLog);

  /**
   * 异步记录单条审计日志，不阻塞调用线程
   *
   * @param auditLog 审计日志实体
   */
  void recordAsync(AuditLog auditLog);

  /**
   * 批量记录审计日志
   *
   * @param auditLogs 审计日志列表
   */
  void recordBatch(List<AuditLog> auditLogs);

  /**
   * 获取记录器名称
   *
   * @return 记录器名称（默认返回类名简单名）
   */
  default String getName() {
    return this.getClass().getSimpleName();
  }

  /**
   * 返回当前记录器的健康状态
   *
   * <p>默认实现返回 UP 状态，子类可覆盖以提供运行时指标（如队列水位、丢弃计数等）。
   *
   * @return 健康信息
   */
  default HealthInfo health() {
    return HealthInfo.up();
  }

  /**
   * 返回当前异步队列大小。
   *
   * <p>默认实现返回 0（同步记录器无队列概念），异步记录器应覆盖此方法。
   *
   * @return 队列中待写入的审计日志数量
   * @since 26.09.19
   */
  default int getQueueSize() {
    return 0;
  }

  /**
   * 返回当前异步队列使用率（范围 0.0-1.0）。
   *
   * <p>默认实现返回 0.0（同步记录器无队列概念），异步记录器应覆盖此方法。
   * 返回 {@code double} 而非 {@code BigDecimal}，避免 Micrometer Gauge 回调路径上的对象创建开销。
   *
   * @return 队列使用率，范围 [0.0, 1.0]
   * @since 26.09.19
   */
  default double getQueueUsageRatio() {
    return 0.0;
  }

  /**
   * 返回队列满累计触发次数。
   *
   * <p>默认返回 0（同步记录器无队列概念），异步记录器应覆盖此方法。
   *
   * @return 队列满累计触发次数
   * @since 26.09.19
   */
  default long getQueueFullWarnCount() {
    return 0L;
  }
}
