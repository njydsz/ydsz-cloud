package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.nextwiki.domain.entity.AuditLog;

/**
 * 审计日志仓储接口（P2-6）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuditLogRepository {

    /**
     * 保存审计日志记录。
     *
     * @param auditLog 待持久化的审计日志实体（含操作类型、文件节点、操作人、结果等）
     * @return 持久化后的审计日志（回填主键与审计字段）
     */
    AuditLog save(AuditLog auditLog);

    /**
     * 按文件节点 ID 查询其全部审计记录（按时间倒序）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 该节点的审计日志列表，无记录时返回空列表
     */
    List<AuditLog> findByFileNodeId(String fileNodeId);

    /**
     * 按操作人 ID 查询其产生的全部审计记录。
     *
     * @param operatorId 操作人 ID
     * @return 该操作人的审计日志列表，无记录时返回空列表
     */
    List<AuditLog> findByOperatorId(String operatorId);

    /**
     * 按用户维度分页查询审计日志（用于后台审计查询/导出）。
     *
     * @param userId   用户 ID（权限过滤，null 表示查询全部）
     * @param page     页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页审计日志结果
     */
    PageResponse<AuditLog> findPage(String userId, int page, int pageSize);
}
