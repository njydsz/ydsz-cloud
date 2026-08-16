package com.njydsz.common.audit.core;

import java.util.List;

import com.njydsz.common.audit.domain.AuditLog;

/**
 * 审计日志写入器接口
 *
 * <p>定义审计日志持久化的统一抽象，供 {@link AuditRecorder} 实现类委托写入操作。 写入失败时抛出 {@link AuditWriteException}，让
 * Recorder 层自行决定降级/兜底策略。
 *
 * <p>内置实现：
 *
 * <ul>
 *   <li>{@link com.njydsz.common.audit.storage.DefaultAuditStorage}：开发测试用，控制台输出
 *   <li>{@link com.njydsz.common.audit.storage.JdbcAuditStorage}：JDBC 持久化（支持分表路由）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuditWriter {

  /**
   * 写入单条审计日志
   *
   * @param auditLog 审计日志实体
   * @throws AuditWriteException 写入失败时抛出
   */
  void write(AuditLog auditLog);

  /**
   * 批量写入审计日志
   *
   * @param auditLogs 审计日志列表（非空）
   * @throws AuditWriteException 写入失败时抛出
   */
  void writeBatch(List<AuditLog> auditLogs);

  /**
   * 获取写入器名称
   *
   * @return 写入器名称（默认返回类名简单名）
   */
  default String getName() {
    return this.getClass().getSimpleName();
  }

  /**
   * 获取存储策略类型
   *
   * @return 存储类型标识（如 "DEFAULT"、"JDBC"）
   * @since 1.1.0 原 AuditStorage.getType()
   */
  default String getType() {
    return "UNKNOWN";
  }

  /**
   * 检查写入器是否可用
   *
   * @return 可用返回 true
   * @since 1.1.0 原 AuditStorage.isAvailable()
   */
  default boolean isAvailable() {
    return true;
  }
}
