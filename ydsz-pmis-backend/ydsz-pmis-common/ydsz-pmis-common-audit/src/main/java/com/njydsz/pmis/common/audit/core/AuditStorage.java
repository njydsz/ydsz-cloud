package com.njydsz.pmis.common.audit.core;

import com.njydsz.pmis.common.audit.domain.AuditLog;

import java.util.List;

/**
 * 审计日志存储策略接口
 * <p>
 * 采用策略模式抽象审计日志的存储方式，内置实现包括
 * {@link com.njydsz.pmis.common.audit.storage.DefaultAuditStorage}（控制台输出）
 * 和 {@link com.njydsz.pmis.common.audit.storage.JdbcAuditStorage}（JDBC 持久化）。
 * </p>
 *
 * <p>业务方可实现该接口对接 ELK、消息队列、远程审计中心等自定义存储后端。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public interface AuditStorage {

    /**
     * 保存单条审计日志
     *
     * @param auditLog 审计日志实体
     */
    void save(AuditLog auditLog);

    /**
     * 批量保存审计日志
     *
     * @param auditLogs 审计日志列表
     */
    void saveBatch(List<AuditLog> auditLogs);

    /**
     * 获取存储策略类型
     *
     * @return 存储类型标识
     */
    String getType();

    /**
     * 检查存储策略是否可用
     *
     * @return 可用返回 true（默认 true）
     */
    default boolean isAvailable() {
        return true;
    }
}
