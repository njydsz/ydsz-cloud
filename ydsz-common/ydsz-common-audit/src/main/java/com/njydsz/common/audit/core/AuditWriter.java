package com.njydsz.common.audit.core;

import java.util.List;

import com.njydsz.common.audit.domain.AuditLog;

/**
 * 审计日志写入器接口
 * <p>
 * 供 {@link AuditRecorder} 实现类委托数据库写入，与 {@link AuditStorage} 的关键区别：
 * <ul>
 *   <li>写入失败时抛出异常（而非吞异常），让 Recorder 层自行决定降级/兜底策略</li>
 *   <li>支持分表路由（由实现类内部处理）</li>
 * </ul>
 * </p>
 *
 * <p>典型实现：{@code JdbcAuditStorage}（JDBC 批量写入）。</p>
 *
 * @author ydsz-team
 * @since 1.1.0
 *
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
}
