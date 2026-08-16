package com.njydsz.common.audit.core;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.core.response.BaseResponse;

/**
 * 审计查询服务接口
 *
 * <p>定义审计日志查询的统一抽象，支持按 ID、业务流水号、操作人、模块、类型、 时间范围、追踪 ID 等维度查询，并提供分页查询能力。
 *
 * <p>内置实现见 {@link com.njydsz.common.audit.core.DefaultAuditQueryService}， 支持分表场景下的 UNION ALL 合并查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuditQueryService {

  /**
   * 根据 ID 查询审计日志
   *
   * @param id 审计记录 ID
   * @return 审计日志实体；不存在时返回 null
   */
  AuditLog getById(String id);

  /**
   * 根据业务流水号查询审计日志
   *
   * @param businessNo 业务流水号
   * @return 审计日志列表（按 operation_time 倒序）；businessNo 为空时返回空列表
   */
  List<AuditLog> getByBusinessNo(String businessNo);

  /**
   * 根据操作人查询审计日志
   *
   * @param operatorId 操作人 ID
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @return 审计日志列表
   */
  List<AuditLog> getByOperator(String operatorId, LocalDateTime startTime, LocalDateTime endTime);

  /**
   * 根据模块查询审计日志
   *
   * @param module 模块名称
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @return 审计日志列表
   */
  List<AuditLog> getByModule(String module, LocalDateTime startTime, LocalDateTime endTime);

  /**
   * 根据审计类型查询审计日志
   *
   * @param auditType 审计类型编码
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @return 审计日志列表
   */
  List<AuditLog> getByAuditType(Integer auditType, LocalDateTime startTime, LocalDateTime endTime);

  /**
   * 查询指定时间范围内的所有审计日志
   *
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @return 审计日志列表
   */
  List<AuditLog> getByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

  // ====================== Paginated query methods ======================

  /**
   * 按时间范围分页查询审计日志
   *
   * @param start 开始时间
   * @param end 结束时间
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页查询结果
   */
  BaseResponse<List<AuditLog>> queryByTimeRange(
      LocalDateTime start, LocalDateTime end, int page, int size);

  /**
   * 按操作人分页查询审计日志
   *
   * @param operatorId 操作人 ID
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页查询结果
   */
  BaseResponse<List<AuditLog>> queryByOperator(String operatorId, int page, int size);

  /**
   * 按操作行为分页查询审计日志
   *
   * @param action 操作行为编码
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页查询结果
   */
  BaseResponse<List<AuditLog>> queryByAction(Integer action, int page, int size);

  /**
   * 按实体类型分页查询审计日志
   *
   * <p>实体类型映射到 {@code module} 字段。
   *
   * @param entityType 实体类型
   * @param page 页码（从 1 开始）
   * @param size 每页大小
   * @return 分页查询结果
   */
  BaseResponse<List<AuditLog>> queryByEntityType(String entityType, int page, int size);

  /**
   * 按追踪 ID 查询审计日志
   *
   * <p>追踪 ID 存储在 {@code extra_info} JSON 字段中，使用 LIKE 匹配。
   *
   * @param traceId 追踪 ID
   * @return 审计日志列表
   */
  List<AuditLog> queryByTraceId(String traceId);

  /**
   * 按条件统计审计日志数量
   *
   * @param operatorId 操作人 ID（可为 null）
   * @param action 操作行为编码（可为 null）
   * @param module 模块/实体类型（可为 null）
   * @param auditType 审计类型（可为 null）
   * @param startTime 开始时间（可为 null）
   * @param endTime 结束时间（可为 null）
   * @return 符合条件的记录总数
   */
  long countByConditions(
      String operatorId,
      Integer action,
      String module,
      Integer auditType,
      LocalDateTime startTime,
      LocalDateTime endTime);
}
