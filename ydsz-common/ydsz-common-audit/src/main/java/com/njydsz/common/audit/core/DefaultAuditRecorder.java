package com.njydsz.common.audit.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.njydsz.common.audit.domain.AuditLog;

/**
 * 基于 {@link AuditWriter} 的默认审计记录器实现
 *
 * <p>将审计记录委托给底层的 {@link AuditWriter}，支持同步、异步、批量三种记录方式。 当 {@code executor} 为 null 时，异步模式使用 {@link
 * CompletableFuture} 的默认线程池 （ForkJoinPool.commonPool()），建议生产环境显式注入专用线程池。
 *
 * <p><b>线程安全：</b>本类无状态，{@link AuditWriter} 实现需自身保证线程安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class DefaultAuditRecorder implements AuditRecorder {

  /** 审计日志写入器实现 */
  private final AuditWriter writer;

  /** 异步执行器（可为 null，为 null 时使用 CompletableFuture 默认线程池） */
  private final Executor executor;

  /**
   * 构造默认审计记录器（同步模式）
   *
   * @param writer 审计日志写入器实现
   */
  public DefaultAuditRecorder(AuditWriter writer) {
    this(writer, null);
  }

  /**
   * 构造默认审计记录器（支持异步执行器）
   *
   * @param writer 审计日志写入器实现
   * @param executor 异步执行器（可为 null，为 null 时使用 {@link CompletableFuture} 默认线程池）
   */
  public DefaultAuditRecorder(AuditWriter writer, Executor executor) {
    this.writer = Objects.requireNonNull(writer, "AuditWriter must not be null");
    this.executor = executor;
  }

  /**
   * 同步记录单条审计日志
   *
   * @param auditLog 审计日志实体
   * @throws NullPointerException 当 auditLog 为 null
   */
  @Override
  public void record(AuditLog auditLog) {
    Objects.requireNonNull(auditLog, "AuditLog must not be null");
    writer.write(auditLog);
  }

  /**
   * 异步记录单条审计日志，不阻塞调用线程
   *
   * @param auditLog 审计日志实体
   * @throws NullPointerException 当 auditLog 为 null
   */
  @Override
  public void recordAsync(AuditLog auditLog) {
    Objects.requireNonNull(auditLog, "AuditLog must not be null");
    if (executor != null) {
      CompletableFuture.runAsync(() -> writer.write(auditLog), executor);
    } else {
      CompletableFuture.runAsync(() -> writer.write(auditLog));
    }
  }

  /**
   * 同步批量记录审计日志
   *
   * @param auditLogs 审计日志列表（不可为空）
   * @throws NullPointerException 当 auditLogs 为 null
   */
  @Override
  public void recordBatch(List<AuditLog> auditLogs) {
    Objects.requireNonNull(auditLogs, "AuditLogs must not be null");
    if (auditLogs.isEmpty()) {
      return;
    }
    writer.writeBatch(Collections.unmodifiableList(auditLogs));
  }

  /**
   * 获取记录器名称
   *
   * @return 固定返回 {@code "DefaultAuditRecorder"}
   */
  @Override
  public String getName() {
    return "DefaultAuditRecorder";
  }
}
