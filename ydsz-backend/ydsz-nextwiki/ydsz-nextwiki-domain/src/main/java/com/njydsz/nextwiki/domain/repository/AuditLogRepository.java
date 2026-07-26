package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.common.domain.query.PageResult;
import com.njydsz.nextwiki.domain.entity.AuditLog;

/**
 * 审计日志仓储接口（P2-6）
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public interface AuditLogRepository {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByFileNodeId(String fileNodeId);

    List<AuditLog> findByOperatorId(String operatorId);

    PageResult<AuditLog> findPage(String userId, int page, int pageSize);
}
