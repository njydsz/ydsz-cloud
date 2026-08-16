package com.njydsz.common.audit.core;

import com.njydsz.common.audit.domain.AuditLog;
import java.util.List;

/**
 * 审计记录器接口
 *
 * <p>定义审计日志记录的统一抽象，支持同步、异步、批量三种记录方式。 实现类可基于 BlockingQueue、Disruptor、消息队列等不同技术实现。
 *
 * @author ydsz-team
 * @since 1.0.0
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
}
